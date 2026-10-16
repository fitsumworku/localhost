import { ChangeDetectorRef, Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { HttpErrorResponse } from '@angular/common/http';
import { v4 as uuidv4 } from 'uuid';
import { OrderOperationResponse, OrderService, PlaceOrderRequest } from '../services/order.service';

interface TradeData {
  accountId: number;
  securityId: number;
  symbol: string;
  price: number;
}

interface OrderSummary {
  orderId: number;
  status: string;
  message: string;
}

interface OrderOutcome {
  tone: 'success' | 'warning' | 'error';
  title: string;
  detail: string;
}

@Component({
  selector: 'app-trading-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './trading-dialog.component.html',
  styleUrl: './trading-dialog.component.scss',
})
export class TradingDialogComponent {
  transactionType: 'buy' | 'sell' = 'buy';
  tradeForm: FormGroup;
  estimatedTotal = 0;
  isSubmitting = false;
  orderOutcome: OrderOutcome | null = null;
  orderSummary: OrderSummary | null = null;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: TradeData,
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<TradingDialogComponent>,
    private orderService: OrderService,
    private changeDetector: ChangeDetectorRef,
  ) {
    this.tradeForm = this.fb.group({
      quantity: [10, [Validators.required, Validators.min(1)]],
    });

    this.calculateEstimatedTotal();
    this.tradeForm.valueChanges.subscribe(() => this.calculateEstimatedTotal());
  }

  selectTransactionType(type: 'buy' | 'sell'): void {
    this.transactionType = type;
  }

  calculateEstimatedTotal(): void {
    const quantity = this.tradeForm.get('quantity')?.value || 0;
    this.estimatedTotal = quantity * this.data.price;
  }

  adjustQuantity(delta: number): void {
    const control = this.tradeForm.get('quantity');
    const quantity = Number(control?.value) || 0;
    control?.setValue(Math.max(1, quantity + delta));
  }

  onSubmit(): void {
    if (!this.tradeForm.valid || this.isSubmitting) return;

    this.isSubmitting = true;
    this.orderOutcome = null;
    this.orderSummary = null;
    const quantity = Number(this.tradeForm.get('quantity')?.value);
    const request: PlaceOrderRequest = {
      clientRequestId: uuidv4(),
      securityId: this.data.securityId,
      side: this.transactionType === 'buy' ? 'B' : 'S',
      requestedAmount: this.transactionType === 'buy' ? this.estimatedTotal : null,
      quantityOrdered: this.transactionType === 'sell' ? quantity : null,
    };
    console.log('[TradingDialog] Submitting order', {
      accountId: this.data.accountId,
      securityId: request.securityId,
      side: request.side,
      clientRequestId: request.clientRequestId,
    });

    this.orderService.placeOrder(this.data.accountId, request).subscribe({
      next: (response: OrderOperationResponse) => {
        this.isSubmitting = false;
        const order = response.details?.order;
        if (!order) {
          console.error('[TradingDialog] Invalid order response', { response });
          this.orderOutcome = {
            tone: 'error',
            title: 'Order response incomplete',
            detail: 'Check transaction history before retrying.',
          };
          this.changeDetector.markForCheck();
          return;
        }
        const status = order.orderStatus.toUpperCase();
        const message = response.details.lastExecutionMessage || '';
        this.orderSummary = { orderId: order.orderId, status, message };
        console.log('[TradingDialog] Order response', {
          accountId: this.data.accountId,
          orderId: order.orderId,
          status,
          replayed: response.replayed,
          reservation: response.details.reservation,
          executionAttempts: response.details.executionAttempts,
          lastExecutionMessage: message,
        });
        this.orderOutcome = this.normalizeOrderOutcome(order.orderId, status, order.rejectionReason, response.replayed);
        this.changeDetector.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting = false;
        const rejectedResponse = error.error as OrderOperationResponse | undefined;
        const rejectedOrder = rejectedResponse?.details?.order;
        if (error.status === 409 && rejectedOrder) {
          const status = rejectedOrder.orderStatus.toUpperCase();
          const reason = rejectedOrder.rejectionReason || 'The order did not meet the execution requirements.';
          this.orderSummary = {
            orderId: rejectedOrder.orderId,
            status,
            message: this.clientReason(reason),
          };
          this.orderOutcome = {
            tone: 'warning',
            title: `Order #${rejectedOrder.orderId} rejected and recorded`,
            detail: this.clientReason(reason),
          };
          console.warn('[TradingDialog] Order rejected and recorded', {
            accountId: this.data.accountId,
            orderId: rejectedOrder.orderId,
            side: request.side,
            status,
            reason,
            reservation: rejectedResponse?.details?.reservation,
          });
          this.changeDetector.markForCheck();
          return;
        }
        console.error('[TradingDialog] Order submission failed', {
          accountId: this.data.accountId,
          securityId: this.data.securityId,
          side: request.side,
          status: error.status,
          errorCode: error.error?.code,
          error: error.error,
        });
        this.orderOutcome = {
          tone: 'error',
          title: 'Order could not be submitted',
          detail: error.error?.message || 'Check your connection and try again.',
        };
        this.changeDetector.markForCheck();
      },
    });
  }

  private normalizeOrderOutcome(
    orderId: number,
    status: string,
    rejectionReason: string | null,
    replayed: boolean,
  ): OrderOutcome {
    if (status === 'REJECTED') {
      return {
        tone: 'warning',
        title: `Order #${orderId} rejected and recorded`,
        detail: this.clientReason(rejectionReason),
      };
    }
    if (replayed) {
      return {
        tone: 'warning',
        title: `Order #${orderId} already submitted`,
        detail: 'The original order is still tracked in transaction history.',
      };
    }
    if (status === 'ACCEPTED' || status === 'IN_EXECUTION') {
      return {
        tone: 'success',
        title: `Order #${orderId} accepted`,
        detail: 'Your order is waiting for market execution and remains visible in transaction history.',
      };
    }
    if (status === 'FILLED') {
      return {
        tone: 'success',
        title: `Order #${orderId} filled`,
        detail: 'Your trade was completed and is available in transaction history.',
      };
    }
    return {
      tone: 'warning',
      title: `Order #${orderId}: ${status}`,
      detail: 'The latest order state is available in transaction history.',
    };
  }

  private clientReason(reason: string | null | undefined): string {
    switch (reason) {
      case 'INSUFFICIENT_AVAILABLE_QUANTITY':
        return 'You do not have enough available shares to sell. A pending buy order does not become sellable until it executes.';
      case 'INSUFFICIENT_AVAILABLE_CASH':
        return 'There is not enough available cash for this buy order. Check pending reservations or deposit more cash.';
      case 'MARKET_CLOSED':
        return 'The market is currently closed. Your accepted order will remain pending until execution is available.';
      default:
        return reason || 'The order did not meet the execution requirements.';
    }
  }
  close(): void {
    this.dialogRef.close();
  }
}
