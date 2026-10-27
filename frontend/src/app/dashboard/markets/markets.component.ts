import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, distinctUntilChanged, interval } from 'rxjs';
import { TradingDialogComponent } from './trading-dialog/trading-dialog.component';
import { MarketDataService, MarketQuote, Security, SecurityPage } from './services/market-data.service';
import { AccountService } from '../services/account.service';

type AssetFilter = 'all' | 'equity' | 'etf' | 'crypto' | 'forex';

// Re-quote the rows on screen so Open/Closed flips at the bell without a reload
const QUOTE_REFRESH_MS = 60_000;

interface MarketAsset {
  securityId: number;
  symbol: string;
  name: string;
  type: string;
  price: number | null;
  currency: string;
  marketState: MarketQuote['marketState'] | null;
  tradingOpen: boolean;
  stale: boolean;
  quoteStatus: 'loading' | 'ready' | 'unavailable';
}

@Component({
  selector: 'app-markets',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, MatDialogModule],
  templateUrl: './markets.component.html',
  styleUrl: './markets.component.scss',
})
export class MarketsComponent implements OnInit {
  searchQuery = '';
  selectedFilter: AssetFilter = 'all';
  readonly filters: { value: AssetFilter; label: string }[] = [
    { value: 'all', label: 'All' },
    { value: 'equity', label: 'Stocks' },
    { value: 'etf', label: 'ETFs' },
    { value: 'crypto', label: 'Crypto' },
    { value: 'forex', label: 'FX' },
  ];

  marketAssets: MarketAsset[] = [];
  errorMessage = '';
  isLoading = false;

  // Searching waits until typing pauses, so each keystroke doesn't cost a round of quotes
  private readonly search$ = new Subject<string>();

  constructor(
    private dialog: MatDialog,
    private marketData: MarketDataService,
    private accountService: AccountService,
    private changeDetector: ChangeDetectorRef,
  ) {
    const destroyRef = inject(DestroyRef);
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(destroyRef))
      .subscribe(() => this.loadSecurities());
    interval(QUOTE_REFRESH_MS)
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe(() => this.loadQuotes(true));
  }

  ngOnInit(): void {
    this.loadSecurities();
  }

  onSearchChange(): void {
    this.search$.next(this.searchQuery.trim());
  }

  selectFilter(filter: AssetFilter): void {
    if (filter === this.selectedFilter) return;
    this.selectedFilter = filter;
    this.loadSecurities();
  }

  private loadSecurities(): void {
    this.errorMessage = '';
    this.isLoading = true;

    // The backend only searches by text, and sorts by ticker, so crypto (X:...) and FX
    // (FX:...) never appear in the first page. Their ticker prefix finds them instead.
    const text = this.searchQuery.trim();
    const query = text || (this.selectedFilter === 'crypto' ? 'X:' : this.selectedFilter === 'forex' ? 'FX:' : '');

    this.marketData.listSecurities(query).subscribe({
      next: (page: SecurityPage) => {
        this.isLoading = false;
        this.marketAssets = page.content
          .map((security: Security) => this.toMarketAsset(security))
          .filter((asset) => this.selectedFilter === 'all' || asset.type === this.selectedFilter);
        this.loadQuotes();
        this.changeDetector.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        console.error('[Markets] Failed to load securities', { search: query, error });
        this.isLoading = false;
        this.marketAssets = [];
        this.errorMessage = 'Market data is currently unavailable.';
        this.changeDetector.markForCheck();
      },
    });
  }

  // Only the rows on screen are quoted. A background refresh keeps the last quote if it fails.
  private loadQuotes(background = false): void {
    this.marketAssets.forEach((asset) => {
      this.marketData.quote(asset.securityId).subscribe({
        next: (quote: MarketQuote) => {
          asset.price = quote.price;
          asset.currency = quote.currency || asset.currency;
          asset.marketState = quote.marketState;
          asset.tradingOpen = quote.tradingOpen;
          asset.stale = quote.stale;
          asset.quoteStatus = 'ready';
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse) => {
          console.error('[Markets] Failed to load quote', { securityId: asset.securityId, symbol: asset.symbol, error });
          if (background && asset.quoteStatus === 'ready') return;
          asset.quoteStatus = 'unavailable';
          this.changeDetector.markForCheck();
        },
      });
    });
  }

  private toMarketAsset(security: Security): MarketAsset {
    return {
      securityId: security.securityId,
      symbol: security.ticker,
      name: security.name,
      type: security.assetType.toLowerCase(),
      price: null,
      currency: security.quoteCurrency,
      marketState: null,
      tradingOpen: false,
      stale: false,
      quoteStatus: 'loading',
    };
  }

  // The backend's trading calendar decides, so this matches when orders actually fill
  isOpen(asset: MarketAsset): boolean {
    return asset.tradingOpen;
  }

  typeLabel(type: string): string {
    return this.filters.find((filter) => filter.value === type)?.label ?? type;
  }

  openTradingDialog(asset: MarketAsset): void {
    const accountId = this.accountService.selectedAccount()?.accountId;
    if (!accountId) {
      this.errorMessage = 'Create or select an account first (top right).';
      return;
    }
    if (asset.price === null) {
      this.errorMessage = `Waiting for a live price for ${asset.symbol}. Try again in a moment.`;
      return;
    }
    this.errorMessage = '';
    this.dialog.open(TradingDialogComponent, {
      width: '440px',
      maxWidth: '92vw',
      data: {
        accountId,
        securityId: asset.securityId,
        symbol: asset.symbol,
        name: asset.name,
        price: asset.price,
        currency: asset.currency,
        isOpen: this.isOpen(asset),
      },
      panelClass: ['dark-ui', 'app-dialog'],
    });
  }
}
