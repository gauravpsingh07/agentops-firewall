import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuditFilters, AuditLogEntry } from '../models/audit.model';
import { PageResponse } from '../models/common.model';

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);

  search(filters: AuditFilters): Observable<PageResponse<AuditLogEntry>> {
    let params = new HttpParams();
    if (filters.eventType) params = params.set('eventType', filters.eventType);
    if (filters.actorType) params = params.set('actorType', filters.actorType);
    if (filters.subjectType) params = params.set('subjectType', filters.subjectType);
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    if (filters.page !== undefined) params = params.set('page', filters.page);
    if (filters.size !== undefined) params = params.set('size', filters.size);
    return this.http.get<PageResponse<AuditLogEntry>>(
      `${environment.apiBaseUrl}/audit-logs`,
      { params }
    );
  }
}
