package com.neueda.leap.team.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Account_Cash_Balances")
public class AccountCashBalanceEntity {

    @Id
    @Column(name = "Account_ID")
    private Long accountId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "Account_ID", nullable = false)
    private AccountEntity accountForCashBalance;

    @Column(name = "Cash_Balance", nullable = false, precision = 20, scale = 2)
    private BigDecimal cashBalance;

    @Column(name = "Updated_Date", nullable = false)
    private LocalDateTime updatedDate;

    public AccountCashBalanceEntity() {
    }

    public AccountCashBalanceEntity(AccountEntity accountForCashBalance, BigDecimal cashBalance, LocalDateTime updatedDate) {
        this.accountForCashBalance = accountForCashBalance;
        this.cashBalance = cashBalance;
        this.updatedDate = updatedDate;
    }

    public Long getAccountId() {
        return accountId;
    }

    public AccountEntity getAccountForCashBalance() {
        return accountForCashBalance;
    }

    public void setAccountForCashBalance(AccountEntity accountForCashBalance) {
        this.accountForCashBalance = accountForCashBalance;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }
}
