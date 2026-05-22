import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { SimulationRequest } from '../models/simulator.model';
import { SimulatorService } from './simulator.service';

/**
 * The simulator endpoint is the single most distinctive non-CRUD call
 * in the API — it accepts an action shape and returns a decision
 * without persisting anything. Wire-format coverage here protects
 * the Policies > Simulator page from silent body-shape drift.
 */
describe('SimulatorService', () => {
  let service: SimulatorService;
  let httpMock: HttpTestingController;
  const url = `${environment.apiBaseUrl}/policies/simulate`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(SimulatorService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('simulate(request) POSTs to /policies/simulate with the request as the body', () => {
    const request: SimulationRequest = {
      actionType: 'DELETE_FILE',
      riskLevel: 'MEDIUM',
      resource: '/var/log/old.log',
      agentId: null,
      metadata: { recipientDomain: 'external.com' }
    } as SimulationRequest;

    service.simulate(request).subscribe();
    const req = httpMock.expectOne(url);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });
});
