package com.neueda.leap.team.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "Transactions")
public class TransactionEntity {

    /**
     *
     * CREATE TABLE Transactions (
     *     Transaction_ID BIGSERIAL PRIMARY KEY,
     *     Account_ID BIGINT NOT NULL,
     *     Transaction_Amount NUMERIC(18,4) NOT NULL,
     *     Transaction_Type VARCHAR(16) NOT NULL,
     *     Transaction_Date TIMESTAMP NOT NULL,
     *     Transaction_Status VARCHAR(9) NOT NULL,
     *     FOREIGN KEY (Account_ID) REFERENCES Accounts(Account_ID),
     * );
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Transaction_ID")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Account_ID", nullable = false)
    private AccountEntity accountForTransaction;

    @Column(name = "Client_Request_ID", nullable = false)
    private UUID clientRequestId;

    @Column(name = "Transaction_Amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal transactionAmount;

    @Column(name = "Transaction_Type", nullable = false)
    private String transactionType;

    @Column(name = "Transaction_Date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "Transaction_Status", nullable = false)
    private String transactionStatus;

    @Column(name = "Completed_At")
    private LocalDateTime completedAt;

    @Column(name = "Failure_Reason")
    private String failureReason;

    @OneToMany(mappedBy = "transactionForDispute", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DisputeEntity> disputesForTransaction = new ArrayList<>();

    @OneToMany(mappedBy = "transactionForCashLedger", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CashLedgerEntity> cashLedgerEntries = new ArrayList<>();

    public TransactionEntity(AccountEntity accountForTransaction, BigDecimal transactionAmount, String transactionType, LocalDateTime transactionDate, String transactionStatus) {
        this.accountForTransaction = accountForTransaction;
        this.clientRequestId = UUID.randomUUID();
        this.transactionAmount = transactionAmount;
        this.transactionType = transactionType;
        this.transactionDate = transactionDate;
        this.transactionStatus = transactionStatus;
    }

    public TransactionEntity() {

    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public AccountEntity getAccountForTransaction() {
        return accountForTransaction;
    }

    public void setAccountForTransaction(AccountEntity accountForTransaction) {
        this.accountForTransaction = accountForTransaction;
    }

    public UUID getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(UUID clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public BigDecimal getTransactionAmount() {
        return transactionAmount;
    }

    public void setTransactionAmount(BigDecimal transactionAmount) {
        this.transactionAmount = transactionAmount;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDateTime transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getTransactionStatus() {
        return transactionStatus;
    }

    public void setTransactionStatus(String transactionStatus) {
        this.transactionStatus = transactionStatus;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
