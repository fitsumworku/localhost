package com.neueda.leap.team.service;

import com.neueda.leap.team.dto.*;
import com.neueda.leap.team.entity.*;
import com.neueda.leap.team.repository.*;
import java.time.Clock;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AccountService {
    private final AccountRepository accounts;
    private final AccountCashBalanceRepository balances;
    private final AccountQueryRepository queries;
    private final AccountAccessService access;
    private final Clock clock;
    public AccountService(AccountRepository accounts, AccountCashBalanceRepository balances,
            AccountQueryRepository queries, AccountAccessService access, Clock clock) {
        this.accounts = accounts; this.balances = balances; this.queries = queries;
        this.access = access; this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENT')")
    public AccountDto create() {
        User owner = access.lockActiveClient();
        var now = clock.instant();
        Account account = accounts.saveAndFlush(new Account(owner, now));
        balances.saveAndFlush(new AccountCashBalance(account, now));
        return queries.account(account.getId()).orElseThrow();
    }

    public PageResponse<AccountDto> list(Long userId, int page, int size) {
        if (!access.isAdmin()) {
            long self = access.principal().getUserId();
            if (userId != null && userId != self) throw new AccessDeniedException("Own accounts only");
            userId = self;
        }
        return queries.accounts(userId, page, size);
    }
    public AccountDto get(long accountId) {
        access.readable(accountId);
        return queries.account(accountId).orElseThrow();
    }
    public AccountBalanceDto balance(long accountId) { return get(accountId).balance(); }
    public PageResponse<PositionDto> positions(long accountId, int page, int size) {
        access.readable(accountId); return queries.positions(accountId, page, size);
    }
    public PageResponse<AccountOrderDto> orders(long accountId, int page, int size) {
        access.readable(accountId); return queries.orders(accountId, page, size);
    }
    public PageResponse<AccountTradeDto> trades(long accountId, int page, int size) {
        access.readable(accountId); return queries.trades(accountId, page, size);
    }
}
