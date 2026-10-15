import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { DisputeDetailDialogComponent } from './dispute-detail-dialog/dispute-detail-dialog.component';

type DisputeStatus = 'open' | 'in-review' | 'resolved';

interface Dispute {
  id: string;
  date: string;
  clientId: string;
  reason: string;
  disputedAmount: number;
  status: DisputeStatus;
  assignedOwner: string;
}

@Component({
  selector: 'app-client-disputes',
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
  templateUrl: './client-disputes.component.html',
  styleUrl: './client-disputes.component.scss',
})
export class ClientDisputesComponent implements OnInit {
  searchQuery = '';
  selectedStatusFilter = 'all';

  // Dispute data
  allDisputes: Dispute[] = [];
  displayedDisputes: Dispute[] = [];

  displayedColumns: string[] = ['date', 'clientId', 'reason', 'disputedAmount', 'status', 'assignedOwner'];

  statusOptions = [
    { value: 'all', label: 'All' },
    { value: 'open', label: 'Open' },
    { value: 'in-review', label: 'In Review' },
    { value: 'resolved', label: 'Resolved' },
  ];

  constructor(private dialog: MatDialog) {}

  ngOnInit(): void {
    this.filterDisputes();
  }

  filterDisputes(): void {
    let filtered = this.allDisputes;

    // Apply status filter
    if (this.selectedStatusFilter !== 'all') {
      filtered = filtered.filter(dispute => dispute.status === this.selectedStatusFilter);
    }

    // Apply search filter
    if (this.searchQuery.trim()) {
      const query = this.searchQuery.toLowerCase();
      filtered = filtered.filter(
        dispute =>
          dispute.clientId.toLowerCase().includes(query) ||
          dispute.reason.toLowerCase().includes(query)
      );
    }

    this.displayedDisputes = filtered;
  }

  onSearchChange(): void {
    this.filterDisputes();
  }

  selectStatusFilter(status: string): void {
    this.selectedStatusFilter = status;
    this.filterDisputes();
  }

  isStatusFilterActive(status: string): boolean {
    return this.selectedStatusFilter === status;
  }

  openDisputeDetail(dispute: Dispute): void {
    this.dialog.open(DisputeDetailDialogComponent, {
      width: '600px',
      data: dispute,
      disableClose: false,
    });
  }

  getStatusColor(status: DisputeStatus): string {
    switch (status) {
      case 'open':
        return '#ff9800';
      case 'in-review':
        return '#2196f3';
      case 'resolved':
        return '#51cf66';
      default:
        return '#999';
    }
  }

  getStatusLabel(status: DisputeStatus): string {
    switch (status) {
      case 'open':
        return 'Open';
      case 'in-review':
        return 'In Review';
      case 'resolved':
        return 'Resolved';
      default:
        return status;
    }
  }
}
