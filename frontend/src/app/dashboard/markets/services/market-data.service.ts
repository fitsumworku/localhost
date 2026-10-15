import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

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
  marketState: 'open' | 'closed' | 'pre' | 'post' | 'unknown';
  source: string;
  stale: boolean;
}

@Injectable({ providedIn: 'root' })
export class MarketDataService {
  private readonly apiUrl = '/api/securities';

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
    return this.http.get<MarketQuote>(`${this.apiUrl}/${securityId}/quote`);
  }
}
