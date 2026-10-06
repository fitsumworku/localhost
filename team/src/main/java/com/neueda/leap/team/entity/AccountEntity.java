 package com.neueda.leap.team.entity;

 import jakarta.persistence.*;

 import java.util.ArrayList;
 import java.util.List;
import java.time.LocalDateTime;

 @Entity
 @Table(name = "Accounts")
 public class AccountEntity {

     @Id
     @GeneratedValue(strategy = GenerationType.IDENTITY)
     @Column(name = "Account_ID")
     private Long accountId;

     @ManyToOne(fetch = FetchType.LAZY, optional = false)
     @JoinColumn(name = "User_ID", nullable = false)
     private UserEntity user;

     @Column(name = "Created_Date", nullable = false, updatable = false)
     private LocalDateTime dateCreated;

    @Column(name = "Updated_Date", nullable = false)
    private LocalDateTime updatedDate;

    @Column(name = "Currency", nullable = false, length = 3)
    private String currency;

    @OneToOne(mappedBy = "accountForCashBalance", cascade = CascadeType.ALL, orphanRemoval = true)
    private AccountCashBalanceEntity accountCashBalance;


    @OneToMany(mappedBy = "accountForPosition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AccountPositionEntity> accountPositionEntityList = new ArrayList<>();

     @OneToMany(mappedBy = "accountForOrder", cascade = CascadeType.ALL, orphanRemoval = true)
     private List<OrderEntity> orderEntityList = new ArrayList<>();

     @OneToMany(mappedBy = "accountForTransaction", cascade = CascadeType.ALL, orphanRemoval = true)
     private List<TransactionEntity> transactionEntityList = new ArrayList<>();

    @OneToMany(mappedBy = "accountForTrade", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TradeEntity> tradeEntityList = new ArrayList<>();

    @OneToMany(mappedBy = "accountForCashLedger", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CashLedgerEntity> cashLedgerEntries = new ArrayList<>();

    @OneToMany(mappedBy = "accountForDispute", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DisputeEntity> disputeEntityList = new ArrayList<>();

     // Constructors
     public AccountEntity() {}

     public AccountEntity(UserEntity user) {
         this.user = user;
         this.dateCreated = LocalDateTime.now();
         this.updatedDate = this.dateCreated;
         this.currency = "USD";
     }

     // Getters and Setters
     public Long getAccountId() {
         return accountId;
     }

     public UserEntity getUser() {
         return user;
     }

     public void setUser(UserEntity user) {
         this.user = user;
     }

     @Transient
     public Long getUserId() {
         return user == null ? null : user.getUserId();
     }

     public LocalDateTime getDateCreated() {
         return dateCreated;
     }

     public LocalDateTime getUpdatedDate() {
         return updatedDate;
     }

     public void setUpdatedDate(LocalDateTime updatedDate) {
         this.updatedDate = updatedDate;
     }

     public String getCurrency() {
         return currency;
     }

     public void setCurrency(String currency) {
         this.currency = currency;
     }

    //  public String getAccountStatus() {
    //      return accountStatus;
    //  }

    //  public void setAccountStatus(String accountStatus) {
    //      this.accountStatus = accountStatus;
    //  }
     

//     public List<AccountPositionEntity> getPositions() {
//         return positions;
//     }
//
//     public void setPositions(List<AccountPositionEntity> positions) {
//         this.positions = positions;
//     }
//
//     public void addPosition(AccountPositionEntity position) {
//         positions.add(position);
//         position.setAccount(this);
//     }
//
//     public void removePosition(AccountPositionEntity position) {
//         positions.remove(position);
//         position.setAccount(null);
//     }
//
//     public double getPortfolioValue() {
//         double positionValue = positions.stream()
//             .mapToDouble(pos -> pos.getQuantity() * pos.getAverageCostPerShare())
//             .sum();
//         return cashBalance + positionValue;
//     }
 }
