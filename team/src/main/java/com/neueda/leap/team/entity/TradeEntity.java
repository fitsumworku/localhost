package com.neueda.leap.team.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Trades")
public class TradeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "Trade_ID")
	private Long tradeId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "Execution_ID", nullable = false)
	private ExecutionEntity executionForTrade;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "Security_ID", nullable = false)
	private SecurityEntity securityForTrade;

	@Column(name = "Trade_Price", nullable = false, precision = 18, scale = 4)
	private BigDecimal tradePrice;

	@Column(name = "Shares", nullable = false, precision = 18, scale = 4)
	private BigDecimal shares;

	@Column(name = "Trade_Status", nullable = false, length = 18)
	private String tradeStatus;

	@Column(name = "Trade_Date", nullable = false)
	private LocalDateTime tradeDate;

	@OneToMany(mappedBy = "tradeForDispute", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<DisputeEntity> disputes = new ArrayList<>();

	@OneToMany(mappedBy = "tradeForCashLedger", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<CashLedgerEntity> cashLedgerEntries = new ArrayList<>();

	public TradeEntity() {
	}

	public TradeEntity(ExecutionEntity tradeForExecution, SecurityEntity securityForTrade,
					   BigDecimal tradePrice, BigDecimal shares, String tradeStatus,
					   LocalDateTime tradeDate) {
		this.executionForTrade = tradeForExecution;
		this.securityForTrade = securityForTrade;
		this.tradePrice = tradePrice;
		this.shares = shares;
		this.tradeStatus = tradeStatus;
		this.tradeDate = tradeDate;
	}

	@PrePersist
	@PreUpdate
	private void validateConstraints() {
		if (tradePrice == null || tradePrice.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Trade_Price must be greater than 0");
		}
		if (shares == null || shares.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Shares must be greater than 0");
		}
		if (tradeStatus == null ||
			!(tradeStatus.equals("PENDING") ||
			  tradeStatus.equals("SETTLED") ||
			  tradeStatus.equals("DISPUTED") ||
			  tradeStatus.equals("REVERSED"))) {
			throw new IllegalArgumentException("Trade_Status must be one of PENDING, SETTLED, DISPUTED, REVERSED");
		}
	}

	public Long getTradeId() {
		return tradeId;
	}

	public ExecutionEntity getTradeForExecution() {
		return executionForTrade;
	}

	public void setTradeForExecution(ExecutionEntity tradeForExecution) {
		this.executionForTrade = tradeForExecution;
	}

	@Transient
	public Long getExecutionId() {
		return executionForTrade == null ? null : executionForTrade.getExecutionId();
	}

	public SecurityEntity getSecurityForTrade() {
		return securityForTrade;
	}

	public void setSecurityForTrade(SecurityEntity securityForTrade) {
		this.securityForTrade = securityForTrade;
	}

	@Transient
	public Long getSecurityId() {
		return securityForTrade == null ? null : securityForTrade.getSecurityId();
	}

	public BigDecimal getTradePrice() {
		return tradePrice;
	}

	public void setTradePrice(BigDecimal tradePrice) {
		this.tradePrice = tradePrice;
	}

	public BigDecimal getShares() {
		return shares;
	}

	public void setShares(BigDecimal shares) {
		this.shares = shares;
	}

	public String getTradeStatus() {
		return tradeStatus;
	}

	public void setTradeStatus(String tradeStatus) {
		this.tradeStatus = tradeStatus;
	}

	public LocalDateTime getTradeDate() {
		return tradeDate;
	}

	public void setTradeDate(LocalDateTime tradeDate) {
		this.tradeDate = tradeDate;
	}
}
