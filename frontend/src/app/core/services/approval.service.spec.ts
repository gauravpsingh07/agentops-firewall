import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { ApprovalService } from './approval.service';

/**
 * The approval inbox is the human-in-the-loop surface, so getting the
 * wire format wrong here means the wrong action ends up
 * approved/rejected. Tests cover the filtered list query, the
 * approve/reject endpoints, and the note serialisation (the backend
 * expects `note: null` when no note was provided).
 */
describe('ApprovalService', () => {
  let service: ApprovalService;
  let httpMock: HttpTestingController;
  const base = `${environment.apiBaseUrl}/approvals`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ApprovalService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list() with filters issues GET /approvals with the expected params', () => {
    service.list({
      status: 'PENDING',
      agentId: 'agent-7',
      actionType: 'DELETE_FILE',
      riskLevel: 'HIGH',
      page: 1,
      size: 10
    }).subscribe();

    const req = httpMock.expectOne((r) => r.url === base);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('status')).toBe('PENDING');
    expect(req.request.params.get('agentId')).toBe('agent-7');
    expect(req.request.params.get('actionType')).toBe('DELETE_FILE');
    expect(req.request.params.get('riskLevel')).toBe('HIGH');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({ items: [], page: 1, size: 10, total: 0 });
  });

  it('list() with no filters issues GET /approvals with no params', () => {
    service.list({}).subscribe();

    const req = httpMock.expectOne((r) => r.url === base);
    expect(req.request.params.keys().length).toBe(0);
    req.flush({ items: [], page: 0, size: 0, total: 0 });
  });

  it('approve(id, note) POSTs to /approvals/{id}/approve with { note }', () => {
    const id = 'abcd';
    service.approve(id, 'looks fine').subscribe();

    const req = httpMock.expectOne(`${base}/${id}/approve`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ note: 'looks fine' });
    req.flush({});
  });

  it('approve(id) without a note POSTs { note: null } (backend contract)', () => {
    service.approve('abcd').subscribe();

    const req = httpMock.expectOne(`${base}/abcd/approve`);
    expect(req.request.body).toEqual({ note: null });
    req.flush({});
  });

  it('reject(id, note) POSTs to /approvals/{id}/reject with { note }', () => {
    service.reject('abcd', 'blocked by policy review').subscribe();

    const req = httpMock.expectOne(`${base}/abcd/reject`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ note: 'blocked by policy review' });
    req.flush({});
  });

  it('reject(id) without a note POSTs { note: null }', () => {
    service.reject('abcd').subscribe();

    const req = httpMock.expectOne(`${base}/abcd/reject`);
    expect(req.request.body).toEqual({ note: null });
    req.flush({});
  });
});
