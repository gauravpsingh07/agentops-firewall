import { ActionType, PolicyOutcome, RiskLevel } from './common.model';

export type ConditionOperator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'IN'
  | 'NOT_IN'
  | 'EXISTS'
  | 'NOT_EXISTS'
  | 'CONTAINS'
  | 'NOT_CONTAINS'
  | 'MATCHES'
  | 'GTE'
  | 'LTE';

export const CONDITION_OPERATORS: ConditionOperator[] = [
  'EQUALS',
  'NOT_EQUALS',
  'IN',
  'NOT_IN',
  'EXISTS',
  'NOT_EXISTS',
  'CONTAINS',
  'NOT_CONTAINS',
  'MATCHES',
  'GTE',
  'LTE'
];

export interface PolicyCondition {
  id?: string;
  field: string;
  operator: ConditionOperator;
  value: string;
}

export interface Policy {
  id: string;
  name: string;
  description: string | null;
  priority: number;
  effect: PolicyOutcome;
  enabled: boolean;
  actionType: ActionType | null;
  resourcePattern: string | null;
  minRiskLevel: RiskLevel | null;
  createdByUserId: string;
  createdAt: string;
  updatedAt: string;
  conditions: PolicyCondition[];
}

export interface CreatePolicyRequest {
  name: string;
  description?: string | null;
  effect: PolicyOutcome;
  priority: number;
  actionType?: ActionType | null;
  resourcePattern?: string | null;
  minRiskLevel?: RiskLevel | null;
  conditions: PolicyCondition[];
}

export interface UpdatePolicyRequest {
  name?: string;
  description?: string | null;
  effect?: PolicyOutcome;
  priority?: number;
  enabled?: boolean;
  actionType?: ActionType | null;
  resourcePattern?: string | null;
  minRiskLevel?: RiskLevel | null;
  conditions?: PolicyCondition[];
}
