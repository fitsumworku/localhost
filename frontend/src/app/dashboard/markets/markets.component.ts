import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { HttpErrorResponse } from '@angular/common/http';
import { TradingDialogComponent } from './trading-dialog/trading-dialog.component';
import { MarketDataService, MarketQuote, Security, SecurityPage } from './services/market-data.service';
import { AccountService } from '../services/account.service';

interface MarketAsset {
  securityId: number;
  symbol: string;
  name: string;
  price: number | null;
  change24h: number | null;
  changePercent: string;
  volume: string;
  marketCap: string;
  type: string;
}

@Component({
  selector: 'app-markets',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatInputModule,
    MatFormFieldModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatDialogModule,
  ],
  templateUrl: './markets.component.html',
  styleUrl: './markets.component.scss',
})
export class MarketsComponent implements OnInit {
  searchQuery = '';
  selectedFilter = 'all';

  allMarketAssets: MarketAsset[] = [];
  errorMessage = '';

  marketAssets: MarketAsset[] = [];
  displayedColumns: string[] = ['symbol', 'price', 'change', 'volume', 'marketCap'];

  constructor(
    private dialog: MatDialog,
    private marketData: MarketDataService,
    private accountService: AccountService,
    private changeDetector: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadSecurities();
  }

  private loadSecurities(): void {
    this.errorMessage = '';
    console.log('[Markets] Loading securities', { search: this.searchQuery });
    this.marketData.listSecurities(this.searchQuery).subscribe({
      next: (page: SecurityPage) => {
        console.log('[Markets] Securities loaded', { count: page.content.length, total: page.totalElements });
        this.allMarketAssets = page.content.map((security: Security) => this.toMarketAsset(security));
        this.loadQuotes();
        this.changeDetector.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        console.error('[Markets] Failed to load securities', { search: this.searchQuery, error });
        this.allMarketAssets = [];
        this.marketAssets = [];
        this.errorMessage = 'Market data is currently unavailable.';
        this.changeDetector.markForCheck();
      },
    });
  }

  private loadQuotes(): void {
    this.allMarketAssets.forEach(asset => {
      this.marketData.quote(asset.securityId).subscribe({
        next: (quote: MarketQuote) => {
          console.log('[Markets] Quote loaded', { securityId: asset.securityId, symbol: asset.symbol, quote });
          asset.price = quote.price;
          asset.changePercent = quote.stale ? 'Stale' : quote.marketState;
          this.filterAssets();
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse) => {
          console.error('[Markets] Failed to load quote', { securityId: asset.securityId, symbol: asset.symbol, error });
          asset.price = null;
          asset.changePercent = 'Unavailable';
          this.filterAssets();
          this.changeDetector.markForCheck();
        },
      });
    });
    this.filterAssets();
  }

  private toMarketAsset(security: Security): MarketAsset {
    return {
      securityId: security.securityId,
      symbol: security.ticker,
      name: security.name,
      price: null,
      change24h: null,
      changePercent: 'Loading',
      volume: '--',
      marketCap: '--',
      type: security.assetType.toLowerCase(),
    };
  }

  filterAssets(): void {
    let filtered = this.allMarketAssets;

    // Apply asset type filter
    if (this.selectedFilter !== 'all') {
      filtered = filtered.filter(asset => asset.type === this.selectedFilter);
    }

    // Apply search filter
    if (this.searchQuery.trim()) {
      const query = this.searchQuery.toLowerCase();
      filtered = filtered.filter(asset => asset.symbol.toLowerCase().includes(query));
    }

    this.marketAssets = filtered;
  }

  onSearchChange(): void {
    this.loadSecurities();
  }

  selectFilter(filter: string): void {
    this.selectedFilter = filter;
    this.filterAssets();
  }

  isFilterActive(filter: string): boolean {
    return this.selectedFilter === filter;
  }

  openTradingDialog(asset: MarketAsset): void {
    const accountId = this.accountService.selectedAccount()?.accountId;
    if (!accountId || asset.price === null) {
      console.warn('[Markets] Cannot open trading dialog', {
        accountId,
        securityId: asset.securityId,
        symbol: asset.symbol,
        hasPrice: asset.price !== null,
      });
      this.errorMessage = 'Select an account and wait for a live price before placing an order.';
      return;
    }
    console.log('[Markets] Opening trading dialog', {
      accountId,
      securityId: asset.securityId,
      symbol: asset.symbol,
      price: asset.price,
    });
    this.dialog.open(TradingDialogComponent, {
      width: '500px',
      data: { ...asset, accountId },
      disableClose: false,
    });
  }

  getChangeColor(change: number | null): string {
    return change === null ? '#6c757d' : change >= 0 ? '#51cf66' : '#ff6b6b';
  }
}
