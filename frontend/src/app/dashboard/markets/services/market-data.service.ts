import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of, tap } from 'rxjs';
import { ValueRange } from '../../services/account.service';

export interface Security {
  securityId: number;
  ticker: string;
  name: string;
  assetType: 'EQUITY' | 'ETF' | 'FOREX' | 'CRYPTO';
  exchange: string;
  quoteCurrency: string;
  baseCurrency: string | null;
  status: string;
}

export interface SecurityPage {
  content: Security[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface MarketQuote {
  symbol: string;
  price: number;
  currency: string;
  asOf: string;
  // Raw provider state; often 'unknown' (stocks) or 'closed' (FX) whatever the session
  marketState: 'open' | 'closed' | 'pre' | 'post' | 'unknown';
  source: string;
  stale: boolean;
  // Whether orders can fill now: the backend trading calendar (US stocks: weekdays 9:30-4:00 ET)
  tradingOpen: boolean;
}

// Raw daily closes, oldest first; the first is the last close before the range (for 1D, the previous close)
export interface SecurityCloses {
  securityId: number;
  ticker: string;
  currency: string;
  range: ValueRange;
  closes: { date: string; close: number }[];
}

@Injectable({ providedIn: 'root' })
export class MarketDataService {
  private readonly apiUrl = '/api/securities';

  // Every quote request costs one unit of the shared daily Fauxnance quota (via the
  // backend), so a quote is reused for a short while instead of fetched again
  private static readonly QUOTE_TTL_MS = 30_000;
  private readonly quoteCache = new Map<number, { quote: MarketQuote; fetchedAt: number }>();

  constructor(private http: HttpClient) {}

  listSecurities(search = ''): Observable<SecurityPage> {
    let params = new HttpParams()
      .set('status', 'ACTIVE')
      .set('page', 0)
      .set('size', 20);

    if (search.trim()) {
      params = params.set('q', search.trim());
    }

    return this.http.get<SecurityPage>(this.apiUrl, { params });
  }

  quote(securityId: number): Observable<MarketQuote> {
    const cached = this.quoteCache.get(securityId);
    if (cached && Date.now() - cached.fetchedAt < MarketDataService.QUOTE_TTL_MS) {
      return of(cached.quote);
    }
    return this.http
      .get<MarketQuote>(`${this.apiUrl}/${securityId}/quote`)
      .pipe(tap((quote) => this.quoteCache.set(securityId, { quote, fetchedAt: Date.now() })));
  }

  // The backend caches a year of closes per symbol for the day, so switching ranges costs no quota
  closes(securityId: number, range: ValueRange): Observable<SecurityCloses> {
    const params = new HttpParams().set('range', range);
    return this.http.get<SecurityCloses>(`${this.apiUrl}/${securityId}/closes`, { params });
  }
}
