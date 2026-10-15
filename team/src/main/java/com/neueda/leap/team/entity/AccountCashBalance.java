package com.neueda.leap.team.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account_cash_balances")
public class AccountCashBalance {
    @Id @Column(name = "account_id")
    private Long accountId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;
    @Column(name = "cash_balance", nullable = false, precision = 20, scale = 2)
    private BigDecimal cashBalance;
    @Column(name = "updated_date", nullable = false)
    private Instant updatedDate;

    protected AccountCashBalance() {}
    public AccountCashBalance(Account account, Instant now) {
        this.account = account;
        this.cashBalance = new BigDecimal("0.00");
        this.updatedDate = now;
    }
    public Long getAccountId() { return accountId; }
    public BigDecimal getCashBalance() { return cashBalance; }
    public Instant getUpdatedDate() { return updatedDate; }
    public void changeBalance(BigDecimal balance, Instant now) {
        this.cashBalance = balance;
        this.updatedDate = now;
    }
}
