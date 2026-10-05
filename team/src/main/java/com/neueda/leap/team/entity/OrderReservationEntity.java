package com.neueda.leap.team.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Order_Reservations")
public class OrderReservationEntity {

    @Id
    @Column(name = "Order_ID")
    private Long orderId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "Order_ID", nullable = false)
    private OrderEntity orderForReservation;

    @Column(name = "Account_ID", nullable = false)
    private Long accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Account_ID", insertable = false, updatable = false)
    private AccountEntity accountForReservation;

    @Column(name = "Security_ID", nullable = false)
    private Long securityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Security_ID", insertable = false, updatable = false)
    private SecurityEntity securityForReservation;

    @Column(name = "Side", nullable = false, length = 1)
    private String side;

    @Column(name = "Reserved_Cash", precision = 20, scale = 2)
    private BigDecimal reservedCash;

    @Column(name = "Reserved_Quantity", precision = 28, scale = 12)
    private BigDecimal reservedQuantity;

    @Column(name = "Status", nullable = false)
    private String status;

    @Column(name = "Created_Date", nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "Resolved_At")
    private LocalDateTime resolvedAt;

    public OrderReservationEntity() {
    }

    public Long getOrderId() {
        return orderId;
    }

    public OrderEntity getOrderForReservation() {
        return orderForReservation;
    }

    public void setOrderForReservation(OrderEntity orderForReservation) {
        this.orderForReservation = orderForReservation;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public AccountEntity getAccountForReservation() {
        return accountForReservation;
    }

    public void setAccountForReservation(AccountEntity accountForReservation) {
        this.accountForReservation = accountForReservation;
    }

    public Long getSecurityId() {
        return securityId;
    }

    public void setSecurityId(Long securityId) {
        this.securityId = securityId;
    }

    public SecurityEntity getSecurityForReservation() {
        return securityForReservation;
    }

    public void setSecurityForReservation(SecurityEntity securityForReservation) {
        this.securityForReservation = securityForReservation;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public BigDecimal getReservedCash() {
        return reservedCash;
    }

    public void setReservedCash(BigDecimal reservedCash) {
        this.reservedCash = reservedCash;
    }

    public BigDecimal getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(BigDecimal reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
