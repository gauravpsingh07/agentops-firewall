import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { ActionService } from './action.service';

/**
 * Locks the wire format of ActionService against the backend's
 * /api/agent-actions endpoint. Coverage is on URL, HTTP method, and
 * the exact query-param set the filters object generates — a typo on
 * either side of that contract (param renamed, omitted, or sent with
 * the wrong value) is exactly the kind of regression unit tests at
 * this layer catch before it hits the action feed UI.
 */
describe('ActionService', () => {
  let service: ActionService;
  let httpMock: HttpTestingController;
  const url = `${environment.apiBaseUrl}/agent-actions`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ActionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list() with all filters issues GET /agent-actions with every param', () => {
    service.list({
      agentId: 'agent-1',
      actionType: 'SEND_EMAIL',
      status: 'PENDING_APPROVAL',
      riskLevel: 'MEDIUM',
      page: 2,
      size: 25
    }).subscribe();

    const req = httpMock.expectOne((r) => r.url === url);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('agentId')).toBe('agent-1');
    expect(req.request.params.get('actionType')).toBe('SEND_EMAIL');
    expect(req.request.params.get('status')).toBe('PENDING_APPROVAL');
    expect(req.request.params.get('riskLevel')).toBe('MEDIUM');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('25');
    req.flush({ items: [], page: 0, size: 25, total: 0 });
  });

  it('list() with empty filters issues GET /agent-actions with no params', () => {
    service.list({}).subscribe();

    const req = httpMock.expectOne((r) => r.url === url);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys().length).toBe(0);
    req.flush({ items: [], page: 0, size: 0, total: 0 });
  });

  it('list() omits absent filter keys (does not send empty strings)', () => {
    service.list({ status: 'ALLOWED', page: 0 }).subscribe();

    const req = httpMock.expectOne((r) => r.url === url);
    expect(req.request.params.get('status')).toBe('ALLOWED');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.has('agentId')).toBeFalse();
    expect(req.request.params.has('actionType')).toBeFalse();
    expect(req.request.params.has('riskLevel')).toBeFalse();
    expect(req.request.params.has('size')).toBeFalse();
    req.flush({ items: [], page: 0, size: 0, total: 0 });
  });

  it('get(id) issues GET /agent-actions/{id}', () => {
    const id = '11111111-1111-1111-1111-111111111111';
    service.get(id).subscribe();

    const req = httpMock.expectOne(`${url}/${id}`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });
});
