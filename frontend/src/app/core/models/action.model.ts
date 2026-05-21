import { ActionRequestStatus, ActionType, RiskLevel } from './common.model';

export interface ActionRequestSummary {
  id: string;
  agentId: string;
  actionType: ActionType;
  resource: string;
  riskLevel: RiskLevel;
  metadataJson: string | null;
  status: ActionRequestStatus;
  decisionReason: string | null;
  matchedPolicyId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ActionFilters {
  agentId?: string;
  actionType?: ActionType;
  status?: ActionRequestStatus;
  riskLevel?: RiskLevel;
  page?: number;
  size?: number;
}
