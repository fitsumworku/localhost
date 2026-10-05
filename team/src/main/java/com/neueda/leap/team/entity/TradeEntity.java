package com.neueda.leap.team.entity;

import jakarta.persistence.*;

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
	@JoinColumn(name = "Execution_ID", nullable = false, unique = true)
	private ExecutionEntity executionForTrade;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "Account_ID", nullable = false)
	private AccountEntity accountForTrade;

	@Column(name = "Trade_Date", nullable = false)
	private LocalDateTime tradeDate;

	@Column(name = "Settlement_Date", nullable = false)
	private LocalDateTime settlementDate;


	@OneToMany(mappedBy = "tradeForDispute", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<DisputeEntity> disputes = new ArrayList<>();

	@OneToMany(mappedBy = "tradeForCashLedger", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<CashLedgerEntity> cashLedgerEntries = new ArrayList<>();

	public TradeEntity() {
	}

	public TradeEntity(ExecutionEntity tradeForExecution,
					   java.math.BigDecimal tradePrice, java.math.BigDecimal shares, String tradeStatus,
					   LocalDateTime tradeDate) {
		this.executionForTrade = tradeForExecution;
		this.accountForTrade = null;
		this.tradeDate = tradeDate;
		this.settlementDate = tradeDate;
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

	public AccountEntity getAccountForTrade() {
		return accountForTrade;
	}

	public void setAccountForTrade(AccountEntity accountForTrade) {
		this.accountForTrade = accountForTrade;
	}

	public void setTradePrice(java.math.BigDecimal tradePrice) {
	}

	public void setShares(java.math.BigDecimal shares) {
	}

	public void setTradeStatus(String tradeStatus) {
	}

	public LocalDateTime getTradeDate() {
		return tradeDate;
	}

	public void setTradeDate(LocalDateTime tradeDate) {
		this.tradeDate = tradeDate;
	}

	public LocalDateTime getSettlementDate() {
		return settlementDate;
	}

	public void setSettlementDate(LocalDateTime settlementDate) {
		this.settlementDate = settlementDate;
	}
}
