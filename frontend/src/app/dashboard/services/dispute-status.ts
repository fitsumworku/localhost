import { DisputeStatus } from './admin.service';

// Admins work disputes through Open → In review → Resolved. Escalated and
// Rejected exist in the backend and are still labelled if a dispute has them.
export const WORKFLOW_STATUSES: { value: DisputeStatus; label: string }[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'UNDER_REVIEW', label: 'In review' },
  { value: 'RESOLVED', label: 'Resolved' },
];

const LABELS: Record<DisputeStatus, string> = {
  OPEN: 'Open',
  UNDER_REVIEW: 'In review',
  ESCALATED: 'Escalated',
  RESOLVED: 'Resolved',
  REJECTED: 'Rejected',
};

const TONES: Record<DisputeStatus, string> = {
  OPEN: 'warning',
  UNDER_REVIEW: 'info',
  ESCALATED: 'negative',
  RESOLVED: 'positive',
  REJECTED: '',
};

export function disputeStatusLabel(status: DisputeStatus): string {
  return LABELS[status] ?? status;
}

export function disputeStatusTone(status: DisputeStatus): string {
  return TONES[status] ?? '';
}
