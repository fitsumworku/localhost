import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type OrderSide = 'B' | 'S';

export interface PlaceOrderRequest {
  clientRequestId: string;
  securityId: number;
  side: OrderSide;
  requestedAmount: number | null;
  quantityOrdered: number | null;
}

export interface OrderDetails {
  order: {
    orderId: number;
    accountId: number;
    securityId: number;
    ticker: string;
    name: string;
    side: OrderSide;
    requestedAmount: number | null;
    quantityOrdered: number | null;
    orderStatus: string;
    rejectionReason: string | null;
  };
  reservation: {
    status: string;
    reservedCash: number | null;
    reservedQuantity: number | null;
  } | null;
  nextAttemptAt: string | null;
  executionAttempts: number;
  lastExecutionMessage: string | null;
}

export interface OrderOperationResponse {
  details: OrderDetails;
  replayed: boolean;
}

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly apiUrl = '/api/accounts';

  constructor(private http: HttpClient) {}

  placeOrder(accountId: number, request: PlaceOrderRequest): Observable<OrderOperationResponse> {
    return this.http.post<OrderOperationResponse>(`${this.apiUrl}/${accountId}/orders`, request);
  }
}
