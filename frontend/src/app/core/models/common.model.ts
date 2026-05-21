export type ActionType =
  | 'SEND_EMAIL'
  | 'DELETE_FILE'
  | 'READ_SECRET'
  | 'WRITE_DATABASE'
  | 'CALL_EXTERNAL_API'
  | 'DEPLOY_CODE'
  | 'ACCESS_CUSTOMER_DATA'
  | 'CREATE_GITHUB_PR'
  | 'RUN_TERMINAL_COMMAND';

export const ACTION_TYPES: ActionType[] = [
  'SEND_EMAIL',
  'DELETE_FILE',
  'READ_SECRET',
  'WRITE_DATABASE',
  'CALL_EXTERNAL_API',
  'DEPLOY_CODE',
  'ACCESS_CUSTOMER_DATA',
  'CREATE_GITHUB_PR',
  'RUN_TERMINAL_COMMAND'
];

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export const RISK_LEVELS: RiskLevel[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

export type ActionRequestStatus =
  | 'RECEIVED'
  | 'ALLOWED'
  | 'DENIED'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED';

export const ACTION_REQUEST_STATUSES: ActionRequestStatus[] = [
  'RECEIVED',
  'ALLOWED',
  'DENIED',
  'PENDING_APPROVAL',
  'APPROVED',
  'REJECTED'
];

export type PolicyOutcome = 'ALLOW' | 'DENY' | 'NEEDS_APPROVAL';
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED';
export type AgentStatus = 'ACTIVE' | 'DISABLED' | 'ROTATED' | 'DELETED';

/** Mirrors the structural fields of Spring's PageImpl JSON serialisation. */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}
