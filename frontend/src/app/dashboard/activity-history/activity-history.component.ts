import { ChangeDetectorRef, Component, OnInit, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import {
  DisputeDialogData,
  DisputeDialogResult,
  DisputeTransactionDialogComponent,
} from './dispute-transaction-dialog/dispute-transaction-dialog.component';
import { AccountPosition, AccountService } from '../services/account.service';
import { MarketDataService, MarketQuote } from '../markets/services/market-data.service';
import {
  AccountOrder,
  AccountTrade,
  AccountDispute,
  CashTransaction,
  CreateDisputeRequest,
  TransactionHistoryService,
} from './services/transaction-history.service';

export interface Activity {
  date: string;
  asset: string;
  type: 'buy' | 'sell' | 'deposit' | 'withdrawal';
  amount: number;
  price?: number;
  pnl?: number;
  transactionId?: number;
  tradeId?: number;
}

interface PortfolioMetrics {
  portfolioValue: number;
  cashBalance: number;
  buyingPower: number;
  totalReturn: number;
}

@Component({
  selector: 'app-transaction-history',
  templateUrl: './activity-history.component.html',
  styleUrl: './activity-history.component.scss',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatTableModule,
    MatIconModule,
    MatDialogModule
  ]
})
export class TransactionHistoryComponent {
  portfolioMetrics: PortfolioMetrics | null = null;
  isLoadingPortfolio = false;
  portfolioError = '';

  // Activity History Table
  displayedColumns: string[] = ['date', 'asset', 'type', 'amount', 'price', 'pnl', 'actions'];
  transactionData: Activity[] = [];
  disputeData: AccountDispute[] = [];
  isLoading = false;
  errorMessage = '';

