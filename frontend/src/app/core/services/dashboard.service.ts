import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ActionRequestSummary } from '../models/action.model';
import { AuditLogEntry } from '../models/audit.model';
import {
  DashboardSummary,
  DecisionDistributionEntry,
  RiskDistributionEntry
} from '../models/dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/dashboard`;

  summary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.base}/summary`);
  }

  recentActions(): Observable<ActionRequestSummary[]> {
    return this.http.get<ActionRequestSummary[]>(`${this.base}/recent-actions`);
  }

  riskDistribution(): Observable<RiskDistributionEntry[]> {
    return this.http.get<RiskDistributionEntry[]>(`${this.base}/risk-distribution`);
  }

  decisionDistribution(): Observable<DecisionDistributionEntry[]> {
    return this.http.get<DecisionDistributionEntry[]>(`${this.base}/decision-distribution`);
  }

  recentAuditEvents(): Observable<AuditLogEntry[]> {
    return this.http.get<AuditLogEntry[]>(`${this.base}/recent-audit-events`);
  }
}
