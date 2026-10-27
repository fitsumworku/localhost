import { ChangeDetectorRef, Component, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AccountPosition, AccountService, ValueHistory, ValueRange } from '../services/account.service';
import { MarketDataService, MarketQuote } from '../markets/services/market-data.service';
import {
  AccountOrder,
  AccountTrade,
  CashTransaction,
  PageResponse,
  TransactionHistoryService,
} from '../activity-history/services/transaction-history.service';
import { ValueChartComponent } from './value-chart/value-chart.component';
import { TradeData, TradingDialogComponent } from '../markets/trading-dialog/trading-dialog.component';
import { PortfolioTrackerService, ValuePoint } from './portfolio-tracker.service';

interface Activity {
  date: string;
  asset: string;
  type: 'buy' | 'sell' | 'deposit' | 'withdrawal';
  amount: number;
  price?: number;
}

interface PortfolioMetrics {
  portfolioValue: number;
  buyingPower: number;
}

const signedMoney = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', signDisplay: 'always' });
const signedPercent = new Intl.NumberFormat('en-US', { style: 'percent', maximumFractionDigits: 2, signDisplay: 'always' });

// A history point is the value at that day's close; drawn at 4 PM local time on that date
function closeTime(date: string): number {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(year, month - 1, day, 16).getTime();
}

interface Holding {
  securityId: number;
  ticker: string;
  name: string;
  currency: string;
  shares: number;
  // Shares not already reserved by pending sell orders
  availableShares: number;
  tradingOpen: boolean;
  avgCost: number;
  currentPrice: number;
  totalValue: number;
  return: number;
  priceIsLive: boolean;
}

@Component({
  selector: 'app-client-dashboard',
  templateUrl: './client-dashboard.component.html',
  styleUrl: './client-dashboard.component.scss',
  standalone: true,
  imports: [CommonModule, MatDialogModule, ValueChartComponent],
})
export class ClientDashboardComponent {
  portfolioMetrics: PortfolioMetrics | null = null;
  portfolioError = '';
  isLoadingPortfolio = false;

  holdingsData: Holding[] = [];

  // Portfolio graph: one point per day's close for the chosen range, then live readings since the page loaded
  readonly ranges: { value: ValueRange; caption: string }[] = [
    { value: '1D', caption: 'since yesterday’s close' },
    { value: '1W', caption: 'past week' },
    { value: '1M', caption: 'past month' },
    { value: '1Y', caption: 'past year' },
  ];
  selectedRange: ValueRange = '1M';
  chartPoints: ValuePoint[] = [];
  rangeChange: { text: string; up: boolean } | null = null;
  historyNote = '';
  private dailyPoints: ValuePoint[] = [];
  private livePoints: ValuePoint[] = [];
  private historyRequest = 0;

  activityData: Activity[] = [];
  transactionData: Activity[] = [];
  isLoadingHistory = false;
  historyError = '';

  constructor(
    private accountService: AccountService,
    private historyService: TransactionHistoryService,
    private marketData: MarketDataService,
    private tracker: PortfolioTrackerService,
    private changeDetector: ChangeDetectorRef,
    private dialog: MatDialog,
  ) {
    effect(() => {
      const account = this.accountService.selectedAccount();
      if (account) {
        this.dailyPoints = [];
        this.livePoints = [];
        this.loadPortfolio(account.accountId);
        this.loadValueHistory(account.accountId);
        this.loadHistory(account.accountId);
      } else {
        this.portfolioMetrics = null;
        this.portfolioError = 'Select an account to view portfolio metrics.';
        this.holdingsData = [];
        this.activityData = [];
        this.transactionData = [];
        this.dailyPoints = [];
        this.livePoints = [];
        this.historyNote = '';
        this.updateChart();
        this.historyError = 'Select an account to view activity.';
      }
    });
  }

