import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatAutocompleteModule } from '@angular/material/autocomplete';

export type TimePeriod = '1D' | '1W' | '1M' | '1Y';

interface Asset {
  symbol: string;
  name: string;
  price: number;
  change24h: number;
  changePercent: string;
  volume: string;
  marketCap: string;
}

@Component({
  selector: 'app-compare-markets',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatInputModule,
    MatFormFieldModule,
    MatButtonModule,
    MatIconModule,
    MatAutocompleteModule,
  ],
  templateUrl: './compare-markets.component.html',
  styleUrl: './compare-markets.component.scss',
})
export class CompareMarketsComponent implements OnInit {
  selectedPeriod: TimePeriod = '1D';
  timePeriods: TimePeriod[] = ['1D', '1W', '1M', '1Y'];

  selectedAssets: (Asset | null)[] = [null, null];
  searchQuery = '';
  showSearchResults = false;

  // Available assets for search
  availableAssets: Asset[] = [];

  ngOnInit(): void {
    // Initialize with empty assets (to be populated from API later)
  }

  onSearchChange(): void {
    this.showSearchResults = this.searchQuery.trim().length > 0;
  }

  selectAsset(asset: Asset, position: 0 | 1): void {
    // Check if asset is already selected in the other position
    const otherPosition = position === 0 ? 1 : 0;
    if (this.selectedAssets[otherPosition]?.symbol === asset.symbol) {
      return; // Can't select the same asset twice
    }

    this.selectedAssets[position] = asset;
    this.searchQuery = '';
    this.showSearchResults = false;
  }

  removeAsset(position: 0 | 1): void {
    this.selectedAssets[position] = null;
  }

  selectPeriod(period: TimePeriod): void {
    this.selectedPeriod = period;
    // TODO: Fetch chart data for selected period
  }

  getChangeColor(change: number): string {
    return change >= 0 ? '#51cf66' : '#ff6b6b';
  }

  getFilteredAssets(): Asset[] {
    if (!this.searchQuery.trim()) {
      return [];
    }

    const query = this.searchQuery.toLowerCase();
    return this.availableAssets.filter(
      asset =>
        (asset.symbol.toLowerCase().includes(query) ||
          asset.name.toLowerCase().includes(query)) &&
        asset.symbol !== this.selectedAssets[0]?.symbol &&
        asset.symbol !== this.selectedAssets[1]?.symbol
    );
  }
}
