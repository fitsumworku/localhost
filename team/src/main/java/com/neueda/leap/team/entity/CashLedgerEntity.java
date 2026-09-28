package com.neueda.leap.team.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Cash_Ledger")
public class CashLedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Ledger_ID")
    private Long ledgerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Account_ID", nullable = false)
    private AccountEntity accountForCashLedger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Transaction_ID")
    private TransactionEntity transactionForCashLedger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Trade_ID")
    private TradeEntity tradeForCashLedger;

    @Column(name = "Entry_Type", nullable = false, length = 16)
    private String entryType;

    @Column(name = "Debit_Amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal debitAmount;

    @Column(name = "Credit_Amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal creditAmount;

    @Column(name = "Running_Balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal runningBalance;

    @Column(name = "Entry_Date", nullable = false)
    private LocalDateTime entryDate;

    public CashLedgerEntity() {
    }

    public CashLedgerEntity(AccountEntity accountForCashLedger,
                            TransactionEntity transactionForCashLedger,
                            TradeEntity tradeForCashLedger,
                            String entryType,
                            BigDecimal debitAmount,
                            BigDecimal creditAmount,
                            BigDecimal runningBalance,
                            LocalDateTime entryDate) {
        this.accountForCashLedger = accountForCashLedger;
        this.transactionForCashLedger = transactionForCashLedger;
        this.tradeForCashLedger = tradeForCashLedger;
        this.entryType = entryType;
        this.debitAmount = debitAmount;
        this.creditAmount = creditAmount;
        this.runningBalance = runningBalance;
        this.entryDate = entryDate;
    }

    public Long getLedgerId() {
        return ledgerId;
    }

    public AccountEntity getAccountForCashLedger() {
        return accountForCashLedger;
    }

    public void setAccountForCashLedger(AccountEntity accountForCashLedger) {
        this.accountForCashLedger = accountForCashLedger;
    }

    public TransactionEntity getTransactionForCashLedger() {
        return transactionForCashLedger;
    }

    public void setTransactionForCashLedger(TransactionEntity transactionForCashLedger) {
        this.transactionForCashLedger = transactionForCashLedger;
    }

    public TradeEntity getTradeForCashLedger() {
        return tradeForCashLedger;
    }

    public void setTradeForCashLedger(TradeEntity tradeForCashLedger) {
        this.tradeForCashLedger = tradeForCashLedger;
    }

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }

    public BigDecimal getDebitAmount() {
        return debitAmount;
    }

    public void setDebitAmount(BigDecimal debitAmount) {
        this.debitAmount = debitAmount;
    }

    public BigDecimal getCreditAmount() {
        return creditAmount;
    }

    public void setCreditAmount(BigDecimal creditAmount) {
        this.creditAmount = creditAmount;
    }

    public BigDecimal getRunningBalance() {
        return runningBalance;
    }

    public void setRunningBalance(BigDecimal runningBalance) {
        this.runningBalance = runningBalance;
    }

    public LocalDateTime getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDateTime entryDate) {
        this.entryDate = entryDate;
    }
}