  /** Buy or sell more of a holding without going back to Markets. */
  openTrade(holding: Holding, side: 'buy' | 'sell'): void {
    const account = this.accountService.selectedAccount();
    if (!account) return;
    const data: TradeData = {
      accountId: account.accountId,
      securityId: holding.securityId,
      symbol: holding.ticker,
      name: holding.name,
      // Without a live quote this is the average cost; it only feeds the estimate, the order fills at the live price
      price: holding.currentPrice,
      currency: holding.currency,
      isOpen: holding.tradingOpen,
      initialSide: side,
      availableQuantity: holding.availableShares,
    };
    const dialogRef = this.dialog.open(TradingDialogComponent, {
      width: '440px',
      maxWidth: '92vw',
      data,
      panelClass: ['dark-ui', 'app-dialog'],
    });
    dialogRef.afterClosed().subscribe(() => {
      // Orders reserve cash/shares as soon as they are placed, so refresh even if still pending
      if (dialogRef.componentInstance?.orderPlaced && this.accountService.selectedAccount()?.accountId === account.accountId) {
        this.loadPortfolio(account.accountId);
        this.loadHistory(account.accountId);
      }
    });
  }

  get rangeCaption(): string {
    return this.ranges.find((range) => range.value === this.selectedRange)?.caption ?? '';
  }

  selectRange(range: ValueRange): void {
    const account = this.accountService.selectedAccount();
    if (range === this.selectedRange || !account) return;
    this.selectedRange = range;
    this.loadValueHistory(account.accountId);
  }

  // Each day's close comes from the backend, which prices past holdings with daily candles
  private loadValueHistory(accountId: number): void {
    const request = ++this.historyRequest;
    this.accountService.valueHistory(accountId, this.selectedRange).subscribe({
      next: (history: ValueHistory) => {
        if (request !== this.historyRequest) return; // another range or account was picked meanwhile
        this.dailyPoints = history.points.map((point) => ({ time: closeTime(point.date), value: point.value, daily: true }));
        this.historyNote = history.complete
          ? ''
          : 'Some holdings have no price history for this range and are shown at their last trade price.';
        this.updateChart();
      },
      error: (error: unknown) => {
        if (request !== this.historyRequest) return;
        console.error('[ClientDashboard] Failed to load value history', { accountId, range: this.selectedRange, error });
        this.dailyPoints = [];
        this.historyNote = 'Earlier values are currently unavailable; showing today only.';
        this.updateChart();
      },
    });
  }

  private updateChart(): void {
    const lastClose = this.dailyPoints.length ? this.dailyPoints[this.dailyPoints.length - 1].time : -Infinity;
    this.chartPoints = [...this.dailyPoints, ...this.livePoints.filter((point) => point.time > lastClose)];

    const first = this.chartPoints[0];
    const last = this.chartPoints[this.chartPoints.length - 1];
    if (this.chartPoints.length < 2) {
      this.rangeChange = null;
    } else {
      const amount = last.value - first.value;
      const percent = first.value > 0 ? ` (${signedPercent.format(amount / first.value)})` : '';
      this.rangeChange = { text: signedMoney.format(amount) + percent, up: amount >= 0 };
    }
    this.changeDetector.markForCheck();
  }

  private loadPortfolio(accountId: number): void {
    this.isLoadingPortfolio = true;
    this.portfolioError = '';

    forkJoin({
      balance: this.accountService.balance(accountId),
      positions: this.accountService.positions(accountId),
    }).pipe(finalize(() => {
      this.isLoadingPortfolio = false;
      this.changeDetector.markForCheck();
    })).subscribe({
      next: ({ balance, positions }: { balance: { cashBalance: number; availableCash: number }; positions: { content: AccountPosition[] } }) => {
        const holdings = Array.isArray(positions?.content) ? positions.content : [];
        const quoteRequests: Record<number, ReturnType<MarketDataService['quote']>> = {};
        holdings.forEach(position => quoteRequests[position.securityId] = this.marketData.quote(position.securityId));
        if (holdings.length === 0) {
          this.applyPortfolioMetrics(accountId, balance, holdings, {});
          return;
        }
        forkJoin(quoteRequests).subscribe({
          next: (quotes: Record<number, MarketQuote>) => this.applyPortfolioMetrics(accountId, balance, holdings, quotes),
          error: (error: unknown) => {
            console.error('[ClientDashboard] Failed to load portfolio quotes', { accountId, error });
            this.portfolioError = 'Portfolio prices are currently unavailable.';
            this.changeDetector.markForCheck();
          },
        });
      },
      error: (error: unknown) => {
        console.error('[ClientDashboard] Failed to load portfolio data', { accountId, error });
        this.portfolioMetrics = null;
        this.portfolioError = 'Portfolio data is currently unavailable.';
        this.changeDetector.markForCheck();
      },
    });
  }

