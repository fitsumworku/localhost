// package com.neueda.leap.team.entity;

// import jakarta.persistence.*;

// import java.util.ArrayList;
// import java.util.List;
// import java.time.LocalDateTime;

// @Entity
// @Table(name = "accounts")
// public class AccountEntity {

//     @Id
//     @GeneratedValue(strategy = GenerationType.IDENTITY)
//     @Column(name = "account_id")
//     private Long accountId;

//     @ManyToOne(fetch = FetchType.LAZY, optional = false)
//     @JoinColumn(name = "user_id", nullable = false)
//     private UserEntity user;

//     @Column(name = "created_date", nullable = false, updatable = false)
//     private LocalDateTime dateCreated;

//     @Column(name = "status", nullable = false)
//     private String accountStatus; 

//     @Column
//     private double cashBalance;  // Available cash balance

//     @Column
//     private double reservedCash;  // Cash reserved for pending orders

//     @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
//     private List<AccountPositionEntity> positions = new ArrayList<>();

//     // Constructors
//     public AccountEntity() {}

//     public AccountEntity(UserEntity user) {
//         this.user = user;
//         this.dateCreated = LocalDateTime.now();
//         this.accountStatus = "ACTIVE";
//         this.cashBalance = 0;
//         this.reservedCash = 0;
//     }

//     public AccountEntity(UserEntity user, double initialCash) {
//         this.user = user;
//         this.dateCreated = LocalDateTime.now();
//         this.accountStatus = "ACTIVE";
//         this.cashBalance = initialCash;
//         this.reservedCash = 0;
//     }

//     // Getters and Setters
//     public Long getAccountId() {
//         return accountId;
//     }

//     public UserEntity getUser() {
//         return user;
//     }

//     public void setUser(UserEntity user) {
//         this.user = user;
//     }

//     @Transient
//     public Long getUserId() {
//         return user == null ? null : user.getUserId();
//     }

//     public LocalDateTime getDateCreated() {
//         return dateCreated;
//     }

//     public String getAccountStatus() {
//         return accountStatus;
//     }

//     public void setAccountStatus(String accountStatus) {
//         this.accountStatus = accountStatus;
//     }

//     public double getCashBalance() {
//         return cashBalance;
//     }

//     public void setCashBalance(double cashBalance) {
//         this.cashBalance = cashBalance;
//     }

//     public double getReservedCash() {
//         return reservedCash;
//     }

//     public void setReservedCash(double reservedCash) {
//         this.reservedCash = reservedCash;
//     }

//     public double getAvailableBalance() {
//         return cashBalance - reservedCash;
//     }

//     public List<AccountPositionEntity> getPositions() {
//         return positions;
//     }

//     public void setPositions(List<AccountPositionEntity> positions) {
//         this.positions = positions;
//     }

//     public void addPosition(AccountPositionEntity position) {
//         positions.add(position);
//         position.setAccount(this);
//     }

//     public void removePosition(AccountPositionEntity position) {
//         positions.remove(position);
//         position.setAccount(null);
//     }

//     public double getPortfolioValue() {
//         double positionValue = positions.stream()
//             .mapToDouble(pos -> pos.getQuantity() * pos.getAverageCostPerShare())
//             .sum();
//         return cashBalance + positionValue;
//     }
// }
