import { ChangeDetectorRef, Component, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
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
import { ValueChartComponent } from './value-chart/value-chart.component';
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

interface Holding {
  ticker: string;
  name: string;
  shares: number;
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
  imports: [CommonModule, ValueChartComponent],
})
export class ClientDashboardComponent {
  portfolioMetrics: PortfolioMetrics | null = null;
  portfolioError = '';
  isLoadingPortfolio = false;

  holdingsData: Holding[] = [];

  // Portfolio graph: a dot each time the portfolio value changes
  chartPoints: ValuePoint[] = [];

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
  ) {
    effect(() => {
      const account = this.accountService.selectedAccount();
      if (account) {
        this.loadPortfolio(account.accountId);
        this.loadHistory(account.accountId);
      } else {
        this.portfolioMetrics = null;
        this.portfolioError = 'Select an account to view portfolio metrics.';
        this.holdingsData = [];
        this.activityData = [];
        this.transactionData = [];
        this.chartPoints = [];
        this.historyError = 'Select an account to view activity.';
      }
    });
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
        ticker: position.ticker,
        name: position.name,
        shares: position.quantity,
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
    this.chartPoints = this.tracker.record(accountId, this.portfolioMetrics.portfolioValue);
    this.changeDetector.markForCheck();
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
