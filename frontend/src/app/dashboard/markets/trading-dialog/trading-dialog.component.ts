import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

interface TradeData {
  symbol: string;
  price: number;
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
  orderType: 'market' | 'limit' = 'market';
  tradeForm: FormGroup;
  estimatedTotal = 0;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: TradeData,
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<TradingDialogComponent>
  ) {
    this.tradeForm = this.fb.group({
      quantity: [10, [Validators.required, Validators.min(1)]],
      limitPrice: [this.data.price, Validators.min(0)],
    });

    this.calculateEstimatedTotal();
    this.tradeForm.valueChanges.subscribe(() => this.calculateEstimatedTotal());
  }

  selectTransactionType(type: 'buy' | 'sell'): void {
    this.transactionType = type;
  }

  selectOrderType(type: 'market' | 'limit'): void {
    this.orderType = type;
  }

  calculateEstimatedTotal(): void {
    const quantity = this.tradeForm.get('quantity')?.value || 0;
    const price = this.orderType === 'market' ? this.data.price : this.tradeForm.get('limitPrice')?.value || 0;
    this.estimatedTotal = quantity * price;
  }

  onSubmit(): void {
    if (this.tradeForm.valid) {
      const orderData = {
        symbol: this.data.symbol,
        transactionType: this.transactionType,
        orderType: this.orderType,
        quantity: this.tradeForm.get('quantity')?.value,
        price: this.orderType === 'market' ? this.data.price : this.tradeForm.get('limitPrice')?.value,
        estimatedTotal: this.estimatedTotal,
      };
      console.log('Order placed:', orderData);
      this.dialogRef.close(orderData);
    }
  }

  close(): void {
    this.dialogRef.close();
  }
}
