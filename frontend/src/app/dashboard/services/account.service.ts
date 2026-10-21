import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AccountBalance {
  cashBalance: number;
  reservedCash: number;
  availableCash: number;
}

export interface AccountPosition {
  positionId: number;
  accountId: number;
  securityId: number;
  ticker: string;
  name: string;
  assetType: string;
  exchange: string;
  quoteCurrency: string;
  accountCurrency: string;
  quantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  averagePrice: number;
  updatedDate: string;
}

export interface Account {
  accountId: number;
  userId: number;
  currency: string;
  createdDate: string;
  updatedDate: string;
  balance: AccountBalance;
}

export type ValueRange = '1D' | '1W' | '1M' | '1Y';

// End-of-day account value (cash + holdings at that day's close), oldest first, up to yesterday
export interface ValueHistory {
  accountId: number;
  range: ValueRange;
  points: { date: string; value: number }[];
  complete: boolean; // false when a holding had no price history and used its last trade price
}

export interface AccountPage {
  content: Account[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly apiUrl = '/api/accounts';
  readonly accounts = signal<Account[]>([]);
  readonly selectedAccount = signal<Account | null>(null);

  constructor(private http: HttpClient) {}

  loadAccounts(): Observable<AccountPage> {
    const params = new HttpParams().set('page', 0).set('size', 20);
    return this.http.get<AccountPage>(this.apiUrl, { params });
  }

  setAccounts(accounts: Account[]): void {
    this.accounts.set(accounts);
    const selectedId = this.selectedAccount()?.accountId;
    this.selectedAccount.set(accounts.find(account => account.accountId === selectedId) ?? accounts[0] ?? null);
  }

  selectAccount(account: Account): void {
    this.selectedAccount.set(account);
  }

  createAccount(): Observable<Account> {
    return this.http.post<Account>(this.apiUrl, {});
  }

  balance(accountId: number): Observable<AccountBalance> {
    return this.http.get<AccountBalance>(`${this.apiUrl}/${accountId}/balance`);
  }

  positions(accountId: number): Observable<{ content: AccountPosition[] }> {
    const params = new HttpParams().set('page', 0).set('size', 100);
    return this.http.get<{ content: AccountPosition[] }>(`${this.apiUrl}/${accountId}/positions`, { params });
  }

  valueHistory(accountId: number, range: ValueRange): Observable<ValueHistory> {
    const params = new HttpParams().set('range', range);
    return this.http.get<ValueHistory>(`${this.apiUrl}/${accountId}/value-history`, { params });
  }
}
