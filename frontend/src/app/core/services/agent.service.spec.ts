import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { Agent } from '../models/agent.model';
import { AgentService } from './agent.service';

/**
 * Smallest of the data-services in surface area, but exercising the
 * pass-through-response path here keeps it covered if the endpoint
 * ever moves or starts wrapping the array in an envelope.
 */
describe('AgentService', () => {
  let service: AgentService;
  let httpMock: HttpTestingController;
  const url = `${environment.apiBaseUrl}/agents`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AgentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list() issues GET /agents', () => {
    let received: Agent[] | undefined;
    service.list().subscribe((response) => (received = response));

    const req = httpMock.expectOne(url);
    expect(req.request.method).toBe('GET');

    const body: Agent[] = [
      {
        id: '00000000-0000-0000-0000-000000000001',
        name: 'email-bot',
        status: 'ACTIVE',
        createdAt: '2026-05-22T00:00:00Z'
      } as Agent
    ];
    req.flush(body);

    expect(received).toEqual(body);
  });
});
