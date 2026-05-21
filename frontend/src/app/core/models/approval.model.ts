import {
  ActionRequestStatus,
  ActionType,
  ApprovalStatus,
  RiskLevel
} from './common.model';

export interface Approval {
  id: string;
  status: ApprovalStatus;
  actionRequestId: string;
  actionType: ActionType;
  resource: string | null;
  riskLevel: RiskLevel;
  actionStatus: ActionRequestStatus;
  agentId: string | null;
  agentName: string | null;
  matchedPolicyId: string | null;
  decisionReason: string | null;
  reviewerUserId: string | null;
  reviewerUsername: string | null;
  reviewerNote: string | null;
  createdAt: string;
  decidedAt: string | null;
  expiresAt: string | null;
}

export interface ApprovalFilters {
  status?: ApprovalStatus;
  agentId?: string;
  actionType?: ActionType;
  riskLevel?: RiskLevel;
  page?: number;
  size?: number;
}
