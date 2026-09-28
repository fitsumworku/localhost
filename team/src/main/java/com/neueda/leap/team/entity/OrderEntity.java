 package com.neueda.leap.team.entity;

 import jakarta.persistence.*;

 import java.math.BigDecimal;
 import java.time.LocalDateTime;
 import java.util.ArrayList;
 import java.util.List;

 @Entity
 @Table(name = "Orders")
 public class OrderEntity {

     /**
      *
      * CREATE TABLE Orders (
      *     Order_ID BIGSERIAL PRIMARY KEY,
      *     Account_ID BIGINT NOT NULL,
      *     Security_ID BIGINT NOT NULL,
      *     Side CHAR(1) NOT NULL,
      *     Quantity_Ordered NUMERIC(18,4) NOT NULL,
      *     Order_Status VARCHAR(16) NOT NULL,
      *     Created_Date TIMESTAMP NOT NULL,
      *     Updated_Date TIMESTAMP NOT NULL,
      *     FOREIGN KEY (Account_ID) REFERENCES Accounts(Account_ID),
      *     FOREIGN KEY (Security_ID) REFERENCES Securities(Security_ID),
      *     CHECK (Side IN ('B','S')),
      *     CHECK (Quantity_Ordered > 0),
      *     CHECK (Order_Status IN ('PENDING','IN_EXECUTION','CANCELLED')),
      *     CHECK (Updated_Date >= Created_Date)
      * );
      */

     @Id
     @GeneratedValue(strategy = GenerationType.IDENTITY)
     @Column(name = "Order_ID")
     private Long id;

     @ManyToOne(fetch = FetchType.LAZY, optional = false)
     @JoinColumn(name = "Account_ID", nullable = false)
     private AccountEntity accountForOrder;

     @ManyToOne(fetch = FetchType.LAZY, optional = false)
     @JoinColumn(name = "Security_ID", nullable = false)
     private SecurityEntity securityForOrder;

     @Column(name = "Side", nullable = false)
     private String side;

     @Column(name = "Quantity_Ordered", nullable = false)
     private BigDecimal quantityOrdered;

     @Column(name = "Order_Status", nullable = false)
     private String orderStatus;

     @Column(name = "Created_Date", nullable = false)
     private LocalDateTime createdDate;

     @Column(name = "Updated_Date", nullable = false)
     private LocalDateTime updatedDate;

    @OneToMany(mappedBy = "orderForExecution", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExecutionEntity> executions = new ArrayList<>();

     public OrderEntity(AccountEntity accountForOrder, SecurityEntity securityForOrder, String side, BigDecimal quantityOrdered, String orderStatus, LocalDateTime createdDate, LocalDateTime updatedDate) {
         this.accountForOrder = accountForOrder;
         this.securityForOrder = securityForOrder;
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
     }

     public SecurityEntity getSecurityForOrder() {
         return securityForOrder;
     }

     public void setSecurityForOrder(SecurityEntity securityForOrder) {
         this.securityForOrder = securityForOrder;
     }

     public String getSide() {
         return side;
     }

     public void setSide(String side) {
         this.side = side;
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

     public List<ExecutionEntity> getExecutions() {
         return executions;
     }

     public void setExecutions(List<ExecutionEntity> executions) {
         this.executions = executions;
     }
 }
