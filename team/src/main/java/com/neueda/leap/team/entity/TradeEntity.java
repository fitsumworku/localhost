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

	@Column(name = "Execution_ID", nullable = false, unique = true)
	private Long executionId;

	@Column(name = "Account_ID", nullable = false)
	private Long accountId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumns({
		@JoinColumn(name = "Execution_ID", referencedColumnName = "Execution_ID", insertable = false, updatable = false),
		@JoinColumn(name = "Account_ID", referencedColumnName = "Account_ID", insertable = false, updatable = false)
	})
	private ExecutionEntity executionForTrade;

	@Transient
	private AccountEntity accountForTrade;

	@Column(name = "Trade_Date", nullable = false)
	private LocalDateTime tradeDate;

	@Column(name = "Settlement_Date", nullable = false)
	private LocalDateTime settlementDate;


	@OneToMany(mappedBy = "tradeForDispute", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<DisputeEntity> disputes = new ArrayList<>();

	@OneToOne(mappedBy = "tradeForCashLedger", cascade = CascadeType.ALL, orphanRemoval = true)
	private CashLedgerEntity cashLedgerEntry;

	public TradeEntity() {
	}

	public TradeEntity(ExecutionEntity tradeForExecution,
					   java.math.BigDecimal tradePrice, java.math.BigDecimal shares, String tradeStatus,
					   LocalDateTime tradeDate) {
		setTradeForExecution(tradeForExecution);
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
		if (tradeForExecution != null) {
			this.executionId = tradeForExecution.getExecutionId();
			AccountEntity executionAccount = tradeForExecution.getAccountForExecution();
			this.accountId = executionAccount == null ? null : executionAccount.getAccountId();
		}
	}

	public Long getExecutionId() {
		return executionId;
	}

	public void setExecutionId(Long executionId) {
		this.executionId = executionId;
	}

	public AccountEntity getAccountForTrade() {
		// Derive from the composite execution relationship to avoid dual mapping
		return executionForTrade == null ? null : executionForTrade.getAccountForExecution();
	}

	public void setAccountForTrade(AccountEntity accountForTrade) {
		// Not used; account is set through setTradeForExecution()
		this.accountId = accountForTrade == null ? null : accountForTrade.getAccountId();
	}

	public Long getAccountId() {
		return accountId;
	}

	public void setAccountId(Long accountId) {
		this.accountId = accountId;
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

	public CashLedgerEntity getCashLedgerEntry() {
		return cashLedgerEntry;
	}

	public void setCashLedgerEntry(CashLedgerEntity cashLedgerEntry) {
		this.cashLedgerEntry = cashLedgerEntry;
	}
}
