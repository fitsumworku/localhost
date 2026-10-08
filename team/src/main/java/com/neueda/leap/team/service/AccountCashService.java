package com.neueda.leap.team.service;

import com.neueda.leap.team.dto.*;
import com.neueda.leap.team.entity.*;
import com.neueda.leap.team.entity.enums.*;
import com.neueda.leap.team.exception.ApiException;
import com.neueda.leap.team.repository.*;
import java.math.*;
import java.time.Clock;
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
    private final AccountQueryRepository queries;
    private final Clock clock;

    public AccountCashService(AccountAccessService access, AccountCashBalanceRepository balances,
            CashTransactionRepository transactions,
            AccountQueryRepository queries, Clock clock) {
        this.access = access; this.balances = balances; this.transactions = transactions;
        this.queries = queries; this.clock = clock;
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
            // Return normally so FAILED commits. The controller returns HTTP 409.
            return new CashOperationResponse(queries.transaction(accountId, transaction.getId()).orElseThrow(), false);
        }

        transaction.complete(now);
        transactions.saveAndFlush(transaction);
        balance.changeBalance(after, now);
        balances.flush();
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
