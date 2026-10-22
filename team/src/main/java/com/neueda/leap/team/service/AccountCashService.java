package com.neueda.leap.team.service;

import com.neueda.leap.team.audit.AuditLogService;
import com.neueda.leap.team.dto.*;
import com.neueda.leap.team.entity.*;
import com.neueda.leap.team.entity.enums.*;
import com.neueda.leap.team.exception.ApiException;
import com.neueda.leap.team.repository.*;
import java.math.*;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AccountCashService {
    private static final BigDecimal MAX_BALANCE = new BigDecimal("999999999999999999.99");
    private final AccountAccessService access;
    private final AccountCashBalanceRepository balances;
    private final CashLedgerEntryRepository ledgerEntries;
    private final CashTransactionRepository transactions;
    private final AccountQueryRepository queries;
    private final AuditLogService auditLogs;
    private final Clock clock;

    public AccountCashService(AccountAccessService access, AccountCashBalanceRepository balances,
            CashLedgerEntryRepository ledgerEntries,
            CashTransactionRepository transactions,
            AccountQueryRepository queries, Clock clock, AuditLogService auditLogs) {
        this.access = access; this.balances = balances; this.transactions = transactions;
        this.ledgerEntries = ledgerEntries;
        this.queries = queries; this.clock = clock;
        this.auditLogs = auditLogs;
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENT')")
    public CashOperationResponse deposit(long accountId, CashMovementRequest request) {
        return post(accountId, request, CashTransactionType.DEPOSIT);
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENT')")
    public CashOperationResponse withdraw(long accountId, CashMovementRequest request) {
        return post(accountId, request, CashTransactionType.WITHDRAWAL);
    }

    private CashOperationResponse post(long accountId, CashMovementRequest request, CashTransactionType type) {
        BigDecimal amount = validateAmount(request);
        // Lock order: user -> account cash balance -> dependent cash rows.
        // Order reservation/execution services use this same lock order.
        User user = access.lockActiveClient();
        access.owned(accountId, user.getId());
        AccountCashBalance balance = balances.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "ACCOUNT_BALANCE_MISSING", "Account balance is unavailable."));

        // Checking AFTER locking also makes simultaneous retries safe across multiple app instances.
        var existing = transactions.findByAccountIdAndClientRequestId(accountId, request.clientRequestId());
        if (existing.isPresent()) {
            CashTransaction prior = existing.get();
            if (prior.getType() != type || prior.getAmount().compareTo(amount) != 0) {
                throw new ApiException(HttpStatus.CONFLICT, "REQUEST_ID_REUSED",
                        "This clientRequestId was already used for a different cash movement.");
            }
            if (prior.getStatus() == CashTransactionStatus.PENDING) {
                throw new ApiException(HttpStatus.CONFLICT, "TRANSACTION_PENDING", "This cash movement is still pending.");
            }
            return new CashOperationResponse(queries.transaction(accountId, prior.getId()).orElseThrow(), true);
        }

        BigDecimal before = balance.getCashBalance();
        BigDecimal after = type == CashTransactionType.DEPOSIT ? before.add(amount) : before.subtract(amount);
        String failure = null;
        if (type == CashTransactionType.WITHDRAWAL && amount.compareTo(before.subtract(queries.reservedCash(accountId))) > 0) {
            failure = "INSUFFICIENT_AVAILABLE_CASH";
        } else if (after.compareTo(MAX_BALANCE) > 0) {
            failure = "BALANCE_LIMIT_EXCEEDED";
        }

        var now = clock.instant();
        CashTransaction transaction = new CashTransaction(accountId, request.clientRequestId(), amount, type, now);
        if (failure != null) {
            transaction.fail(failure, now);
            transactions.saveAndFlush(transaction);
            auditLogs.record(user.getId(), user.getId(), "transactions",
                    Map.of("transaction_id", transaction.getId()), "CASH_OPERATION_FAILED", null,
                    cashSnapshot(transaction, before, before));
            // Return normally so FAILED commits. The controller returns HTTP 409.
            return new CashOperationResponse(queries.transaction(accountId, transaction.getId()).orElseThrow(), false);
        }

        transaction.complete(now);
        transactions.saveAndFlush(transaction);
        balance.changeBalance(after, now);
        ledgerEntries.save(new CashLedgerEntry(transaction, after, now));
        balances.flush();
        auditLogs.record(user.getId(), user.getId(), "transactions",
                Map.of("transaction_id", transaction.getId()),
                type == CashTransactionType.DEPOSIT ? "CASH_DEPOSITED" : "CASH_WITHDRAWN", null,
                cashSnapshot(transaction, before, after));
        return new CashOperationResponse(queries.transaction(accountId, transaction.getId()).orElseThrow(), false);
    }

    // This is an explicit audit projection, not serialization of a JPA entity or request.
    private static Map<String, Object> cashSnapshot(CashTransaction transaction, BigDecimal before, BigDecimal after) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("transaction_id", transaction.getId());
        snapshot.put("account_id", transaction.getAccountId());
        snapshot.put("transaction_type", transaction.getType().name());
        snapshot.put("transaction_amount", transaction.getAmount());
        snapshot.put("transaction_status", transaction.getStatus().name());
        snapshot.put("failure_reason", transaction.getFailureReason());
        snapshot.put("cash_balance_before", before);
        snapshot.put("cash_balance_after", after);
        return snapshot;
    }

    public PageResponse<CashTransactionDto> transactions(long accountId, int page, int size) {
        access.readable(accountId); return queries.transactions(accountId, page, size);
    }
    public CashTransactionDto transaction(long accountId, long transactionId) {
        access.readable(accountId);
        return queries.transaction(accountId, transactionId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction not found."));
    }
    private static BigDecimal validateAmount(CashMovementRequest request) {
        if (request == null || request.clientRequestId() == null || request.amount() == null
                || request.amount().signum() <= 0 || request.amount().scale() > 2
                || request.amount().compareTo(MAX_BALANCE) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CASH_MOVEMENT",
                    "Provide a clientRequestId and a positive amount with at most two decimal places.");
        }
        return request.amount().setScale(2, RoundingMode.UNNECESSARY);
    }
}
