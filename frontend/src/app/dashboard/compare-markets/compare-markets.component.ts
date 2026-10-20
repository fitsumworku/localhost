import { ChangeDetectorRef, Component, DestroyRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject, Subscription, catchError, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { MarketDataService, MarketQuote, Security, SecurityCloses } from '../markets/services/market-data.service';
import { AccountService, ValueRange } from '../services/account.service';
import { ValueChartComponent } from '../client-dashboard/value-chart/value-chart.component';
import { TradingDialogComponent } from '../markets/trading-dialog/trading-dialog.component';
import { CompareSelectionService } from './compare-selection.service';
import { ValuePoint } from '../client-dashboard/portfolio-tracker.service';

// One side of the comparison: its own search box until a market is picked
interface Slot {
  query: string;
  results: Security[];
  showResults: boolean;
  search$: Subject<string>;
  security: Security | null;
  quote: MarketQuote | null;
  quoteFailed: boolean;
  closes: SecurityCloses | null;
  closesFailed: boolean;
  loadingCloses: boolean;
  points: ValuePoint[];
  stats: SlotStats | null;
  requests: Subscription | null;
  tradeMessage: string;
}

interface SlotStats {
  price: number;
  change: number;
  changePercent: number | null;
  previousClose: number | null;
  high: number;
  low: number;
}

// A close is drawn at 4 PM local time on its date
function closeTime(date: string): number {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(year, month - 1, day, 16).getTime();
}

@Component({
  selector: 'app-compare-markets',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, MatDialogModule, ValueChartComponent],
  templateUrl: './compare-markets.component.html',
  styleUrl: './compare-markets.component.scss',
})
export class CompareMarketsComponent {
  readonly ranges: { value: ValueRange; caption: string }[] = [
    { value: '1D', caption: 'since the previous close' },
    { value: '1W', caption: 'over the past week' },
    { value: '1M', caption: 'over the past month' },
    { value: '1Y', caption: 'over the past year' },
  ];
  selectedRange: ValueRange = '1M';
  readonly slots: Slot[];

  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private marketData: MarketDataService,
    private accountService: AccountService,
    private selection: CompareSelectionService,
    private dialog: MatDialog,
    private changeDetector: ChangeDetectorRef,
  ) {
    this.slots = [this.createSlot(), this.createSlot()];
    // Bring back the comparison from the last visit
    const saved = this.selection.load();
    this.selectedRange = saved.range;
    saved.securities.forEach((security, i) => {
      if (security) {
        this.slots[i].security = security;
        this.load(this.slots[i]);
      }
    });
  }

  get rangeCaption(): string {
    return this.ranges.find((range) => range.value === this.selectedRange)?.caption ?? '';
  }

  // "AAPL is up 4.20% over the past month, 1.10% more than MSFT"
  get summary(): string {
    const [a, b] = this.slots;
    if (!a.security || !b.security || a.stats?.changePercent == null || b.stats?.changePercent == null) return '';
    const describe = (slot: Slot) =>
      `${slot.security!.ticker} is ${slot.stats!.changePercent! >= 0 ? 'up' : 'down'} ${Math.abs(slot.stats!.changePercent!).toFixed(2)}%`;
    const gap = Math.abs(a.stats.changePercent - b.stats.changePercent).toFixed(2);
    const leader = a.stats.changePercent >= b.stats.changePercent ? a : b;
    return `${describe(a)} and ${describe(b)} ${this.rangeCaption}. ${leader.security!.ticker} did ${gap} percentage points better.`;
  }

  selectRange(range: ValueRange): void {
    if (range === this.selectedRange) return;
    this.selectedRange = range;
    this.saveSelection();
    this.slots.filter((slot) => slot.security).forEach((slot) => this.load(slot));
  }

  onSearchChange(slot: Slot): void {
    slot.showResults = slot.query.trim().length > 0;
    slot.search$.next(slot.query.trim());
  }

  // Hide the dropdown after a click on a result has had a chance to land
  closeResults(slot: Slot): void {
    setTimeout(() => {
      slot.showResults = false;
      this.changeDetector.markForCheck();
    }, 150);
  }

  select(slot: Slot, security: Security): void {
    slot.security = security;
    slot.query = '';
    slot.results = [];
    slot.showResults = false;
    this.saveSelection();
    this.load(slot);
  }

  remove(slot: Slot): void {
    slot.requests?.unsubscribe();
    Object.assign(slot, {
      security: null, quote: null, quoteFailed: false, closes: null, closesFailed: false,
      loadingCloses: false, points: [], stats: null, requests: null,
    });
    slot.tradeMessage = '';
    this.saveSelection();
  }

  // Same trading dialog as the Markets page; it opens on Buy
  buy(slot: Slot): void {
    const security = slot.security;
    if (!security) return;
    const accountId = this.accountService.selectedAccount()?.accountId;
    if (!accountId) {
      slot.tradeMessage = 'Create or select an account first (top right).';
      return;
    }
    if (!slot.quote) {
      slot.tradeMessage = `Waiting for a live price for ${security.ticker}. Try again in a moment.`;
      return;
    }
    slot.tradeMessage = '';
    this.dialog.open(TradingDialogComponent, {
      width: '440px',
      maxWidth: '92vw',
      data: {
        accountId,
        securityId: security.securityId,
        symbol: security.ticker,
        name: security.name,
        price: slot.quote.price,
        currency: slot.quote.currency || security.quoteCurrency,
        isOpen: this.isOpen(slot),
      },
      panelClass: ['dark-ui', 'app-dialog'],
    });
  }

  private saveSelection(): void {
    this.selection.save({
      securities: [this.slots[0].security, this.slots[1].security],
      range: this.selectedRange,
    });
  }

  // The other side's market can't be picked twice
  resultsFor(slot: Slot): Security[] {
    const taken = this.slots.find((other) => other !== slot)?.security?.securityId;
    return slot.results.filter((security) => security.securityId !== taken);
  }

  typeLabel(type: string): string {
    return { EQUITY: 'Stock', ETF: 'ETF', CRYPTO: 'Crypto', FOREX: 'FX' }[type] ?? type;
  }

  // Small prices (FX rates, cheap coins) need more than 2 decimals
  digits(value: number): string {
    return Math.abs(value) < 10 ? '1.2-4' : '1.2-2';
  }

  isOpen(slot: Slot): boolean {
    return slot.security?.assetType === 'CRYPTO' || slot.quote?.marketState === 'open';
  }

  private createSlot(): Slot {
    const slot: Slot = {
      query: '', results: [], showResults: false, search$: new Subject<string>(),
      security: null, quote: null, quoteFailed: false, closes: null, closesFailed: false,
      loadingCloses: false, points: [], stats: null, requests: null, tradeMessage: '',
    };
    // Searching waits until typing pauses, and a newer search replaces an older one
    slot.search$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((query): Observable<Security[]> =>
          query
            ? this.marketData.listSecurities(query).pipe(
                map((page) => page.content),
                catchError((error: unknown) => {
                  console.error('[CompareMarkets] Search failed', { query, error });
                  return of([] as Security[]);
                }),
              )
            : of([] as Security[]),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((results) => {
        slot.results = results;
        this.changeDetector.markForCheck();
      });
    return slot;
  }

  // Live quote and daily closes load separately, so either can show while the other fails
  private load(slot: Slot): void {
    const security = slot.security!;
    slot.requests?.unsubscribe();
    slot.requests = new Subscription();
    slot.loadingCloses = true;
    slot.closesFailed = false;
    slot.closes = null;
    this.update(slot);

    slot.requests.add(this.marketData.quote(security.securityId).subscribe({
      next: (quote: MarketQuote) => {
        slot.quote = quote;
        slot.quoteFailed = false;
        this.update(slot);
      },
      error: (error: unknown) => {
        console.error('[CompareMarkets] Failed to load quote', { securityId: security.securityId, error });
        slot.quote = null;
        slot.quoteFailed = true;
        this.update(slot);
      },
    }));

    // 1D needs the close before the live price's trading day, which can be several days back (weekends), so ask for a week
    const closesRange = this.selectedRange === '1D' ? '1W' : this.selectedRange;
    slot.requests.add(this.marketData.closes(security.securityId, closesRange).subscribe({
      next: (closes: SecurityCloses) => {
        slot.closes = closes;
        slot.loadingCloses = false;
        this.update(slot);
      },
      error: (error: unknown) => {
        console.error('[CompareMarkets] Failed to load closes', { securityId: security.securityId, error });
        slot.closesFailed = true;
        slot.loadingCloses = false;
        this.update(slot);
      },
    }));
  }

  // Chart: the closes, then the live price. Change is measured from the first close (the baseline).
  private update(slot: Slot): void {
    let closes = slot.closes?.closes ?? [];
    // The live price belongs to its own trading day (UTC date of asOf), so it replaces that day's close and any later
    // one. Without this, after midnight UTC a close dated "tomorrow" is drawn after the live price.
    const liveDay = slot.quote?.asOf.slice(0, 10) ?? null;
    if (liveDay) closes = closes.filter((close) => close.date < liveDay);
    // 1D: just the previous close before the live price, or the last two closes when there is no live price
    if (this.selectedRange === '1D') closes = closes.slice(slot.quote ? -1 : -2);

    slot.points = closes.map((close) => ({ time: closeTime(close.date), value: close.close, daily: true }));
    if (slot.quote) slot.points.push({ time: Math.min(Date.parse(slot.quote.asOf) || Date.now(), Date.now()), value: slot.quote.price });

    if (slot.points.length === 0) {
      slot.stats = null;
    } else {
      const values = slot.points.map((point) => point.value);
      const price = values[values.length - 1];
      const baseline = closes.length && values.length > 1 ? values[0] : null;
      const previousDay = liveDay ?? new Date().toISOString().slice(0, 10);
      const previous = [...(slot.closes?.closes ?? [])].reverse().find((close) => close.date < previousDay);
      // The baseline sits before the range, so it only counts toward the high and low when nothing else does
      const inRange = baseline !== null ? values.slice(1) : values;
      const change = baseline === null ? 0 : price - baseline;
      // Anything that rounds to zero at the displayed precision is "no change", not a red loss
      const flat = Math.abs(change) < (Math.abs(price) < 10 ? 0.00005 : 0.005);
      slot.stats = {
        price,
        change: flat ? 0 : change,
        changePercent: baseline ? ((flat ? 0 : change) / baseline) * 100 : null,
        previousClose: previous?.close ?? null,
        high: Math.max(...inRange),
        low: Math.min(...inRange),
      };
    }
    this.changeDetector.markForCheck();
  }
}
