import { ChangeDetectorRef, Component, DestroyRef, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { EMPTY, Subject, merge, timer } from 'rxjs';
import { catchError, debounceTime, switchMap } from 'rxjs/operators';
import { AdminService, AdminUser, UserStatus } from '../services/admin.service';

// Picks up status changes made by other admins.
const POLL_MS = 10_000;

@Component({
  selector: 'app-compliance',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
  ],
  templateUrl: './compliance.component.html',
  styleUrl: './compliance.component.scss',
})
export class ComplianceComponent implements OnInit {
  searchQuery = '';
  selectedStatus: UserStatus | null = null;

  clients: AdminUser[] = [];
  totalElements = 0;
  isLoading = true;
  errorMessage = '';

  // Blacklisting signs the client out everywhere, so it takes a second click.
  confirmingId: number | null = null;
  updatingIds = new Set<number>();

  statusOptions: { value: UserStatus | null; label: string }[] = [
    { value: null, label: 'All' },
    { value: 'ACTIVE', label: 'Active' },
    { value: 'SUSPENDED', label: 'Blacklisted' },
  ];

  private readonly search$ = new Subject<void>();
  private readonly reload$ = new Subject<void>();

  constructor(
    private admin: AdminService,
    private changeDetector: ChangeDetectorRef,
    private destroyRef: DestroyRef,
  ) {}

  ngOnInit(): void {
    merge(this.search$.pipe(debounceTime(200)), this.reload$)
      .pipe(
        switchMap(() => timer(0, POLL_MS)),
        switchMap(() => this.admin.clients(this.selectedStatus, this.searchQuery).pipe(
          catchError(error => {
            console.error('[Compliance] Failed to load clients', error);
            this.clients = [];
            this.errorMessage = 'Clients are currently unavailable.';
            this.isLoading = false;
            this.changeDetector.markForCheck();
            return EMPTY;
          }),
        )),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(page => {
        // Rows mid-update keep their optimistic status until the PATCH settles.
        this.clients = page.content.map(client => this.updatingIds.has(client.userId)
          ? this.clients.find(c => c.userId === client.userId) ?? client
          : client);
        this.totalElements = page.totalElements;
        this.errorMessage = '';
        this.isLoading = false;
        this.changeDetector.markForCheck();
      });
    this.reload$.next();
  }

  onSearchChange(): void {
    this.confirmingId = null;
    this.search$.next();
  }

  selectStatus(status: UserStatus | null): void {
    this.selectedStatus = status;
    this.confirmingId = null;
    this.reload$.next();
  }

  onStatusAction(client: AdminUser): void {
    if (client.status === 'ACTIVE' && this.confirmingId !== client.userId) {
      this.confirmingId = client.userId;
      return;
    }
    this.confirmingId = null;
    this.setStatus(client, client.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE');
  }

  cancelConfirm(): void {
    this.confirmingId = null;
  }

  private setStatus(client: AdminUser, status: UserStatus): void {
    const previous = client.status;
    this.updatingIds.add(client.userId);
    client.status = status;

    this.admin.setUserStatus(client.userId, status).subscribe({
      next: updated => {
        this.updatingIds.delete(client.userId);
        Object.assign(client, updated);
        // Drop the row if it no longer matches the active filter.
        if (this.selectedStatus && updated.status !== this.selectedStatus) {
          this.clients = this.clients.filter(c => c.userId !== client.userId);
          this.totalElements = Math.max(0, this.totalElements - 1);
        }
        this.changeDetector.markForCheck();
      },
      error: error => {
        console.error('[Compliance] Failed to update client status', { userId: client.userId, error });
        this.updatingIds.delete(client.userId);
        client.status = previous;
        this.errorMessage = `Could not update ${client.name}. Please try again.`;
        this.changeDetector.markForCheck();
      },
    });
  }

  isUpdating(client: AdminUser): boolean {
    return this.updatingIds.has(client.userId);
  }
}
