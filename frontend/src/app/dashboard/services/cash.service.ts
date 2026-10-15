import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type CashMovementType = 'deposit' | 'withdrawal';

export interface CashMovementRequest {
  clientRequestId: string;
  amount: number;
}

export interface CashTransaction {
  transactionId: number;
  accountId: number;
  clientRequestId: string;
  amount: number;
  type: 'DEPOSIT' | 'WITHDRAWAL';
  status: 'PENDING' | 'COMPLETED' | 'FAILED';
  transactionDate: string;
  completedAt: string | null;
  failureReason: string | null;
}

export interface CashOperationResponse {
  transaction: CashTransaction;
  replayed: boolean;
}

@Injectable({ providedIn: 'root' })
export class CashService {
  private readonly apiUrl = '/api/accounts';

  constructor(private http: HttpClient) {}

  moveCash(accountId: number, type: CashMovementType, request: CashMovementRequest): Observable<CashOperationResponse> {
    return this.http.post<CashOperationResponse>(
      `${this.apiUrl}/${accountId}/${type === 'deposit' ? 'deposits' : 'withdrawals'}`,
      request,
    );
  }
}
