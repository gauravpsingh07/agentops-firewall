import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { DashboardService } from './dashboard.service';

/**
 * Five small GETs that back the dashboard landing page. Each tile on
 * /dashboard binds directly to one of these methods, so a wrong URL
 * here equals an empty card in the UI.
 */
describe('DashboardService', () => {
  let service: DashboardService;
  let httpMock: HttpTestingController;
  const base = `${environment.apiBaseUrl}/dashboard`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(DashboardService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('summary() issues GET /dashboard/summary', () => {
    service.summary().subscribe();
    const req = httpMock.expectOne(`${base}/summary`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('recentActions() issues GET /dashboard/recent-actions', () => {
    service.recentActions().subscribe();
    const req = httpMock.expectOne(`${base}/recent-actions`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('riskDistribution() issues GET /dashboard/risk-distribution', () => {
    service.riskDistribution().subscribe();
    const req = httpMock.expectOne(`${base}/risk-distribution`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('decisionDistribution() issues GET /dashboard/decision-distribution', () => {
    service.decisionDistribution().subscribe();
    const req = httpMock.expectOne(`${base}/decision-distribution`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('recentAuditEvents() issues GET /dashboard/recent-audit-events', () => {
    service.recentAuditEvents().subscribe();
    const req = httpMock.expectOne(`${base}/recent-audit-events`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
