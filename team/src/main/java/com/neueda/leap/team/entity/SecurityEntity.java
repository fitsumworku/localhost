 package com.neueda.leap.team.entity;

 import jakarta.persistence.*;

 import java.util.ArrayList;
 import java.util.List;

 @Entity
 @Table(name = "Securities")
 public class SecurityEntity {
     @Id
     @GeneratedValue(strategy = GenerationType.IDENTITY)
     @Column(name = "Security_ID")
     private Long securityId;

     @Column(name = "Ticker", nullable = false)
     private String ticker;

     @Column(name = "Name", nullable = false)
     private String name;

     @Column(name = "Asset_Type", nullable = false)
     private String assetType;

     @Column(name = "Exchange", nullable = false)
     private String exchange;

    @Column(name = "Quote_Currency", nullable = false, length = 3)
    private String quoteCurrency;

    @Column(name = "Base_Currency", length = 3)
    private String baseCurrency;

     @Column(name = "Status", nullable = false)
     private String status;

    @Column(name = "Sector")
     private String sector;

     @OneToMany(mappedBy = "securityForAccountPosition", cascade = CascadeType.ALL, orphanRemoval = true)
     private List<AccountPositionEntity> positions = new ArrayList<>();

     @OneToMany(mappedBy = "securityForOrder", cascade = CascadeType.ALL, orphanRemoval = true)
     private List<OrderEntity> orders = new ArrayList<>();

    @OneToMany(mappedBy = "securityForExecution", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExecutionEntity> trade = new ArrayList<>();



     public SecurityEntity() {}

     public SecurityEntity(String ticker, String name, String assetType, String exchange, String status, String sector) {
         this(ticker, name, assetType, exchange, "USD", null, status, sector);
     }

     public SecurityEntity(String ticker, String name, String assetType, String exchange, String quoteCurrency, String baseCurrency, String status, String sector) {
         this.ticker = ticker;
         this.name = name;
         this.assetType = assetType;
         this.exchange = exchange;
         this.quoteCurrency = quoteCurrency;
         this.baseCurrency = baseCurrency;
         this.status = status;
         this.sector = sector;
     }

     public Long getSecurityId() {
         return securityId;
     }

     public String getTicker() {
         return ticker;
     }

     public void setTicker(String ticker) {
         this.ticker = ticker;
     }

     public String getName() {
         return name;
     }

     public void setName(String name) {
         this.name = name;
     }

     public String getAssetType() {
         return assetType;
     }

     public void setAssetType(String assetType) {
         this.assetType = assetType;
     }

     public String getExchange() {
         return exchange;
     }

     public void setExchange(String exchange) {
         this.exchange = exchange;
     }

     public String getQuoteCurrency() {
         return quoteCurrency;
     }

     public void setQuoteCurrency(String quoteCurrency) {
         this.quoteCurrency = quoteCurrency;
     }

     public String getBaseCurrency() {
         return baseCurrency;
     }

     public void setBaseCurrency(String baseCurrency) {
         this.baseCurrency = baseCurrency;
     }

     public String getStatus() {
         return status;
     }

     public void setStatus(String status) {
         this.status = status;
     }

     public String getSector() {
         return sector;
     }

     public void setSector(String sector) {
         this.sector = sector;
     }

     public List<AccountPositionEntity> getPositions() {
         return positions;
     }

     public void setPositions(List<AccountPositionEntity> positions) {
         this.positions = positions;
     }

     public void addPosition(AccountPositionEntity position) {
         positions.add(position);
         position.setSecurity(this);
     }

     public void removePosition(AccountPositionEntity position) {
         positions.remove(position);
         position.setSecurity(null);
     }

     public List<ExecutionEntity> getTrade() {
         return trade;
     }

     public void setTrade(List<ExecutionEntity> trade) {
         this.trade = trade;
     }

    
 }
