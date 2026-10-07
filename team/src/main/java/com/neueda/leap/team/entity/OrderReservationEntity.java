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
    @JoinColumns({
        @JoinColumn(name = "Order_ID", referencedColumnName = "Order_ID", insertable = false, updatable = false),
        @JoinColumn(name = "Account_ID", referencedColumnName = "Account_ID", insertable = false, updatable = false),
        @JoinColumn(name = "Security_ID", referencedColumnName = "Security_ID", insertable = false, updatable = false),
        @JoinColumn(name = "Side", referencedColumnName = "Side", insertable = false, updatable = false)
    })
    private OrderEntity orderForReservation;

    @Column(name = "Account_ID", nullable = false)
    private Long accountId;

    @Transient
    private AccountEntity accountForReservation;

    @Column(name = "Security_ID", nullable = false)
    private Long securityId;

    @Transient
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

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public OrderEntity getOrderForReservation() {
        return orderForReservation;
    }

    public void setOrderForReservation(OrderEntity orderForReservation) {
        this.orderForReservation = orderForReservation;
        if (orderForReservation != null) {
            this.orderId = orderForReservation.getId();
            AccountEntity account = orderForReservation.getAccountForOrder();
            SecurityEntity security = orderForReservation.getSecurityForOrder();
            this.accountId = account == null ? null : account.getAccountId();
            this.securityId = security == null ? null : security.getSecurityId();
            this.side = orderForReservation.getSide();
        }
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public AccountEntity getAccountForReservation() {
        // Derive from the composite order relationship to avoid dual mapping
        return orderForReservation == null ? null : orderForReservation.getAccountForOrder();
    }

    public void setAccountForReservation(AccountEntity accountForReservation) {
        // Not used; account is set through setOrderForReservation()
        this.accountId = accountForReservation == null ? null : accountForReservation.getAccountId();
    }

    public Long getSecurityId() {
        return securityId;
    }

    public void setSecurityId(Long securityId) {
        this.securityId = securityId;
    }

    public SecurityEntity getSecurityForReservation() {
        // Derive from the composite order relationship to avoid dual mapping
        return orderForReservation == null ? null : orderForReservation.getSecurityForOrder();
    }

    public void setSecurityForReservation(SecurityEntity securityForReservation) {
        // Not used; security is set through setOrderForReservation()
        this.securityId = securityForReservation == null ? null : securityForReservation.getSecurityId();
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
