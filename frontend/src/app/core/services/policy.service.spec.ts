import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { CreatePolicyRequest, UpdatePolicyRequest } from '../models/policy.model';
import { PolicyService } from './policy.service';

/**
 * The policy service is the only write-heavy data service in the
 * frontend (create / update / disable). Each method maps to a
 * distinct HTTP verb, and getting any of them wrong silently
 * inverts the intent of a policy edit — covered explicitly here.
 */
describe('PolicyService', () => {
  let service: PolicyService;
  let httpMock: HttpTestingController;
  const base = `${environment.apiBaseUrl}/policies`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(PolicyService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list() issues GET /policies', () => {
    service.list().subscribe();
    const req = httpMock.expectOne(base);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('get(id) issues GET /policies/{id}', () => {
    const id = 'pol-123';
    service.get(id).subscribe();
    const req = httpMock.expectOne(`${base}/${id}`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('create(payload) issues POST /policies with the payload as the body', () => {
    const payload: CreatePolicyRequest = {
      name: 'Deny SEND_EMAIL externally',
      description: 'block outbound mail to external domains',
      effect: 'DENY',
      priority: 90,
      enabled: true,
      actionType: 'SEND_EMAIL',
      resourcePattern: null,
      minRiskLevel: null,
      conditions: []
    } as CreatePolicyRequest;

    service.create(payload).subscribe();
    const req = httpMock.expectOne(base);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({});
  });

  it('update(id, payload) issues PATCH /policies/{id} with the payload', () => {
    const id = 'pol-123';
    const payload: UpdatePolicyRequest = {
      name: 'Updated name',
      description: null,
      effect: 'NEEDS_APPROVAL',
      priority: 80,
      enabled: false,
      actionType: 'DELETE_FILE',
      resourcePattern: null,
      minRiskLevel: null,
      conditions: []
    } as UpdatePolicyRequest;

    service.update(id, payload).subscribe();
    const req = httpMock.expectOne(`${base}/${id}`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(payload);
    req.flush({});
  });

  it('disable(id) issues DELETE /policies/{id} (soft-disable, not destructive remove)', () => {
    const id = 'pol-123';
    service.disable(id).subscribe();
    const req = httpMock.expectOne(`${base}/${id}`);
    expect(req.request.method).toBe('DELETE');
    req.flush({});
  });
});
