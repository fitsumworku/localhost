// package com.neueda.leap.team.entity;

// import jakarta.persistence.*;

// import java.time.LocalDateTime;
// import java.util.UUID;

// @Entity
// @Table(name = "account_positions", uniqueConstraints = {
//     @UniqueConstraint(columnNames = {"account_id", "security_id"})
// })
// public class AccountPositionEntity {

// //CREATE TABLE Account_Positions (
// //            Position_ID BIGSERIAL PRIMARY KEY,
// //            Account_ID BIGINT NOT NULL,
// //            Security_ID BIGINT NOT NULL,
// //            Total_Shares NUMERIC(18,4),
// //    Average_Price NUMERIC(18,4),
// //    Updated_Date TIMESTAMP,
// //    FOREIGN KEY (Account_ID) REFERENCES Accounts(Account_ID),
// //    FOREIGN KEY (Security_ID) REFERENCES Securities(Security_ID),
// //    UNIQUE (Account_ID, Security_ID),
// //    CHECK (Total_Shares >= 0),
// //    CHECK (Average_Price >= 0)
// //);
//     @Id
//     @GeneratedValue(strategy = GenerationType.UUID)
//     private UUID positionId;

//     @ManyToOne(fetch = FetchType.LAZY, optional = false)
//     @JoinColumn(name = "account_id", nullable = false)
//     private AccountEntity account;

//     @ManyToOne(fetch = FetchType.LAZY, optional = false)
//     @JoinColumn(name = "security_id", nullable = false)
//     private SecurityEntity security;

//     @Column(nullable = false)
//     private double totalShares;

//     @Column(nullable = false)
//     private double averagePrice;

//     @Column(nullable = false)
//     private LocalDateTime updatedDate;

//     // Constructors
//     public AccountPositionEntity() {}

//     public AccountPositionEntity(AccountEntity account, SecurityEntity security) {
//         this.account = account;
//         this.security = security;
//         this.totalShares = 0;
//         this.averagePrice = 0;
//         this.updatedDate = LocalDateTime.now();
//     }

//     // Getters and Setters
//     public UUID getPositionId() {
//         return positionId;
//     }

//     public AccountEntity getAccount() {
//         return account;
//     }

//     public void setAccount(AccountEntity account) {
//         this.account = account;
//     }

//     @Transient
//     public Long getAccountId() {
//         return account == null ? null : account.getAccountId();
//     }

//     public SecurityEntity getSecurity() {
//         return security;
//     }

//     public void setSecurity(SecurityEntity security) {
//         this.security = security;
//     }

//     @Transient
//     public UUID getSecurityId() {
//         return security == null ? null : security.getSecurityId();
//     }

//     public double getTotalShares() {
//         return totalShares;
//     }

//     public void setTotalShares(double totalShares) {
//         this.totalShares = totalShares;
//     }

//     public double getAveragePrice() {
//         return averagePrice;
//     }

//     public void setAveragePrice(double averagePrice) {
//         this.averagePrice = averagePrice;
//     }

//     public LocalDateTime getUpdatedDate() {
//         return updatedDate;
//     }

//     public void setUpdatedDate(LocalDateTime updatedDate) {
//         this.updatedDate = updatedDate;
//     }

//     public double getQuantity() {
//         return totalShares;
//     }

//     public double getAverageCostPerShare() {
//         return averagePrice;
//     }
// }

