package com.neueda.leap.team.entity;

import com.neueda.leap.team.entity.enums.CashTransactionType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cash_ledger")
public class CashLedgerEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_id")
    private Long id;
    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;
    @Column(name = "transaction_id", unique = true, updatable = false)
    private Long transactionId;
    @Column(name = "trade_id", unique = true, updatable = false)
    private Long tradeId;
    @Column(name = "entry_type", nullable = false, length = 16, updatable = false)
    private String entryType;
    @Column(name = "debit_amount", nullable = false, precision = 20, scale = 2, updatable = false)
    private BigDecimal debitAmount;
    @Column(name = "credit_amount", nullable = false, precision = 20, scale = 2, updatable = false)
    private BigDecimal creditAmount;
    @Column(name = "running_balance", nullable = false, precision = 20, scale = 2, updatable = false)
    private BigDecimal runningBalance;
    @Column(name = "entry_date", nullable = false, updatable = false)
    private Instant entryDate;

    protected CashLedgerEntry() {}
    public CashLedgerEntry(CashTransaction transaction, BigDecimal balanceAfter, Instant now) {
        if (transaction.getType() != CashTransactionType.DEPOSIT && transaction.getType() != CashTransactionType.WITHDRAWAL) {
            throw new IllegalArgumentException("Only client deposits and withdrawals are posted by this module");
        }
        this.accountId = transaction.getAccountId();
        this.transactionId = transaction.getId();
        this.entryType = transaction.getType().name();
        BigDecimal zero = new BigDecimal("0.00");
        this.debitAmount = transaction.getType() == CashTransactionType.DEPOSIT ? transaction.getAmount() : zero;
        this.creditAmount = transaction.getType() == CashTransactionType.WITHDRAWAL ? transaction.getAmount() : zero;
        this.runningBalance = balanceAfter;
        this.entryDate = now;
    }
    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public Long getTransactionId() { return transactionId; }
    public Long getTradeId() { return tradeId; }
    public String getEntryType() { return entryType; }
    public BigDecimal getDebitAmount() { return debitAmount; }
    public BigDecimal getCreditAmount() { return creditAmount; }
    public BigDecimal getRunningBalance() { return runningBalance; }
    public Instant getEntryDate() { return entryDate; }
}
