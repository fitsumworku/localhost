import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

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
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './dispute-transaction-dialog.component.html',
  styleUrl: './dispute-transaction-dialog.component.scss',
})
export class DisputeTransactionDialogComponent {
  disputeForm: FormGroup;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: Transaction,
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<DisputeTransactionDialogComponent>
  ) {
    this.disputeForm = this.fb.group({
      details: ['', [Validators.required, Validators.minLength(10)]],
    });
  }

  onSubmit(): void {
    if (this.disputeForm.valid) {
      const disputeData = {
        transaction: this.data,
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
