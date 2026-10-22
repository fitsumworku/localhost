import { ChangeDetectorRef, Component, DestroyRef, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { BehaviorSubject, EMPTY, combineLatest, forkJoin, timer } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import {
  AdminService,
  ReportRange,
  ReportSummary,
  SecurityVolume,
  TradeSide,
  UserVolume,
} from '../services/admin.service';

// The ETL refreshes the OLAP snapshot every 60s by default.
const REFRESH_MS = 60_000;
const TOP_CLIENTS = 10;

type RangePreset = '7d' | '30d' | '90d' | 'all' | 'custom';

@Component({
  selector: 'app-admin-dashboard',
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.scss',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule],
})
export class AdminDashboardComponent implements OnInit {
  summary: ReportSummary | null = null;
  topClients: UserVolume[] = [];
  instruments: SecurityVolume[] = [];
  snapshotAt: string | null = null;

  isLoading = true;
  errorMessage = '';

  // ---------- Top clients filters ----------
  readonly sideOptions: { value: TradeSide | null; label: string }[] = [
    { value: 'BUY', label: 'Bought' },
    { value: 'SELL', label: 'Sold' },
    { value: null, label: 'Total' },
  ];
  readonly rangeOptions: { value: RangePreset; label: string }[] = [
    { value: '7d', label: '7 days' },
    { value: '30d', label: '30 days' },
    { value: '90d', label: '90 days' },
    { value: 'all', label: 'All time' },
    { value: 'custom', label: 'Custom' },
  ];
  side: TradeSide | null = 'BUY';
  rangePreset: RangePreset = '30d';
  customFrom = '';
  customTo = '';
  readonly today = toDateInput(new Date());

  isLoadingClients = true;
  clientsError = '';

  private readonly clientFilters$ = new BehaviorSubject<void>(undefined);

  constructor(
    private admin: AdminService,
    private changeDetector: ChangeDetectorRef,
    private destroyRef: DestroyRef,
  ) {}

  ngOnInit(): void {
    timer(0, REFRESH_MS)
      .pipe(
        // A failed refresh keeps the last data on screen and tries again next tick.
        switchMap(() => forkJoin({
          summary: this.admin.summary(),
          securities: this.admin.topSecurities(10),
        }).pipe(
          catchError((error: HttpErrorResponse) => {
            console.error('[ClientMonitor] Failed to load reports', error);
            this.errorMessage = error.error?.code === 'REPORTING_NOT_INITIALIZED'
              ? 'Reporting data has not been loaded yet. It refreshes every minute.'
              : 'Reporting data is currently unavailable.';
            this.isLoading = false;
            this.changeDetector.markForCheck();
            return EMPTY;
          }),
        )),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ summary, securities }) => {
        this.summary = summary.data;
        this.instruments = securities.data;
        this.snapshotAt = summary.freshness.sourceSnapshotAt;
        this.errorMessage = '';
        this.isLoading = false;
        this.changeDetector.markForCheck();
      });

    // Top clients reload when a filter changes, and with each snapshot refresh.
    combineLatest([this.clientFilters$, timer(0, REFRESH_MS)])
      .pipe(
        switchMap(() => {
          const range = this.selectedRange();
          if (!range) {
            this.isLoadingClients = false;
            this.changeDetector.markForCheck();
            return EMPTY;
          }
          return this.admin.topClients(TOP_CLIENTS, this.side, range).pipe(
            catchError((error: HttpErrorResponse) => {
              console.error('[ClientMonitor] Failed to load top clients', error);
              this.topClients = [];
              this.clientsError = 'Top clients are currently unavailable.';
              this.isLoadingClients = false;
              this.changeDetector.markForCheck();
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(report => {
        this.topClients = report.data;
        this.clientsError = '';
        this.isLoadingClients = false;
        this.changeDetector.markForCheck();
      });
  }

  selectSide(side: TradeSide | null): void {
    this.side = side;
    this.reloadClients();
  }

  selectRange(preset: RangePreset): void {
    this.rangePreset = preset;
    if (preset === 'custom' && !this.customFrom) {
      // Start the custom range from the last 30 days so it is never empty.
      this.customTo = this.today;
      this.customFrom = toDateInput(addDays(new Date(), -29));
    }
    this.reloadClients();
  }

  onCustomRangeChange(): void {
    this.reloadClients();
  }

  get customRangeInvalid(): boolean {
    return this.rangePreset === 'custom' && !!this.customFrom && !!this.customTo && this.customFrom > this.customTo;
  }

  /** e.g. "bought between Oct 1 and Oct 22". */
  get rangeCaption(): string {
    const verb = this.side === 'BUY' ? 'Ranked by amount bought' : this.side === 'SELL' ? 'Ranked by amount sold' : 'Ranked by total traded';
    if (this.rangePreset === 'all') return `${verb}, all time`;
    if (this.rangePreset === 'custom') {
      if (!this.customFrom || !this.customTo || this.customRangeInvalid) return verb;
      return `${verb}, ${formatDay(fromDateInput(this.customFrom))} – ${formatDay(fromDateInput(this.customTo))}`;
    }
    return `${verb} in the last ${this.rangePreset.replace('d', '')} days`;
  }

  /** Bar width for a client, relative to the busiest client. */
  clientBarWidth(client: UserVolume): number {
    const max = this.topClients[0]?.volume.grossVolumeUsd ?? 0;
    return max > 0 ? (client.volume.grossVolumeUsd / max) * 100 : 0;
  }

  clientTradeCount(client: UserVolume): number {
    return this.side === 'BUY' ? client.volume.buyTradeCount
      : this.side === 'SELL' ? client.volume.sellTradeCount
      : client.volume.tradeCount;
  }

  /** Share of total gross volume, as a fraction for the percent pipe. */
  share(instrument: SecurityVolume): number {
    const total = this.summary?.volume.grossVolumeUsd ?? 0;
    return total > 0 ? instrument.volume.grossVolumeUsd / total : 0;
  }

  private reloadClients(): void {
    this.isLoadingClients = true;
    this.clientFilters$.next();
  }

  /** Whole local days: from midnight of the first day up to midnight after the last. Null while a custom range is incomplete. */
  private selectedRange(): ReportRange | null {
    const tomorrow = addDays(startOfDay(new Date()), 1);
    switch (this.rangePreset) {
      case 'all':
        return {};
      case 'custom': {
        if (!this.customFrom || !this.customTo || this.customRangeInvalid) return null;
        return {
          from: fromDateInput(this.customFrom).toISOString(),
          to: addDays(fromDateInput(this.customTo), 1).toISOString(),
        };
      }
      default: {
        const days = Number(this.rangePreset.replace('d', ''));
        return { from: addDays(tomorrow, -days).toISOString(), to: tomorrow.toISOString() };
      }
    }
  }
}

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

/** yyyy-mm-dd in local time, the value format of <input type="date">. */
function toDateInput(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function fromDateInput(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}

function formatDay(date: Date): string {
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}
