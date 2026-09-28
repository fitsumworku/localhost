package com.neueda.leap.team.entity;

import jakarta.persistence.*;

// import java.beans.Transient;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "account_positions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"account_id", "security_id"})
})
public class AccountPositionEntity {
    // Position_ID BIGSERIAL PRIMARY KEY,
    // Account_ID BIGINT NOT NULL,
    // Security_ID BIGINT NOT NULL,
    // Total_Shares NUMERIC(18,4),
    // Average_Price NUMERIC(18,4),
    // Updated_Date TIMESTAMP,
    // FOREIGN KEY (Account_ID) REFERENCES Accounts(Account_ID),
    // FOREIGN KEY (Security_ID) REFERENCES Securities(Security_ID),
    // UNIQUE (Account_ID, Security_ID),
    // CHECK (Total_Shares >= 0),
    // CHECK (Average_Price >= 0)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Position_ID")
    private Long positionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Account_ID", nullable = false)
    private AccountEntity accountForPosition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Security_Id", nullable = false)
    private SecurityEntity securityForAccountPosition;

    @Column(name = "Total_Shares", nullable = false)
    private double totalShares;

    @Column(name = "Average_Price", nullable = false)
    private double averagePrice;

    @Column(name = "Updated_Date", nullable = false)
    private LocalDateTime updatedDate;

    public AccountPositionEntity(AccountEntity accountForPosition, SecurityEntity securityForAccountPosition, double totalShares, double averagePrice, LocalDateTime updatedDate) {
        this.accountForPosition = accountForPosition;
        this.securityForAccountPosition = securityForAccountPosition;
        this.totalShares = totalShares;
        this.averagePrice = averagePrice;
        this.updatedDate = updatedDate;
    }

    public AccountPositionEntity() {

    }

    // Getters and Setters
    public Long getPositionId() {
        return positionId;
    }

    public AccountEntity getAccount() {
        return accountForPosition;
    }

    public void setAccount(AccountEntity account) {
        this.accountForPosition = account;
    }

    @Transient
    public Long getAccountId() {
        return accountForPosition == null ? null : accountForPosition.getAccountId();
    }

    public SecurityEntity getSecurity() {
        return securityForAccountPosition;
    }

    public void setSecurity(SecurityEntity security) {
        this.securityForAccountPosition = security;
    }

    @Transient
    public Long getSecurityId() {
        return securityForAccountPosition == null ? null : securityForAccountPosition.getSecurityId();
    }

    public double getTotalShares() {
        return totalShares;
    }

    public void setTotalShares(double totalShares) {
        this.totalShares = totalShares;
    }

    public double getAveragePrice() {
        return averagePrice;
    }

    public void setAveragePrice(double averagePrice) {
        this.averagePrice = averagePrice;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }

    public double getQuantity() {
        return totalShares;
    }

    public double getAverageCostPerShare() {
        return averagePrice;
    }
}

