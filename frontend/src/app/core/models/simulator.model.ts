import { ActionType, PolicyOutcome, RiskLevel } from './common.model';

export interface SimulationRequest {
  actionType: ActionType;
  resource?: string;
  riskLevel: RiskLevel;
  metadata?: Record<string, unknown>;
  agentName?: string;
}

export interface SimulationResponse {
  decision: PolicyOutcome;
  matchedPolicyId: string | null;
  matchedPolicyName: string | null;
  reason: string;
  simulated: boolean;
}
