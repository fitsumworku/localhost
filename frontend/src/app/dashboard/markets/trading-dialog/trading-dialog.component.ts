import { ChangeDetectorRef, Component, DestroyRef, Inject, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { HttpErrorResponse } from '@angular/common/http';
import { v4 as uuidv4 } from 'uuid';
import { OrderDetails, OrderOperationResponse, OrderService, PlaceOrderRequest } from '../services/order.service';

interface TradeData {
  accountId: number;
  securityId: number;
  symbol: string;
  name: string;
  price: number;
  currency: string;
  isOpen: boolean;
}

interface OrderOutcome {
  tone: 'success' | 'warning' | 'error' | 'pending';
  title: string;
  detail: string;
}

const PENDING_STATUSES = ['SUBMITTED', 'ACCEPTED', 'IN_EXECUTION'];
const POLL_INTERVAL_MS = 2000;
const POLL_LIMIT = 15; // ~30 seconds; the order worker runs every 5s

@Component({
  selector: 'app-trading-dialog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatDialogModule, MatIconModule],
  templateUrl: './trading-dialog.component.html',
  styleUrl: './trading-dialog.component.scss',
})
export class TradingDialogComponent {
  transactionType: 'buy' | 'sell' = 'buy';
  tradeForm: FormGroup;
  isSubmitting = false;
  orderOutcome: OrderOutcome | null = null;

  private pollTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: TradeData,
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<TradingDialogComponent>,
    private orderService: OrderService,
    private changeDetector: ChangeDetectorRef,
  ) {
    // The backend takes BUY orders as a USD amount (2 decimals) and SELL orders as a quantity
    this.tradeForm = this.fb.group({
      amount: [100, [Validators.required, Validators.min(0.01)]],
      quantity: [1, [Validators.required, Validators.min(0.000000000001)]],
    });
    inject(DestroyRef).onDestroy(() => this.stopPolling());
  }

  get isUsd(): boolean {
    return !this.data.currency || this.data.currency === 'USD';
  }

  get canSubmit(): boolean {
    const control = this.tradeForm.get(this.transactionType === 'buy' ? 'amount' : 'quantity');
    return !!control?.valid && !this.isSubmitting && this.orderOutcome?.tone !== 'pending';
  }

  // Rough preview only; the order executes at the live bid/ask when it fills
  get estimate(): string {
    if (this.transactionType === 'buy') {
      const amount = Number(this.tradeForm.get('amount')?.value) || 0;
      if (!this.isUsd) return 'Converted from USD when the order fills';
      return `≈ ${this.formatQuantity(amount / this.data.price)} ${this.data.symbol}`;
    }
    const quantity = Number(this.tradeForm.get('quantity')?.value) || 0;
    const proceeds = quantity * this.data.price;
    return `≈ ${proceeds.toLocaleString('en-US', { style: 'currency', currency: this.data.currency || 'USD' })}`;
  }

  selectTransactionType(type: 'buy' | 'sell'): void {
    this.transactionType = type;
    this.orderOutcome = null;
  }

  onSubmit(): void {
    if (!this.canSubmit) return;

    this.isSubmitting = true;
    this.orderOutcome = null;
    const isBuy = this.transactionType === 'buy';
    const request: PlaceOrderRequest = {
      clientRequestId: uuidv4(),
      securityId: this.data.securityId,
      side: isBuy ? 'B' : 'S',
      requestedAmount: isBuy ? Math.round(Number(this.tradeForm.get('amount')?.value) * 100) / 100 : null,
      quantityOrdered: isBuy ? null : Number(Number(this.tradeForm.get('quantity')?.value).toFixed(12)),
    };

    this.orderService.placeOrder(this.data.accountId, request).subscribe({
      next: (response: OrderOperationResponse) => {
        this.isSubmitting = false;
        const order = response.details?.order;
        if (!order) {
          this.orderOutcome = {
            tone: 'error',
            title: 'Order response incomplete',
            detail: 'Check transaction history before retrying.',
          };
          this.changeDetector.markForCheck();
          return;
        }
        this.showOrder(response.details, response.replayed);
        if (PENDING_STATUSES.includes(order.orderStatus.toUpperCase()) && !response.replayed) {
          this.pollOrder(order.orderId, 0);
        }
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting = false;
        const rejectedOrder = (error.error as OrderOperationResponse | undefined)?.details?.order;
        if (error.status === 409 && rejectedOrder) {
          this.orderOutcome = {
            tone: 'warning',
            title: `Order #${rejectedOrder.orderId} rejected`,
            detail: this.clientReason(rejectedOrder.rejectionReason),
          };
        } else {
          console.error('[TradingDialog] Order submission failed', { status: error.status, error: error.error });
          this.orderOutcome = {
            tone: 'error',
            title: 'Order could not be submitted',
            detail: error.error?.message || 'Check your connection and try again.',
          };
        }
        this.changeDetector.markForCheck();
      },
    });
  }

  // Follow the order until the background worker fills (or rejects) it
  private pollOrder(orderId: number, attempt: number): void {
    if (attempt >= POLL_LIMIT) {
      this.orderOutcome = {
        tone: 'warning',
        title: `Order #${orderId} is still waiting`,
        detail: this.data.isOpen
          ? 'It will fill shortly. You can close this window; it stays in your activity.'
          : 'The market is closed, so it will fill when trading opens. It stays in your activity.',
      };
      this.changeDetector.markForCheck();
      return;
    }
    this.pollTimer = setTimeout(() => {
      this.orderService.getOrder(this.data.accountId, orderId).subscribe({
        next: (details: OrderDetails) => {
          this.showOrder(details, false);
          if (PENDING_STATUSES.includes(details.order.orderStatus.toUpperCase())) {
            this.pollOrder(orderId, attempt + 1);
          }
        },
        error: () => this.pollOrder(orderId, attempt + 1),
      });
    }, POLL_INTERVAL_MS);
  }

  private stopPolling(): void {
    if (this.pollTimer) clearTimeout(this.pollTimer);
    this.pollTimer = null;
  }

  private showOrder(details: OrderDetails, replayed: boolean): void {
    const { orderId, orderStatus, rejectionReason } = details.order;
    this.orderOutcome = this.normalizeOrderOutcome(
      orderId,
      orderStatus.toUpperCase(),
      rejectionReason,
      replayed,
      details.lastExecutionMessage,
    );
    this.changeDetector.markForCheck();
  }

  private normalizeOrderOutcome(
    orderId: number,
    status: string,
    rejectionReason: string | null,
    replayed: boolean,
    lastExecutionMessage: string | null,
  ): OrderOutcome {
    if (status === 'REJECTED') {
      return { tone: 'warning', title: `Order #${orderId} rejected`, detail: this.clientReason(rejectionReason) };
    }
    if (replayed) {
      return {
        tone: 'warning',
        title: `Order #${orderId} already submitted`,
        detail: 'The original order is still tracked in your activity.',
      };
    }
    if (PENDING_STATUSES.includes(status)) {
      return {
        tone: 'pending',
        title: `Order #${orderId} accepted`,
        detail: lastExecutionMessage
          ? `Waiting to fill: ${this.clientReason(lastExecutionMessage)}`
          : 'Waiting for the market to fill it…',
      };
    }
    if (status === 'FILLED') {
      return {
        tone: 'success',
        title: `Order #${orderId} filled`,
        detail: 'Your trade is complete and now shows in your portfolio.',
      };
    }
    if (status === 'CANCELLED') {
      return { tone: 'warning', title: `Order #${orderId} cancelled`, detail: 'Any reserved cash or shares were released.' };
    }
    return {
      tone: 'warning',
      title: `Order #${orderId}: ${status}`,
      detail: 'The latest order state is available in your activity.',
    };
  }

  private clientReason(reason: string | null | undefined): string {
    switch (reason) {
      case 'INSUFFICIENT_AVAILABLE_QUANTITY':
        return 'You do not have enough available shares to sell. A pending buy order does not become sellable until it executes.';
      case 'INSUFFICIENT_AVAILABLE_CASH':
        return 'There is not enough available cash for this buy order. Deposit more cash or wait for pending orders.';
      case 'MARKET_CLOSED':
        return 'The market is currently closed. The order will fill when trading opens.';
      case 'QUOTE_TOO_OLD':
      case 'INVALID_QUOTE_TIME':
        return 'No fresh price is available right now (check the server clock).';
      default:
        return reason || 'The order did not meet the execution requirements.';
    }
  }

  private formatQuantity(quantity: number): string {
    return quantity.toLocaleString('en-US', { maximumFractionDigits: quantity < 1 ? 6 : 4 });
  }

  close(): void {
    this.dialogRef.close();
  }
}