  constructor(
    private dialog: MatDialog,
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
        this.transactionData = [];
        this.disputeData = [];
        this.errorMessage = 'Select an account to view transaction history.';
      }
    });
  }

  private loadPortfolio(accountId: number): void {
    this.isLoadingPortfolio = true;
    this.portfolioError = '';
    console.log('[TransactionHistory] Loading portfolio metrics', { accountId });

    forkJoin({
      balance: this.accountService.balance(accountId),
      positions: this.accountService.positions(accountId),
    }).pipe(finalize(() => {
      this.isLoadingPortfolio = false;
      this.changeDetector.markForCheck();
    })).subscribe({
      next: ({ balance, positions }: {
        balance: { cashBalance: number; availableCash: number };
        positions: { content: AccountPosition[] };
      }) => {
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
            console.error('[TransactionHistory] Failed to load portfolio quotes', { accountId, error });
            this.portfolioError = 'Portfolio prices are currently unavailable.';
            this.changeDetector.markForCheck();
          },
        });
      },
      error: (error: unknown) => {
        console.error('[TransactionHistory] Failed to load portfolio data', { accountId, error });
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
    holdings.forEach(position => {
      const quote = quotes[position.securityId];
      const currentPrice = quote?.price ?? position.averagePrice;
      marketValue += position.quantity * currentPrice;
      totalReturn += position.quantity * (currentPrice - position.averagePrice);
    });
    this.portfolioMetrics = {
      portfolioValue: balance.cashBalance + marketValue,
      cashBalance: balance.cashBalance,
      buyingPower: balance.availableCash,
      totalReturn,
    };
    console.log('[TransactionHistory] Portfolio metrics loaded', {
      holdings: holdings.length,
      ...this.portfolioMetrics,
    });
    this.changeDetector.markForCheck();
  }

  private loadHistory(accountId: number): void {
    this.isLoading = true;
    this.errorMessage = '';
    console.log('[TransactionHistory] Loading history for account', accountId);

    forkJoin({
      cash: this.historyService.cashTransactions(accountId),
      orders: this.historyService.orders(accountId),
      trades: this.historyService.trades(accountId),
      disputes: this.historyService.disputes(accountId),
    }).pipe(finalize(() => {
      this.isLoading = false;
      this.changeDetector.markForCheck();
    })).subscribe({
      next: ({ cash, orders, trades, disputes }: {
        cash: { content: CashTransaction[] };
        orders: { content: AccountOrder[] };
        trades: { content: AccountTrade[] };
        disputes: { content: AccountDispute[] };
      }) => {
        if (this.accountService.selectedAccount()?.accountId !== accountId) return;

        const cashContent = this.contentOf(cash);
        const orderContent = this.contentOf(orders);
        const tradeContent = this.contentOf(trades);
        this.disputeData = this.contentOf(disputes);
        console.log('[TransactionHistory] History response counts', {
          cash: cashContent.length,
          orders: orderContent.length,
          trades: tradeContent.length,
        });
        const activities: Activity[] = [
          ...cashContent.map((transaction: CashTransaction) => this.cashActivity(transaction)),
          ...orderContent
            .filter((order: AccountOrder) => order.orderStatus !== 'FILLED')
            .map((order: AccountOrder) => this.orderActivity(order)),
          ...tradeContent.map((trade: AccountTrade) => this.tradeActivity(trade)),
        ];
        this.transactionData = activities.sort((a, b) => b.date.localeCompare(a.date));
        this.changeDetector.markForCheck();
      },
      error: (error: unknown) => {
        if (this.accountService.selectedAccount()?.accountId !== accountId) return;

        console.error('[TransactionHistory] Failed to load history', { accountId, error });
        this.transactionData = [];
        this.disputeData = [];
        this.errorMessage = 'Transaction history is currently unavailable.';
        this.changeDetector.markForCheck();
      },
    });
  }

  private contentOf<T>(page: { content?: T[] }): T[] {
    return Array.isArray(page?.content) ? page.content : [];
  }

  private cashActivity(transaction: CashTransaction): Activity {
    return {
      date: transaction.transactionDate,
      asset: 'Cash',
      type: transaction.type === 'DEPOSIT' ? 'deposit' : 'withdrawal',
      amount: transaction.amount,
      transactionId: transaction.transactionId,
    };
  }

  private orderActivity(order: AccountOrder): Activity {
    return {
      date: order.createdDate,
      asset: order.ticker,
      type: order.side === 'B' ? 'buy' : 'sell',
      amount: order.requestedAmount ?? order.quantityOrdered ?? 0,
    };
  }

  private tradeActivity(trade: AccountTrade): Activity {
    return {
      date: trade.tradeDate,
      asset: trade.ticker,
      type: trade.side === 'B' ? 'buy' : 'sell',
      amount: trade.cashAmount,
      price: trade.priceOfExecution,
      tradeId: trade.tradeId,
    };
  }

  openDisputeDialog(activity: Activity): void {
    const account = this.accountService.selectedAccount();
    if (!account || (activity.transactionId === undefined && activity.tradeId === undefined)) {
      return;
    }

    const data: DisputeDialogData = { accountId: account.accountId, activity };
    const dialogRef = this.dialog.open(DisputeTransactionDialogComponent, {
      width: '90%',
      maxWidth: '500px',
      maxHeight: '90vh',
      data,
      disableClose: false,
      panelClass: 'dispute-dialog-panel',
    });

    dialogRef.afterClosed().subscribe((result: DisputeDialogResult | undefined) => {
      if (!result) return;

      const request = activity.transactionId !== undefined
        ? this.historyService.createTransactionDispute(account.accountId, activity.transactionId, {
          disputeType: this.disputeTypeFor(activity.type),
          description: result.description,
        })
        : this.historyService.createTradeDispute(account.accountId, activity.tradeId!, {
          disputeType: this.disputeTypeFor(activity.type),
          description: result.description,
        });

      request.subscribe({
        next: () => {
          if (this.accountService.selectedAccount()?.accountId === account.accountId) {
            this.loadHistory(account.accountId);
          }
        },
        error: (error: unknown) => {
          if (this.accountService.selectedAccount()?.accountId !== account.accountId) return;

          console.error('[TransactionHistory] Failed to submit dispute', { accountId: account.accountId, error });
          this.errorMessage = 'The dispute could not be submitted.';
          this.changeDetector.markForCheck();
        },
      });
    });
  }

  private disputeTypeFor(type: Activity['type']): CreateDisputeRequest['disputeType'] {
    return type.toUpperCase();
  }

  getTypeColor(type: string): string {
    switch(type) {
      case 'buy':
        return '#51cf66';
      case 'sell':
        return '#ff6b6b';
      case 'deposit':
        return '#339af0';
      case 'withdrawal':
        return '#ffa94d';
      default:
        return '#868e96';
    }
  }

  getPnlColor(pnl: number | undefined): string {
    if (pnl === undefined) return '#868e96';
    return pnl >= 0 ? '#51cf66' : '#ff6b6b';
  }

  isTransactionActivity(type: string): boolean {
    return type === 'deposit' || type === 'withdrawal';
  }
}
