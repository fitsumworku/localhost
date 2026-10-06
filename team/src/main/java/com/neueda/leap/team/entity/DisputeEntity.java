package com.neueda.leap.team.entity;


import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Disputes")
public class DisputeEntity {

    /**
     *
     * CREATE TABLE Disputes (
     *     Dispute_ID BIGSERIAL PRIMARY KEY,
     *     Account_ID BIGINT NOT NULL,
     *     Admin_ID BIGINT NOT NULL,
     *     Transaction_ID BIGINT NOT NULL,
     *     Trade_ID BIGINT,
     *     Dispute_Type VARCHAR(50) NOT NULL,
     *     Description TEXT,
     *     Status VARCHAR(12) NOT NULL,
     *     Date_Created TIMESTAMP NOT NULL,
     *     Date_Resolved TIMESTAMP,
     *     FOREIGN KEY (Account_ID) REFERENCES Accounts(Account_ID),
     *     FOREIGN KEY (Admin_ID) REFERENCES Users(User_ID),
     *     FOREIGN KEY (Transaction_ID) REFERENCES Transactions(Transaction_ID),
     *     FOREIGN KEY (Trade_ID) REFERENCES Trades(Trade_ID),
     *     CHECK (Status IN ('OPEN','UNDER_REVIEW','ESCALATED','RESOLVED','REJECTED')),
     *     CHECK (Date_Resolved IS NULL OR Date_Resolved >= Date_Created)
     * );
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Dispute_ID")
    private Long disputeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Account_ID", nullable = false)
    private AccountEntity accountForDispute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Admin_ID")
    private UserEntity userForDispute;

    @Column(name = "Transaction_ID")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "Transaction_ID", referencedColumnName = "Transaction_ID", insertable = false, updatable = false),
        @JoinColumn(name = "Account_ID", referencedColumnName = "Account_ID", insertable = false, updatable = false)
    })
    private TransactionEntity transactionForDispute;

    @Column(name = "Trade_ID")
    private Long tradeId;

    @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns({
            @JoinColumn(name = "Trade_ID", referencedColumnName = "Trade_ID", insertable = false, updatable = false),
            @JoinColumn(name = "Account_ID", referencedColumnName = "Account_ID", insertable = false, updatable = false)
        })
    private TradeEntity tradeForDispute;

    @Column(name = "Dispute_Type", nullable = false)
    private String disputeType;

    @Column(name = "Description")
    private String description;

    @Column(name = "Status", nullable = false)
    private String status;

    @Column(name = "Date_Created", nullable = false)
    private LocalDateTime dateCreated;

    @Column(name = "Date_Resolved")
    private LocalDateTime dateResolved;

    public DisputeEntity(AccountEntity accountForDispute, UserEntity userForDispute, TransactionEntity transactionForDispute, TradeEntity tradeForDispute, String disputeType, String description, String status, LocalDateTime dateCreated, LocalDateTime dateResolved) {
        this.accountForDispute = accountForDispute;
        this.userForDispute = userForDispute;
        this.transactionForDispute = transactionForDispute;
        this.transactionId = transactionForDispute == null ? null : transactionForDispute.getTransactionId();
        this.tradeForDispute = tradeForDispute;
        this.tradeId = tradeForDispute == null ? null : tradeForDispute.getTradeId();
        this.disputeType = disputeType;
        this.description = description;
        this.status = status;
        this.dateCreated = dateCreated;
        this.dateResolved = dateResolved;
    }

    public DisputeEntity() {}

    public Long getDisputeId() {
        return disputeId;
    }

    public void setDisputeId(Long disputeId) {
        this.disputeId = disputeId;
    }

    public AccountEntity getAccount() {
        return accountForDispute;
    }

    public void setAccount(AccountEntity account) {
        this.accountForDispute = account;
    }

    public UserEntity getUserForDispute() {
        return userForDispute;
    }

    public void setUserForDispute(UserEntity userForDispute) {
        this.userForDispute = userForDispute;
    }

    public TransactionEntity getTransactionForDispute() {
        return transactionForDispute;
    }

    public void setTransactionForDispute(TransactionEntity transactionForDispute) {
        this.transactionForDispute = transactionForDispute;
        this.transactionId = transactionForDispute == null ? null : transactionForDispute.getTransactionId();
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public TradeEntity getTradeForDispute() {
        return tradeForDispute;
    }

    public void setTradeForDispute(TradeEntity tradeForDispute) {
        this.tradeForDispute = tradeForDispute;
        this.tradeId = tradeForDispute == null ? null : tradeForDispute.getTradeId();
    }

    public Long getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
    }

    public String getDisputeType() {
        return disputeType;
    }

    public void setDisputeType(String disputeType) {
        this.disputeType = disputeType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(LocalDateTime dateCreated) {
        this.dateCreated = dateCreated;
    }

    public LocalDateTime getDateResolved() {
        return dateResolved;
    }

    public void setDateResolved(LocalDateTime dateResolved) {
        this.dateResolved = dateResolved;
    }
}
