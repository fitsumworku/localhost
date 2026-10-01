import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

interface Transaction {
  date: string;
  asset: string;
  type: 'buy' | 'sell';
  amount: number;
  price: number;
  pnl: number;
}

@Component({
  selector: 'app-dispute-transaction-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './dispute-transaction-dialog.component.html',
  styleUrl: './dispute-transaction-dialog.component.scss',
})
export class DisputeTransactionDialogComponent {
  disputeForm: FormGroup;

  disputeReasons = [
    { value: 'wrong_price', label: 'Wrong Price' },
    { value: 'duplicate', label: 'Duplicate Transaction' },
    { value: 'unauthorized', label: 'Unauthorized' },
    { value: 'incorrect_quantity', label: 'Incorrect Quantity' },
    { value: 'other', label: 'Other' },
  ];

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: Transaction,
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<DisputeTransactionDialogComponent>
  ) {
    this.disputeForm = this.fb.group({
      reason: ['', Validators.required],
      details: ['', [Validators.required, Validators.minLength(10)]],
    });
  }

  onSubmit(): void {
    if (this.disputeForm.valid) {
      const disputeData = {
        transaction: this.data,
        reason: this.disputeForm.get('reason')?.value,
        details: this.disputeForm.get('details')?.value,
      };
      console.log('Dispute submitted:', disputeData);
      this.dialogRef.close(disputeData);
    }
  }

  close(): void {
    this.dialogRef.close();
  }
}
