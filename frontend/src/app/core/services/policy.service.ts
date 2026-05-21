import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CreatePolicyRequest, Policy, UpdatePolicyRequest } from '../models/policy.model';

@Injectable({ providedIn: 'root' })
export class PolicyService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/policies`;

  list(): Observable<Policy[]> {
    return this.http.get<Policy[]>(this.base);
  }

  get(id: string): Observable<Policy> {
    return this.http.get<Policy>(`${this.base}/${id}`);
  }

  create(payload: CreatePolicyRequest): Observable<Policy> {
    return this.http.post<Policy>(this.base, payload);
  }

  update(id: string, payload: UpdatePolicyRequest): Observable<Policy> {
    return this.http.patch<Policy>(`${this.base}/${id}`, payload);
  }

  disable(id: string): Observable<Policy> {
    return this.http.delete<Policy>(`${this.base}/${id}`);
  }
}
