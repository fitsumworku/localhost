import { ChangeDetectorRef, Component, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AccountPosition, AccountService } from '../services/account.service';
import { MarketDataService, MarketQuote } from '../markets/services/market-data.service';
import {
  AccountOrder,
  AccountTrade,
  CashTransaction,
  PageResponse,
  TransactionHistoryService,
} from '../activity-history/services/transaction-history.service';

export type TimePeriod = '1W' | '1M' | '1Y';

interface Activity {
  date: string;
  asset: string;
  type: 'buy' | 'sell' | 'deposit' | 'withdrawal';
  amount: number;
  price?: number;
  pnl?: number;
}

interface PortfolioMetrics {
  portfolioValue: number;
  cashBalance: number;
  buyingPower: number;
  totalReturn: number;
}

@Component({
  selector: 'app-client-dashboard',
  templateUrl: './client-dashboard.component.html',
  styleUrl: './client-dashboard.component.scss',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatTableModule,
    MatIconModule,
    MatTabsModule
  ]
})
export class ClientDashboardComponent {
  selectedPeriod: TimePeriod = '1W';
  timePeriods: TimePeriod[] = ['1W', '1M', '1Y'];

  portfolioMetrics: PortfolioMetrics | null = null;
  portfolioError = '';
  isLoadingPortfolio = false;

  // Holdings table
  displayedColumns: string[] = ['asset', 'shares', 'avgCost', 'currentPrice', 'totalValue', 'dayChange'];
  holdingsData: any[] = [];

  // Recent activity table
  activityColumns: string[] = ['symbol', 'action', 'price', 'date'];
  activityData: any[] = [];

  // Transaction activity table
  transactionColumns: string[] = ['date', 'asset', 'type', 'amount'];
  transactionData: Activity[] = [];
  isLoadingHistory = false;
  historyError = '';

  constructor(
    private accountService: AccountService,
    private historyService: TransactionHistoryService,
    private marketData: MarketDataService,
    private changeDetector: ChangeDetectorRef,
  ) {
    effect(() => {
      const account = this.accountService.selectedAccount();
      if (account) {
        this.loadPortfolio(account.accountId);
        this.loadHistory(account.accountId);
      } else {
        this.portfolioMetrics = null;
        this.portfolioError = 'Select an account to view portfolio metrics.';
        this.activityData = [];
        this.transactionData = [];
        this.historyError = 'Select an account to view activity.';
      }
    });

  }

  private loadPortfolio(accountId: number): void {
    this.isLoadingPortfolio = true;
    this.portfolioError = '';
    console.log('[ClientDashboard] Loading portfolio metrics', { accountId });

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
          this.applyPortfolioMetrics(balance, holdings, {});
          return;
        }
        forkJoin(quoteRequests).subscribe({
          next: (quotes: Record<number, MarketQuote>) => this.applyPortfolioMetrics(balance, holdings, quotes),
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
    balance: { cashBalance: number; availableCash: number },
    holdings: AccountPosition[],
    quotes: Record<number, MarketQuote>,
  ): void {
    let marketValue = 0;
    let totalReturn = 0;
    this.holdingsData = holdings.map(position => {
      const quote = quotes[position.securityId];
      const currentPrice = quote?.price ?? position.averagePrice;
      const totalValue = position.quantity * currentPrice;
      marketValue += totalValue;
      totalReturn += position.quantity * (currentPrice - position.averagePrice);
      return {
        asset: position.name,
        shares: position.quantity,
        avgCost: position.averagePrice,
        currentPrice,
        totalValue,
        dayChange: quote?.stale ? 'Stale' : quote?.marketState ?? 'Unavailable',
      };
    });
    this.portfolioMetrics = {
      portfolioValue: balance.cashBalance + marketValue,
      cashBalance: balance.cashBalance,
      buyingPower: balance.availableCash,
      totalReturn,
    };
    console.log('[ClientDashboard] Portfolio metrics loaded', {
      holdings: holdings.length,
      ...this.portfolioMetrics,
    });
    this.changeDetector.markForCheck();
  }

  private loadHistory(accountId: number): void {
    this.isLoadingHistory = true;
    this.historyError = '';
    console.log('[ClientDashboard] Loading history for account', accountId);

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
        console.log('[ClientDashboard] History response counts', {
          cash: cashContent.length,
          orders: orderContent.length,
          trades: tradeContent.length,
        });
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
        ];

        this.activityData = activities
          .filter(activity => activity.type === 'buy' || activity.type === 'sell')
          .slice(0, 5)
          .map(activity => ({
            symbol: activity.asset,
            action: activity.type === 'buy' ? 'Buy' : 'Sell',
            price: activity.price === undefined ? '--' : `$${activity.price}`,
            date: activity.date,
          }));
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

  selectPeriod(period: TimePeriod): void {
    this.selectedPeriod = period;
    // TODO: Fetch performance data for selected period
  }

  getTransactionTypeColor(type: string): string {
    const colors: { [key: string]: string } = {
      'deposit': '#339af0',    // blue
      'withdrawal': '#ffa94d'  // orange
    };
    return colors[type] || '#999';
  }

  getTransactionTypeBadgeClass(type: string): string {
    return `badge-${type}`;
  }
}
