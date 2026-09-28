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

	@Column(name = "Quantity_Filled", nullable = false, precision = 18, scale = 4)
	private BigDecimal quantityFilled;

	@Column(name = "Price_Of_Execution", nullable = false, precision = 18, scale = 4)
	private BigDecimal priceOfExecution;

	@Column(name = "Date_Of_Execution", nullable = false)
	private LocalDateTime dateOfExecution;

	@Column(name = "Settlement_Date")
	private LocalDateTime settlementDate;

	@Column(name = "Status_Of_Execution", nullable = false, length = 20)
	private String statusOfExecution;

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
		this.dateOfExecution = dateOfExecution;
		this.settlementDate = settlementDate;
		this.statusOfExecution = statusOfExecution;
		this.exchangeTradeId = exchangeTradeId;
	}

	@PrePersist
	@PreUpdate
	private void validateConstraints() {
		if (quantityFilled == null || quantityFilled.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Quantity_Filled must be greater than 0");
		}
		if (priceOfExecution == null || priceOfExecution.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Price_Of_Execution must be greater than 0");
		}
		if (statusOfExecution == null ||
			!(statusOfExecution.equals("PENDING") ||
			  statusOfExecution.equals("FILLED") ||
			  statusOfExecution.equals("PARTIALLY_FILLED") ||
			  statusOfExecution.equals("FAILED"))) {
			throw new IllegalArgumentException("Status_Of_Execution must be one of PENDING, FILLED, PARTIALLY_FILLED, FAILED");
		}
		if (settlementDate != null && dateOfExecution != null && settlementDate.isBefore(dateOfExecution)) {
			throw new IllegalArgumentException("Settlement_Date must be greater than or equal to Date_Of_Execution");
		}
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
		return dateOfExecution;
	}

	public void setDateOfExecution(LocalDateTime dateOfExecution) {
		this.dateOfExecution = dateOfExecution;
	}

	public LocalDateTime getSettlementDate() {
		return settlementDate;
	}

	public void setSettlementDate(LocalDateTime settlementDate) {
		this.settlementDate = settlementDate;
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

	public List<TradeEntity> getTrades() {
		return trades;
	}

	public void setTrades(List<TradeEntity> trades) {
		this.trades = trades;
	}

}
