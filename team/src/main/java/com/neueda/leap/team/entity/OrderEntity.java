 package com.neueda.leap.team.entity;

 import jakarta.persistence.*;

 import java.math.BigDecimal;
 import java.time.LocalDateTime;
 import java.util.ArrayList;
 import java.util.List;
 import java.util.UUID;

 @Entity
 @Table(name = "Orders")
 public class OrderEntity {

     @Id
     @GeneratedValue(strategy = GenerationType.IDENTITY)
     @Column(name = "Order_ID")
     private Long id;

    @Column(name = "Account_ID", nullable = false)
    private Long accountId;

     @ManyToOne(fetch = FetchType.LAZY, optional = false)
     @JoinColumn(name = "Account_ID", nullable = false, insertable = false, updatable = false)
     private AccountEntity accountForOrder;

    @Column(name = "Security_ID", nullable = false)
    private Long securityId;

     @ManyToOne(fetch = FetchType.LAZY, optional = false)
     @JoinColumn(name = "Security_ID", nullable = false, insertable = false, updatable = false)
     private SecurityEntity securityForOrder;

    @Column(name = "Client_Request_ID", nullable = false)
    private UUID clientRequestId;

     @Column(name = "Side", nullable = false)
     private String side;

    @Column(name = "Requested_Amount", precision = 20, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "Quantity_Ordered", precision = 28, scale = 12)
     private BigDecimal quantityOrdered;

     @Column(name = "Order_Status", nullable = false)
     private String orderStatus;

     @Column(name = "Created_Date", nullable = false)
     private LocalDateTime createdDate;

     @Column(name = "Updated_Date", nullable = false)
     private LocalDateTime updatedDate;

    @Column(name = "Accepted_At")
    private LocalDateTime acceptedAt;

    @Column(name = "Terminal_At")
    private LocalDateTime terminalAt;

    @Column(name = "Rejection_Reason")
    private String rejectionReason;

    @OneToOne(mappedBy = "orderForReservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private OrderReservationEntity orderReservation;

    @OneToMany(mappedBy = "orderForExecution", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExecutionEntity> executions = new ArrayList<>();

     public OrderEntity(AccountEntity accountForOrder, SecurityEntity securityForOrder, String side, BigDecimal quantityOrdered, String orderStatus, LocalDateTime createdDate, LocalDateTime updatedDate) {
         setAccountForOrder(accountForOrder);
         setSecurityForOrder(securityForOrder);
         this.clientRequestId = UUID.randomUUID();
         this.side = side;
         this.quantityOrdered = quantityOrdered;
         this.orderStatus = orderStatus;
         this.createdDate = createdDate;
         this.updatedDate = updatedDate;
     }

     public OrderEntity() {
         
     }

     public Long getId() {
         return id;
     }

     public void setId(Long id) {
         this.id = id;
     }

     public AccountEntity getAccountForOrder() {
         return accountForOrder;
     }

     public void setAccountForOrder(AccountEntity accountForOrder) {
         this.accountForOrder = accountForOrder;
         this.accountId = accountForOrder == null ? null : accountForOrder.getAccountId();
     }

     public Long getAccountId() {
         return accountId;
     }

     public void setAccountId(Long accountId) {
         this.accountId = accountId;
     }

     public SecurityEntity getSecurityForOrder() {
         return securityForOrder;
     }

     public void setSecurityForOrder(SecurityEntity securityForOrder) {
         this.securityForOrder = securityForOrder;
         this.securityId = securityForOrder == null ? null : securityForOrder.getSecurityId();
     }

     public Long getSecurityId() {
         return securityId;
     }

     public void setSecurityId(Long securityId) {
         this.securityId = securityId;
     }

     public UUID getClientRequestId() {
         return clientRequestId;
     }

     public void setClientRequestId(UUID clientRequestId) {
         this.clientRequestId = clientRequestId;
     }

     public String getSide() {
         return side;
     }

     public void setSide(String side) {
         this.side = side;
     }

     public BigDecimal getRequestedAmount() {
         return requestedAmount;
     }

     public void setRequestedAmount(BigDecimal requestedAmount) {
         this.requestedAmount = requestedAmount;
     }

     public BigDecimal getQuantityOrdered() {
         return quantityOrdered;
     }

     public void setQuantityOrdered(BigDecimal quantityOrdered) {
         this.quantityOrdered = quantityOrdered;
     }

     public String getOrderStatus() {
         return orderStatus;
     }

     public void setOrderStatus(String orderStatus) {
         this.orderStatus = orderStatus;
     }

     public LocalDateTime getCreatedDate() {
         return createdDate;
     }

     public void setCreatedDate(LocalDateTime createdDate) {
         this.createdDate = createdDate;
     }

     public LocalDateTime getUpdatedDate() {
         return updatedDate;
     }

     public void setUpdatedDate(LocalDateTime updatedDate) {
         this.updatedDate = updatedDate;
     }

     public LocalDateTime getAcceptedAt() {
         return acceptedAt;
     }

     public void setAcceptedAt(LocalDateTime acceptedAt) {
         this.acceptedAt = acceptedAt;
     }

     public LocalDateTime getTerminalAt() {
         return terminalAt;
     }

     public void setTerminalAt(LocalDateTime terminalAt) {
         this.terminalAt = terminalAt;
     }

     public String getRejectionReason() {
         return rejectionReason;
     }

     public void setRejectionReason(String rejectionReason) {
         this.rejectionReason = rejectionReason;
     }

     public OrderReservationEntity getOrderReservation() {
         return orderReservation;
     }

     public void setOrderReservation(OrderReservationEntity orderReservation) {
         this.orderReservation = orderReservation;
     }

     public List<ExecutionEntity> getExecutions() {
         return executions;
     }

     public void setExecutions(List<ExecutionEntity> executions) {
         this.executions = executions;
     }
 }
