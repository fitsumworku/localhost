package com.neueda.leap.team.entity;

import com.neueda.leap.team.entity.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class CashTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long id;
    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;
    @Column(name = "client_request_id", nullable = false, updatable = false)
    private UUID clientRequestId;
    @Column(name = "transaction_amount", nullable = false, updatable = false, precision = 20, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, updatable = false, length = 16)
    private CashTransactionType type;
    @Column(name = "transaction_date", nullable = false, updatable = false)
    private Instant transactionDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false, length = 9)
    private CashTransactionStatus status;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    protected CashTransaction() {}
    public CashTransaction(long accountId, UUID requestId, BigDecimal amount, CashTransactionType type, Instant now) {
        this.accountId = accountId; this.clientRequestId = requestId; this.amount = amount;
        this.type = type; this.transactionDate = now; this.status = CashTransactionStatus.PENDING;
    }
    public void complete(Instant now) { finish(CashTransactionStatus.COMPLETED, null, now); }
    public void fail(String reason, Instant now) { finish(CashTransactionStatus.FAILED, reason, now); }
    private void finish(CashTransactionStatus status, String reason, Instant now) {
        if (this.status != CashTransactionStatus.PENDING) throw new IllegalStateException("Transaction already resolved");
        this.status = status; this.failureReason = reason; this.completedAt = now;
    }
    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public UUID getClientRequestId() { return clientRequestId; }
    public BigDecimal getAmount() { return amount; }
    public CashTransactionType getType() { return type; }
    public Instant getTransactionDate() { return transactionDate; }
    public CashTransactionStatus getStatus() { return status; }
    public Instant getCompletedAt() { return completedAt; }
    public String getFailureReason() { return failureReason; }
}
