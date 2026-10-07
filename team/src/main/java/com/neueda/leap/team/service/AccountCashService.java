package com.neueda.leap.team.service;

import com.neueda.leap.team.dto.*;
import com.neueda.leap.team.entity.*;
import com.neueda.leap.team.entity.enums.*;
import com.neueda.leap.team.exception.ApiException;
import com.neueda.leap.team.repository.*;
import java.math.*;
import java.time.Clock;
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
    private final CashTransactionRepository transactions;
    private final CashLedgerRepository ledger;
    private final AccountQueryRepository queries;
    private final AuditService audit;
    private final Clock clock;

    public AccountCashService(AccountAccessService access, AccountCashBalanceRepository balances,
            CashTransactionRepository transactions, CashLedgerRepository ledger,
            AccountQueryRepository queries, AuditService audit, Clock clock) {
        this.access = access; this.balances = balances; this.transactions = transactions;
        this.ledger = ledger; this.queries = queries; this.audit = audit; this.clock = clock;
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
        // Order reservation/execution services must use this same order when they are added.
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
            audit.recordEntityEvent(user.getId(), user.getId(), "transactions",
                    Map.of("transaction_id", transaction.getId(), "account_id", accountId),
                    "CASH_TRANSACTION_FAILED", null,
                    Map.of("type", type.name(), "amount", amount, "status", "FAILED", "failure_reason", failure));
            // Return normally so FAILED and its audit record commit. The controller returns HTTP 409.
            return new CashOperationResponse(queries.transaction(accountId, transaction.getId()).orElseThrow(), false);
        }

        transaction.complete(now);
        transactions.saveAndFlush(transaction);
        balance.changeBalance(after, now);
        CashLedgerEntry entry = ledger.saveAndFlush(new CashLedgerEntry(transaction, after, now));
        audit.recordEntityEvent(user.getId(), user.getId(), "transactions",
                Map.of("transaction_id", transaction.getId(), "account_id", accountId), "CASH_TRANSACTION_COMPLETED", null,
                Map.of("type", type.name(), "amount", amount, "status", "COMPLETED", "client_request_id", request.clientRequestId()));
        audit.recordEntityEvent(user.getId(), user.getId(), "account_cash_balances", Map.of("account_id", accountId),
                "CASH_BALANCE_CHANGED", Map.of("cash_balance", before),
                Map.of("cash_balance", after, "transaction_id", transaction.getId(), "ledger_id", entry.getId()));
        audit.recordEntityEvent(user.getId(), user.getId(), "cash_ledger",
                Map.of("ledger_id", entry.getId(), "account_id", accountId), "CASH_POSTED", null,
                Map.of("transaction_id", transaction.getId(), "entry_type", entry.getEntryType(),
                        "debit_amount", entry.getDebitAmount(), "credit_amount", entry.getCreditAmount(), "running_balance", after));
        return new CashOperationResponse(queries.transaction(accountId, transaction.getId()).orElseThrow(), false);
    }

    public PageResponse<CashTransactionDto> transactions(long accountId, int page, int size) {
        access.readable(accountId); return queries.transactions(accountId, page, size);
    }
    public CashTransactionDto transaction(long accountId, long transactionId) {
        access.readable(accountId);
        return queries.transaction(accountId, transactionId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction not found."));
    }
    public PageResponse<LedgerEntryDto> ledger(long accountId, int page, int size) {
        access.readable(accountId); return queries.ledger(accountId, page, size);
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
