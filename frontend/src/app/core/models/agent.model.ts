import { AgentStatus } from './common.model';

export interface Agent {
  id: string;
  name: string;
  description: string | null;
  status: AgentStatus;
  ownerUserId: string;
  lastUsedAt: string | null;
  createdAt: string;
  updatedAt: string;
}
