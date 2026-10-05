package com.neueda.leap.team.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Account_Positions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"Account_ID", "Security_ID"})
})
public class AccountPositionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Position_ID")
    private Long positionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Account_ID", nullable = false)
    private AccountEntity accountForPosition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Security_ID", nullable = false)
    private SecurityEntity securityForAccountPosition;

    @Column(name = "Quantity", nullable = false, precision = 28, scale = 12)
    private BigDecimal quantity;

    @Column(name = "Average_Price", nullable = false, precision = 28, scale = 12)
    private BigDecimal averagePrice;

    @Column(name = "Updated_Date", nullable = false)
    private LocalDateTime updatedDate;

    public AccountPositionEntity(AccountEntity accountForPosition, SecurityEntity securityForAccountPosition, double totalShares, double averagePrice, LocalDateTime updatedDate) {
        this.accountForPosition = accountForPosition;
        this.securityForAccountPosition = securityForAccountPosition;
        this.quantity = BigDecimal.valueOf(totalShares);
        this.averagePrice = BigDecimal.valueOf(averagePrice);
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
        return quantity == null ? 0.0d : quantity.doubleValue();
    }

    public void setTotalShares(double totalShares) {
        this.quantity = BigDecimal.valueOf(totalShares);
    }

    public double getAveragePrice() {
        return averagePrice == null ? 0.0d : averagePrice.doubleValue();
    }

    public void setAveragePrice(double averagePrice) {
        this.averagePrice = BigDecimal.valueOf(averagePrice);
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }

    public double getQuantity() {
        return quantity == null ? 0.0d : quantity.doubleValue();
    }

    public double getAverageCostPerShare() {
        return averagePrice == null ? 0.0d : averagePrice.doubleValue();
    }
}

