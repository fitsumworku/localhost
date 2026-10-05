package com.neueda.leap.team.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Executions")
public class ExecutionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "Execution_ID")
	private Long executionId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "Order_ID", nullable = false)
	private OrderEntity orderForExecution;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "Account_ID", nullable = false)
	private AccountEntity accountForExecution;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "Security_ID", nullable = false)
	private SecurityEntity securityForExecution;

	@Column(name = "Side", nullable = false, length = 1)
	private String side;

	@Column(name = "Status_Of_Execution", nullable = false, length = 8)
	private String statusOfExecution;

	@Column(name = "Started_At", nullable = false)
	private LocalDateTime startedAt;

	@Column(name = "Finished_At", nullable = false)
	private LocalDateTime finishedAt;

	@Column(name = "Quote_Price", precision = 28, scale = 12)
	private BigDecimal quotePrice;

	@Column(name = "Quote_Currency", length = 3)
	private String quoteCurrency;

	@Column(name = "Quote_Timestamp")
	private LocalDateTime quoteTimestamp;

	@Column(name = "Quote_Source", length = 100)
	private String quoteSource;

	@Column(name = "FX_Rate_To_USD", precision = 28, scale = 12)
	private BigDecimal fxRateToUsd;

	@Column(name = "FX_Quote_Timestamp")
	private LocalDateTime fxQuoteTimestamp;

	@Column(name = "FX_Source", length = 100)
	private String fxSource;

	@Column(name = "Quantity_Filled", precision = 28, scale = 12)
	private BigDecimal quantityFilled;

	@Column(name = "Price_Of_Execution", precision = 28, scale = 12)
	private BigDecimal priceOfExecution;

	@Column(name = "Cash_Amount", precision = 20, scale = 2, insertable = false, updatable = false)
	private BigDecimal cashAmount;

	@Column(name = "Failure_Reason")
	private String failureReason;

	@Column(name = "Exchange_Trade_ID", unique = true, length = 100)
	private String exchangeTradeId;

	@OneToMany(mappedBy = "executionForTrade", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<TradeEntity> trades = new ArrayList<>();

	public ExecutionEntity() {
	}

	public ExecutionEntity(OrderEntity orderForExecution, BigDecimal quantityFilled, BigDecimal priceOfExecution,
						   LocalDateTime dateOfExecution, LocalDateTime settlementDate,
						   String statusOfExecution, String exchangeTradeId) {
		this.orderForExecution = orderForExecution;
		this.quantityFilled = quantityFilled;
		this.priceOfExecution = priceOfExecution;
		this.startedAt = dateOfExecution;
		this.finishedAt = settlementDate == null ? dateOfExecution : settlementDate;
		this.statusOfExecution = statusOfExecution;
		this.exchangeTradeId = exchangeTradeId;
	}

	public Long getExecutionId() {
		return executionId;
	}

	public OrderEntity getOrderForExecution() {
		return orderForExecution;
	}

	public void setOrderForExecution(OrderEntity orderForExecution) {
		this.orderForExecution = orderForExecution;
	}

	@Transient
	public Long getOrderId() {
		return orderForExecution == null ? null : orderForExecution.getId();
	}

	public AccountEntity getAccountForExecution() {
		return accountForExecution;
	}

	public void setAccountForExecution(AccountEntity accountForExecution) {
		this.accountForExecution = accountForExecution;
	}

	public SecurityEntity getSecurityForExecution() {
		return securityForExecution;
	}

	public void setSecurityForExecution(SecurityEntity securityForExecution) {
		this.securityForExecution = securityForExecution;
	}

	public String getSide() {
		return side;
	}

	public void setSide(String side) {
		this.side = side;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(LocalDateTime startedAt) {
		this.startedAt = startedAt;
	}

	public LocalDateTime getFinishedAt() {
		return finishedAt;
	}

	public void setFinishedAt(LocalDateTime finishedAt) {
		this.finishedAt = finishedAt;
	}

	public BigDecimal getQuotePrice() {
		return quotePrice;
	}

	public void setQuotePrice(BigDecimal quotePrice) {
		this.quotePrice = quotePrice;
	}

	public String getQuoteCurrency() {
		return quoteCurrency;
	}

	public void setQuoteCurrency(String quoteCurrency) {
		this.quoteCurrency = quoteCurrency;
	}

	public LocalDateTime getQuoteTimestamp() {
		return quoteTimestamp;
	}

	public void setQuoteTimestamp(LocalDateTime quoteTimestamp) {
		this.quoteTimestamp = quoteTimestamp;
	}

	public String getQuoteSource() {
		return quoteSource;
	}

	public void setQuoteSource(String quoteSource) {
		this.quoteSource = quoteSource;
	}

	public BigDecimal getFxRateToUsd() {
		return fxRateToUsd;
	}

	public void setFxRateToUsd(BigDecimal fxRateToUsd) {
		this.fxRateToUsd = fxRateToUsd;
	}

	public LocalDateTime getFxQuoteTimestamp() {
		return fxQuoteTimestamp;
	}

	public void setFxQuoteTimestamp(LocalDateTime fxQuoteTimestamp) {
		this.fxQuoteTimestamp = fxQuoteTimestamp;
	}

	public String getFxSource() {
		return fxSource;
	}

	public void setFxSource(String fxSource) {
		this.fxSource = fxSource;
	}

	public BigDecimal getQuantityFilled() {
		return quantityFilled;
	}

	public void setQuantityFilled(BigDecimal quantityFilled) {
		this.quantityFilled = quantityFilled;
	}

	public BigDecimal getPriceOfExecution() {
		return priceOfExecution;
	}

	public void setPriceOfExecution(BigDecimal priceOfExecution) {
		this.priceOfExecution = priceOfExecution;
	}

	public LocalDateTime getDateOfExecution() {
		return finishedAt;
	}

	public void setDateOfExecution(LocalDateTime dateOfExecution) {
		this.finishedAt = dateOfExecution;
	}

	public LocalDateTime getSettlementDate() {
		return finishedAt;
	}

	public void setSettlementDate(LocalDateTime settlementDate) {
		this.finishedAt = settlementDate;
	}

	public String getStatusOfExecution() {
		return statusOfExecution;
	}

	public void setStatusOfExecution(String statusOfExecution) {
		this.statusOfExecution = statusOfExecution;
	}

	public String getExchangeTradeId() {
		return exchangeTradeId;
	}

	public void setExchangeTradeId(String exchangeTradeId) {
		this.exchangeTradeId = exchangeTradeId;
	}

	public BigDecimal getCashAmount() {
		return cashAmount;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public void setFailureReason(String failureReason) {
		this.failureReason = failureReason;
	}

	public List<TradeEntity> getTrades() {
		return trades;
	}

	public void setTrades(List<TradeEntity> trades) {
		this.trades = trades;
	}

}
