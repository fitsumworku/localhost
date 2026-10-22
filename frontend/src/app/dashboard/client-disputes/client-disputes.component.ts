import { ChangeDetectorRef, Component, DestroyRef, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { EMPTY, Subject, merge, timer } from 'rxjs';
import { catchError, debounceTime, switchMap } from 'rxjs/operators';
import { DisputeDetailDialogComponent } from './dispute-detail-dialog/dispute-detail-dialog.component';
import { AdminDispute, AdminService, DisputeStatus } from '../services/admin.service';
import { WORKFLOW_STATUSES, disputeStatusLabel, disputeStatusTone } from '../services/dispute-status';

// Keeps the queue current while other admins work disputes.
const POLL_MS = 15_000;

@Component({
  selector: 'app-client-disputes',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    MatDialogModule,
  ],
  templateUrl: './client-disputes.component.html',
  styleUrl: './client-disputes.component.scss',
})
export class ClientDisputesComponent implements OnInit {
  searchQuery = '';
  selectedStatus: DisputeStatus | null = null;

  disputes: AdminDispute[] = [];
  totalElements = 0;
  isLoading = true;
  errorMessage = '';

  statusOptions: { value: DisputeStatus | null; label: string }[] = [
    { value: null, label: 'All' },
    ...WORKFLOW_STATUSES,
  ];

  readonly statusLabel = disputeStatusLabel;
  readonly statusTone = disputeStatusTone;

  // Typing is debounced; filter clicks and saves reload immediately.
  private readonly search$ = new Subject<void>();
  private readonly reload$ = new Subject<void>();

  constructor(
    private dialog: MatDialog,
    private admin: AdminService,
    private changeDetector: ChangeDetectorRef,
    private destroyRef: DestroyRef,
  ) {}

  ngOnInit(): void {
    merge(this.search$.pipe(debounceTime(250)), this.reload$)
      .pipe(
        switchMap(() => timer(0, POLL_MS)),
        // A failed poll shows an error but keeps polling.
        switchMap(() => this.admin.disputes(this.selectedStatus, this.searchQuery).pipe(
          catchError(error => {
            console.error('[Disputes] Failed to load disputes', error);
            this.disputes = [];
            this.errorMessage = 'Disputes are currently unavailable.';
            this.isLoading = false;
            this.changeDetector.markForCheck();
            return EMPTY;
          }),
        )),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(page => {
        this.disputes = page.content;
        this.totalElements = page.totalElements;
        this.errorMessage = '';
        this.isLoading = false;
        this.changeDetector.markForCheck();
      });
    this.reload$.next();
  }

  onSearchChange(): void {
    this.search$.next();
  }

  selectStatus(status: DisputeStatus | null): void {
    this.selectedStatus = status;
    this.reload$.next();
  }

  openDisputeDetail(dispute: AdminDispute): void {
    this.dialog.open(DisputeDetailDialogComponent, {
      width: '600px',
      maxWidth: '95vw',
      data: dispute,
      panelClass: ['dark-ui', 'app-dialog'],
    }).afterClosed().subscribe((changed: boolean | undefined) => {
      if (changed) this.reload$.next();
    });
  }
}