  private applyPortfolioMetrics(
    accountId: number,
    balance: { cashBalance: number; availableCash: number },
    holdings: AccountPosition[],
    quotes: Record<number, MarketQuote>,
  ): void {
    let marketValue = 0;
    this.holdingsData = holdings.map(position => {
      const quote = quotes[position.securityId];
      const currentPrice = quote?.price ?? position.averagePrice;
      const totalValue = position.quantity * currentPrice;
      const cost = position.quantity * position.averagePrice;
      marketValue += totalValue;
      return {
        securityId: position.securityId,
        ticker: position.ticker,
        name: position.name,
        currency: quote?.currency || position.quoteCurrency,
        shares: position.quantity,
        availableShares: position.availableQuantity,
        tradingOpen: quote?.tradingOpen ?? false,
        avgCost: position.averagePrice,
        currentPrice,
        totalValue,
        return: Math.round((totalValue - cost) * 100) / 100,
        priceIsLive: !!quote,
      };
    });
    this.portfolioMetrics = {
      // Cash is still part of what the account is worth, it just isn't shown as its own card
      portfolioValue: balance.cashBalance + marketValue,
      buyingPower: balance.availableCash,
    };
    this.livePoints = this.tracker.record(accountId, this.portfolioMetrics.portfolioValue);
    this.updateChart();
  }

  private loadHistory(accountId: number): void {
    this.isLoadingHistory = true;
    this.historyError = '';

    forkJoin({
      cash: this.historyService.cashTransactions(accountId),
      orders: this.historyService.orders(accountId),
      trades: this.historyService.trades(accountId),
    }).pipe(finalize(() => {
      this.isLoadingHistory = false;
      this.changeDetector.markForCheck();
    })).subscribe({
      next: ({ cash, orders, trades }: {
        cash: PageResponse<CashTransaction>;
        orders: PageResponse<AccountOrder>;
        trades: PageResponse<AccountTrade>;
      }) => {
        const cashContent = this.contentOf(cash);
        const orderContent = this.contentOf(orders);
        const tradeContent = this.contentOf(trades);

        const activities: Activity[] = [
          ...cashContent.map((transaction: CashTransaction) => ({
            date: transaction.transactionDate,
            asset: 'Cash',
            type: transaction.type === 'DEPOSIT' ? 'deposit' as const : 'withdrawal' as const,
            amount: transaction.amount,
          })),
          ...orderContent
            .filter((order: AccountOrder) => order.orderStatus !== 'FILLED')
            .map((order: AccountOrder) => ({
              date: order.createdDate,
              asset: order.ticker,
              type: order.side === 'B' ? 'buy' as const : 'sell' as const,
              amount: order.requestedAmount ?? order.quantityOrdered ?? 0,
            })),
          ...tradeContent.map((trade: AccountTrade) => ({
            date: trade.tradeDate,
            asset: trade.ticker,
            type: trade.side === 'B' ? 'buy' as const : 'sell' as const,
            amount: trade.cashAmount,
            price: trade.priceOfExecution,
          })),
        ].sort((a, b) => Date.parse(b.date) - Date.parse(a.date));

        this.activityData = activities
          .filter(activity => activity.type === 'buy' || activity.type === 'sell')
          .slice(0, 5);
        this.transactionData = activities
          .filter(activity => activity.type === 'deposit' || activity.type === 'withdrawal')
          .slice(0, 5);
        this.changeDetector.markForCheck();
      },
      error: (error: unknown) => {
        console.error('[ClientDashboard] Failed to load history', { accountId, error });
        this.activityData = [];
        this.transactionData = [];
        this.historyError = 'Transaction history is currently unavailable.';
        this.changeDetector.markForCheck();
      },
    });
  }

  private contentOf<T>(page: PageResponse<T>): T[] {
    return Array.isArray(page?.content) ? page.content : [];
  }

}
