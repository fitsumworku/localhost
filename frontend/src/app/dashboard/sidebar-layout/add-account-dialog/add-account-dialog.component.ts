import { Component } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-add-account-dialog',
  templateUrl: './add-account-dialog.component.html',
  styleUrl: './add-account-dialog.component.scss',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ]
})
export class AddAccountDialogComponent {
  readonly form: FormGroup;

  constructor(
    private formBuilder: FormBuilder,
    private dialogRef: MatDialogRef<AddAccountDialogComponent>,
  ) {
    this.form = this.formBuilder.group({
      accountName: ['', [Validators.required, Validators.maxLength(20)]],
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.dialogRef.close({ accountName: this.form.get('accountName')?.value.trim() });
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
