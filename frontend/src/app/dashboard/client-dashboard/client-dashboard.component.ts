import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';

export type TimePeriod = '1D' | '1W' | '1M' | '1Y';

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
export class ClientDashboardComponent implements OnInit {
  selectedPeriod: TimePeriod = '1D';
  timePeriods: TimePeriod[] = ['1D', '1W', '1M', '1Y'];

  // Stat cards data
//   portfolioValue = '$142,502.80';
//   portfolioChange = '+4.28%';
//   cashBalance = '$12,340.50';
//   buyingPower = '$24,681.00';
//   totalReturn = '+$18,940.10';
//   totalReturnPercent = '+15.3%';

  // Holdings table
  displayedColumns: string[] = ['asset', 'shares', 'avgCost', 'currentPrice', 'totalValue', 'dayChange'];
  holdingsData: any[] = [];

  // Recent activity table
  activityColumns: string[] = ['symbol', 'action', 'price', 'time'];
  activityData: any[] = [];

  ngOnInit(): void {
    // Initialize with empty data
    this.holdingsData = [];
    this.activityData = [];
  }

  selectPeriod(period: TimePeriod): void {
    this.selectedPeriod = period;
    // TODO: Fetch performance data for selected period
  }
}
