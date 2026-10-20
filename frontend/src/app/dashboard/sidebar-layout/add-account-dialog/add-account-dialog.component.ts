import { Component } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-add-account-dialog',
  templateUrl: './add-account-dialog.component.html',
  styleUrl: './add-account-dialog.component.scss',
  standalone: true,
  imports: [
    MatButtonModule,
    MatIconModule
  ]
})
export class AddAccountDialogComponent {
  constructor(private dialogRef: MatDialogRef<AddAccountDialogComponent>) {}

  onSubmit(): void {
    this.dialogRef.close(true);
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
