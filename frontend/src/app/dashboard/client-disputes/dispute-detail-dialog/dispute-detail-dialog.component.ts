import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';

type DisputeStatus = 'open' | 'in-review' | 'resolved';

interface Dispute {
  id: string;
  date: string;
  clientId: string;
  reason: string;
  disputedAmount: number;
  status: DisputeStatus;
  assignedOwner: string;
}

@Component({
  selector: 'app-dispute-detail-dialog',
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
    MatCardModule,
  ],
  templateUrl: './dispute-detail-dialog.component.html',
  styleUrl: './dispute-detail-dialog.component.scss',
})
export class DisputeDetailDialogComponent {
  detailForm: FormGroup;

  statusOptions = [
    { value: 'open', label: 'Open' },
    { value: 'in-review', label: 'In Review' },
    { value: 'resolved', label: 'Resolved' },
  ];

//   assigneeOptions = [
//     { value: 'maya-chen', label: 'Maya Chen' },
//     { value: 'omar-patel', label: 'Omar Patel' },
//     { value: 'priya-shah', label: 'Priya Shah' },
//     { value: 'daniel-kim', label: 'Daniel Kim' },
//     { value: 'sofia-martinez', label: 'Sofia Martinez' },
//   ];

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: Dispute,
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<DisputeDetailDialogComponent>
  ) {
    this.detailForm = this.fb.group({
      status: [this.data.status, Validators.required],
      assignedOwner: [this.data.assignedOwner, Validators.required],
      notes: ['', Validators.minLength(10)],
    });
  }

  getStatusColor(status: DisputeStatus): string {
    switch (status) {
      case 'open':
        return '#ff9800';
      case 'in-review':
        return '#2196f3';
      case 'resolved':
        return '#51cf66';
      default:
        return '#999';
    }
  }

  getStatusLabel(status: DisputeStatus): string {
    switch (status) {
      case 'open':
        return 'Open';
      case 'in-review':
        return 'In Review';
      case 'resolved':
        return 'Resolved';
      default:
        return status;
    }
  }

  onSave(): void {
    if (this.detailForm.valid) {
      const updatedData = {
        ...this.data,
        status: this.detailForm.get('status')?.value,
        assignedOwner: this.detailForm.get('assignedOwner')?.value,
        notes: this.detailForm.get('notes')?.value,
      };
      console.log('Dispute updated:', updatedData);
      this.dialogRef.close(updatedData);
    }
  }

  close(): void {
    this.dialogRef.close();
  }
}
