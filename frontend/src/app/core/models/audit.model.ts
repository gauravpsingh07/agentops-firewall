export interface AuditLogEntry {
  id: string;
  eventType: string;
  actorType: string | null;
  actorId: string | null;
  subjectType: string | null;
  subjectId: string | null;
  summary: string | null;
  detailsJson: string | null;
  createdAt: string;
}

export interface AuditFilters {
  eventType?: string;
  actorType?: string;
  subjectType?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}
