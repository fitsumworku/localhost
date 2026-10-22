import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-admin-dashboard',
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.scss',
  standalone: true,
  imports: [CommonModule]
})
export class AdminDashboardComponent implements OnInit {
  // Stat cards data
//   totalTradingVolume = '$2.48B';
//   volumeChange = '+18.6%';
//   activeClients = '12,842';
//   activeClientsChange = '+624 net active';
//   tradesExecuted = '486,291';
//   tradesChange = '+32.3% vs prior period';

  // Trading volume data
  displayedColumns: string[] = ['date', 'volume'];
  tradingVolumeData: any[] = [];

  // Most active instruments
  instrumentsColumns: string[] = ['instrument', 'market', 'volume', 'trades', 'share'];
  instrumentsData: any[] = [];

  ngOnInit(): void {
    // Initialize with empty data
    this.tradingVolumeData = [];
    this.instrumentsData = [];
  }
}
