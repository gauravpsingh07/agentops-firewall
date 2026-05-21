import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Approval, ApprovalFilters } from '../models/approval.model';
import { PageResponse } from '../models/common.model';

@Injectable({ providedIn: 'root' })
export class ApprovalService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/approvals`;

  list(filters: ApprovalFilters): Observable<PageResponse<Approval>> {
    let params = new HttpParams();
    if (filters.status) params = params.set('status', filters.status);
    if (filters.agentId) params = params.set('agentId', filters.agentId);
    if (filters.actionType) params = params.set('actionType', filters.actionType);
    if (filters.riskLevel) params = params.set('riskLevel', filters.riskLevel);
    if (filters.page !== undefined) params = params.set('page', filters.page);
    if (filters.size !== undefined) params = params.set('size', filters.size);
    return this.http.get<PageResponse<Approval>>(this.base, { params });
  }

  approve(id: string, note?: string): Observable<Approval> {
    return this.http.post<Approval>(`${this.base}/${id}/approve`, { note: note ?? null });
  }

  reject(id: string, note?: string): Observable<Approval> {
    return this.http.post<Approval>(`${this.base}/${id}/reject`, { note: note ?? null });
  }
}
