import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { DisputeTransactionDialogComponent } from './dispute-transaction-dialog/dispute-transaction-dialog.component';

interface Transaction {
  date: string;
  asset: string;
  type: 'buy' | 'sell';
  amount: number;
  price: number;
  pnl: number;
}

@Component({
  selector: 'app-transaction-history',
  templateUrl: './transaction-history.component.html',
  styleUrl: './transaction-history.component.scss',
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
export class TransactionHistoryComponent implements OnInit {
  // Stat cards data
//   portfolioValue = '$142,502.80';
//   portfolioChange = '+4.28% today';
//   cashBalance = '$12,340.50';
//   cashStatus = 'settled';
//   buyingPower = '$24,681.00';
//   buyingStatus = '2.00x leverage margin';
//   totalReturn = '+$18,940.10';
//   totalReturnPercent = '+15.3% all-time';

  // Transaction History Table
  displayedColumns: string[] = ['date', 'asset', 'type', 'amount', 'price', 'pnl', 'actions'];
  transactionData: Transaction[] = [];

  constructor(private dialog: MatDialog) {}

  ngOnInit(): void {
    // Initialize with empty data
    this.transactionData = [];
  }

  openDisputeDialog(transaction: Transaction): void {
    this.dialog.open(DisputeTransactionDialogComponent, {
      width: '500px',
      data: transaction,
      disableClose: false,
    });
  }

  getTypeColor(type: 'buy' | 'sell'): string {
    return type === 'buy' ? '#51cf66' : '#ff6b6b';
  }

  getPnlColor(pnl: number): string {
    return pnl >= 0 ? '#51cf66' : '#ff6b6b';
  }
}
