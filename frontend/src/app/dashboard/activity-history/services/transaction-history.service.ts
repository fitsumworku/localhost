import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CashTransaction {
  transactionId: number;
  accountId: number;
  amount: number;
  type: 'DEPOSIT' | 'WITHDRAWAL';
  status: 'PENDING' | 'COMPLETED' | 'FAILED';
  transactionDate: string;
  completedAt: string | null;
  failureReason: string | null;
}

export interface AccountOrder {
  orderId: number;
  accountId: number;
  securityId: number;
  ticker: string;
  name: string;
  side: 'B' | 'S';
  requestedAmount: number | null;
  quantityOrdered: number | null;
  orderStatus: string;
  createdDate: string;
  rejectionReason: string | null;
}

export interface AccountTrade {
  tradeId: number;
  accountId: number;
  orderId: number;
  ticker: string;
  name: string;
  side: 'B' | 'S';
  quantityFilled: number;
  priceOfExecution: number;
  cashAmount: number;
  tradeDate: string;
}

export interface AccountDispute {
  disputeId: number;
  accountId: number;
  adminId: number | null;
  transactionId: number | null;
  tradeId: number | null;
  disputeType: string;
  description: string;
  status: string;
  dateCreated: string;
  dateResolved: string | null;
}

export interface CreateDisputeRequest {
  disputeType: string;
  description: string;
}

@Injectable({ providedIn: 'root' })
export class TransactionHistoryService {
  private readonly apiUrl = '/api/accounts';

  constructor(private http: HttpClient) {}

  cashTransactions(accountId: number): Observable<PageResponse<CashTransaction>> {
    return this.http.get<PageResponse<CashTransaction>>(
      `${this.apiUrl}/${accountId}/transactions`,
      { params: this.historyParams() },
    );
  }

  orders(accountId: number): Observable<PageResponse<AccountOrder>> {
    return this.http.get<PageResponse<AccountOrder>>(
      `${this.apiUrl}/${accountId}/orders`,
      { params: this.historyParams() },
    );
  }

  trades(accountId: number): Observable<PageResponse<AccountTrade>> {
    return this.http.get<PageResponse<AccountTrade>>(
      `${this.apiUrl}/${accountId}/trades`,
      { params: this.historyParams() },
    );
  }

  disputes(accountId: number): Observable<PageResponse<AccountDispute>> {
    return this.http.get<PageResponse<AccountDispute>>(
      `${this.apiUrl}/${accountId}/disputes`,
      { params: this.historyParams() },
    );
  }

  createTransactionDispute(
    accountId: number,
    transactionId: number,
    request: CreateDisputeRequest,
  ): Observable<AccountDispute> {
    return this.http.post<AccountDispute>(
      `${this.apiUrl}/${accountId}/disputes/transaction/${transactionId}`,
      request,
    );
  }

  createTradeDispute(
    accountId: number,
    tradeId: number,
    request: CreateDisputeRequest,
  ): Observable<AccountDispute> {
    return this.http.post<AccountDispute>(
      `${this.apiUrl}/${accountId}/disputes/trade/${tradeId}`,
      request,
    );
  }

  private historyParams(): HttpParams {
    return new HttpParams().set('page', 0).set('size', 100);
  }
}
