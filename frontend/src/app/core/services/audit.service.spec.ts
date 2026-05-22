import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { AuditService } from './audit.service';

/**
 * Audit log search is read-only, but it's the appeal-of-last-resort
 * for figuring out what an agent did and when. A silently-broken
 * filter (wrong param name, missing date range) means investigators
 * can't narrow the search and have to scroll the whole table.
 */
describe('AuditService', () => {
  let service: AuditService;
  let httpMock: HttpTestingController;
  const url = `${environment.apiBaseUrl}/audit-logs`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AuditService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('search() with all filters issues GET /audit-logs with every param', () => {
    service.search({
      eventType: 'ACTION_DECIDED',
      actorType: 'USER',
      subjectType: 'ACTION_REQUEST',
      from: '2026-05-01T00:00:00Z',
      to: '2026-05-22T23:59:59Z',
      page: 0,
      size: 25
    }).subscribe();

    const req = httpMock.expectOne((r) => r.url === url);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('eventType')).toBe('ACTION_DECIDED');
    expect(req.request.params.get('actorType')).toBe('USER');
    expect(req.request.params.get('subjectType')).toBe('ACTION_REQUEST');
    expect(req.request.params.get('from')).toBe('2026-05-01T00:00:00Z');
    expect(req.request.params.get('to')).toBe('2026-05-22T23:59:59Z');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('25');
    req.flush({ items: [], page: 0, size: 25, total: 0 });
  });

  it('search() with empty filters issues GET /audit-logs with no params', () => {
    service.search({}).subscribe();

    const req = httpMock.expectOne((r) => r.url === url);
    expect(req.request.params.keys().length).toBe(0);
    req.flush({ items: [], page: 0, size: 0, total: 0 });
  });

  it('search() omits absent filters (a single supplied filter sends one param)', () => {
    service.search({ eventType: 'POLICY_DECISION' }).subscribe();

    const req = httpMock.expectOne((r) => r.url === url);
    expect(req.request.params.get('eventType')).toBe('POLICY_DECISION');
    expect(req.request.params.keys().length).toBe(1);
    req.flush({ items: [], page: 0, size: 0, total: 0 });
  });
});
