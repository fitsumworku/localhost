import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { HttpErrorResponse } from '@angular/common/http';
import { CashMovementType, CashService } from '../../services/cash.service';

export interface CashMovementDialogData {
  accountId: number;
  type: CashMovementType;
}

@Component({
  selector: 'app-cash-movement-dialog',
  templateUrl: './cash-movement-dialog.component.html',
  styleUrl: './cash-movement-dialog.component.scss',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
})
export class CashMovementDialogComponent {
  readonly form: FormGroup;
  isSubmitting = false;
  errorMessage = '';

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: CashMovementDialogData,
    private formBuilder: FormBuilder,
    private cashService: CashService,
    private dialogRef: MatDialogRef<CashMovementDialogComponent>,
  ) {
    this.form = this.formBuilder.group({
      amount: [null, [Validators.required, Validators.min(0.01), Validators.max(999999999999999999.99)]],
    });
  }

  get isDeposit(): boolean {
    return this.data.type === 'deposit';
  }

  onSubmit(): void {
    if (this.form.invalid || this.isSubmitting) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = '';
    this.cashService.moveCash(this.data.accountId, this.data.type, {
      clientRequestId: this.createRequestId(),
      amount: Number(this.form.get('amount')?.value),
    }).subscribe({
      next: () => this.dialogRef.close(true),
      error: (error: HttpErrorResponse) => {
        this.isSubmitting = false;
        this.errorMessage = error.error?.message || 'The cash transaction could not be completed.';
      },
    });
  }

  private createRequestId(): string {
    const cryptoApi = globalThis.crypto;
    if (cryptoApi?.getRandomValues) {
      const bytes = new Uint8Array(16);
      cryptoApi.getRandomValues(bytes);
      bytes[6] = (bytes[6] & 0x0f) | 0x40;
      bytes[8] = (bytes[8] & 0x3f) | 0x80;
      const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0'));
      return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex.slice(6, 8).join('')}-${hex.slice(8, 10).join('')}-${hex.slice(10).join('')}`;
    }

    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, character => {
      const random = Math.floor(Math.random() * 16);
      const value = character === 'x' ? random : (random & 0x3) | 0x8;
      return value.toString(16);
    });
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
