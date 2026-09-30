import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { TradingDialogComponent } from './trading-dialog/trading-dialog.component';

interface TrendingAsset {
  symbol: string;
  price: number;
  change: number;
  changePercent: string;
}

interface MarketAsset {
  symbol: string;
  price: number;
  change24h: number;
  changePercent: string;
  volume: string;
  marketCap: string;
  type: 'equity' | 'crypto' | 'etf';
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

  trendingAssets: TrendingAsset[] = [
    { symbol: '', price: 0, change: 0, changePercent: '' },
    { symbol: '', price: 0, change: 0, changePercent: '' },
    { symbol: '', price: 0, change: 0, changePercent: '' },
  ];

  allMarketAssets: MarketAsset[] = [];

  marketAssets: MarketAsset[] = [];
  displayedColumns: string[] = ['symbol', 'price', 'change', 'volume', 'marketCap'];

  constructor(private dialog: MatDialog) {}

  ngOnInit(): void {
    this.filterAssets();
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
    this.filterAssets();
  }

  selectFilter(filter: string): void {
    this.selectedFilter = filter;
    this.filterAssets();
  }

  isFilterActive(filter: string): boolean {
    return this.selectedFilter === filter;
  }

  openTradingDialog(asset: MarketAsset): void {
    this.dialog.open(TradingDialogComponent, {
      width: '500px',
      data: asset,
      disableClose: false,
    });
  }

  getChangeColor(change: number): string {
    return change >= 0 ? '#51cf66' : '#ff6b6b';
  }
}
