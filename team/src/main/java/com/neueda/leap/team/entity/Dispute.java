package com.neueda.leap.team.entity;

import com.neueda.leap.team.entity.enums.DisputeStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "disputes")
public class Dispute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dispute_id")
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "transaction_id", updatable = false)
    private Long transactionId;

    @Column(name = "trade_id", updatable = false)
    private Long tradeId;

    @Column(name = "dispute_type", nullable = false, length = 50, updatable = false)
    private String disputeType;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private DisputeStatus status;

    @Column(name = "date_created", nullable = false, updatable = false)
    private Instant dateCreated;

    @Column(name = "date_resolved")
    private Instant dateResolved;

    protected Dispute() {}

    public static Dispute forTransaction(long accountId, long transactionId, String disputeType, String description, Instant now) {
        Dispute dispute = new Dispute();
        dispute.accountId = accountId;
        dispute.transactionId = transactionId;
        dispute.tradeId = null;
        dispute.disputeType = disputeType;
        dispute.description = description;
        dispute.status = DisputeStatus.OPEN;
        dispute.dateCreated = now;
        dispute.dateResolved = null;
        return dispute;
    }

    public static Dispute forTrade(long accountId, long tradeId, String disputeType, String description, Instant now) {
        Dispute dispute = new Dispute();
        dispute.accountId = accountId;
        dispute.transactionId = null;
        dispute.tradeId = tradeId;
        dispute.disputeType = disputeType;
        dispute.description = description;
        dispute.status = DisputeStatus.OPEN;
        dispute.dateCreated = now;
        dispute.dateResolved = null;
        return dispute;
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public Long getAdminId() { return adminId; }
    public Long getTransactionId() { return transactionId; }
    public Long getTradeId() { return tradeId; }
    public String getDisputeType() { return disputeType; }
    public String getDescription() { return description; }
    public DisputeStatus getStatus() { return status; }
    public Instant getDateCreated() { return dateCreated; }
    public Instant getDateResolved() { return dateResolved; }

    public void assignToAdmin(long adminId) {
        this.adminId = adminId;
        if (this.status == DisputeStatus.OPEN) {
            this.status = DisputeStatus.UNDER_REVIEW;
        }
    }

    public void changeStatus(DisputeStatus status, Instant now) {
        this.status = status;
        if (status == DisputeStatus.RESOLVED || status == DisputeStatus.REJECTED) {
            this.dateResolved = now;
            return;
        }
        this.dateResolved = null;
    }
}
