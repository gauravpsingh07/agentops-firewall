import { ActionRequestStatus, RiskLevel } from './common.model';

export interface DashboardSummary {
  totalActions: number;
  allowedActions: number;
  deniedActions: number;
  pendingApprovals: number;
  approvedActions: number;
  rejectedActions: number;
  totalAgents: number;
  activeAgents: number;
  totalPolicies: number;
  enabledPolicies: number;
}

export interface RiskDistributionEntry {
  riskLevel: RiskLevel;
  count: number;
}

export interface DecisionDistributionEntry {
  status: ActionRequestStatus;
  count: number;
}
