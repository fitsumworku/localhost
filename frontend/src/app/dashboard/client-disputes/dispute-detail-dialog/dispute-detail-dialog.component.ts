import { ChangeDetectorRef, Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { Observable, concat, defer, of } from 'rxjs';
import { last } from 'rxjs/operators';
import { AdminDispute, AdminService, DisputeStatus } from '../../services/admin.service';
import { WORKFLOW_STATUSES, disputeStatusLabel, disputeStatusTone } from '../../services/dispute-status';

const MAX_REPLY_LENGTH = 2000;

@Component({
  selector: 'app-dispute-detail-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatIconModule],
  templateUrl: './dispute-detail-dialog.component.html',
  styleUrl: './dispute-detail-dialog.component.scss',
})
export class DisputeDetailDialogComponent {
  readonly statusOptions = WORKFLOW_STATUSES;
  readonly statusLabel = disputeStatusLabel;
  readonly statusTone = disputeStatusTone;
  readonly maxReplyLength = MAX_REPLY_LENGTH;

  selectedStatus: DisputeStatus;
  reply = '';
  isSaving = false;
  errorMessage = '';

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: AdminDispute,
    private dialogRef: MatDialogRef<DisputeDetailDialogComponent>,
    private admin: AdminService,
    private changeDetector: ChangeDetectorRef,
  ) {
    this.selectedStatus = data.status;
  }

  get hasReply(): boolean {
    return this.reply.trim().length > 0;
  }

  get hasChanges(): boolean {
    return this.selectedStatus !== this.data.status || this.hasReply;
  }

  get saveLabel(): string {
    if (this.isSaving) return 'Saving…';
    const resolving = this.selectedStatus === 'RESOLVED' && this.data.status !== 'RESOLVED';
    if (resolving) return this.hasReply ? 'Reply and resolve' : 'Resolve dispute';
    if (this.hasReply && this.selectedStatus === this.data.status) return 'Send reply';
    return 'Save changes';
  }

  selectStatus(status: DisputeStatus): void {
    this.selectedStatus = status;
  }

  onSave(): void {
    if (!this.hasChanges || this.isSaving || this.reply.length > MAX_REPLY_LENGTH) return;
    this.isSaving = true;
    this.errorMessage = '';

    // Send the reply first so the client sees it alongside the new status.
    const steps: Observable<unknown>[] = [];
    if (this.hasReply) steps.push(defer(() => this.admin.respondToDispute(this.data, this.reply.trim())));
    if (this.selectedStatus !== this.data.status) {
      steps.push(defer(() => this.admin.updateDisputeStatus(this.data, this.selectedStatus)));
    }

    concat(...steps, of(null)).pipe(last()).subscribe({
      next: () => this.dialogRef.close(true),
      error: error => {
        console.error('[Disputes] Failed to update dispute', { disputeId: this.data.disputeId, error });
        this.errorMessage = error?.error?.message ?? 'The dispute could not be updated.';
        this.isSaving = false;
        this.changeDetector.markForCheck();
      },
    });
  }

  close(): void {
    this.dialogRef.close(false);
  }
}
