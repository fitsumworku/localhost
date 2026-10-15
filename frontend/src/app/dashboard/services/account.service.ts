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
}
