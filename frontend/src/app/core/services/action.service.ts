import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ActionFilters, ActionRequestSummary } from '../models/action.model';
import { PageResponse } from '../models/common.model';

@Injectable({ providedIn: 'root' })
export class ActionService {
  private readonly http = inject(HttpClient);

  list(filters: ActionFilters): Observable<PageResponse<ActionRequestSummary>> {
    let params = new HttpParams();
    if (filters.agentId) params = params.set('agentId', filters.agentId);
    if (filters.actionType) params = params.set('actionType', filters.actionType);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.riskLevel) params = params.set('riskLevel', filters.riskLevel);
    if (filters.page !== undefined) params = params.set('page', filters.page);
    if (filters.size !== undefined) params = params.set('size', filters.size);
    return this.http.get<PageResponse<ActionRequestSummary>>(
      `${environment.apiBaseUrl}/agent-actions`,
      { params }
    );
  }

  get(id: string): Observable<ActionRequestSummary> {
    return this.http.get<ActionRequestSummary>(`${environment.apiBaseUrl}/agent-actions/${id}`);
  }
}
