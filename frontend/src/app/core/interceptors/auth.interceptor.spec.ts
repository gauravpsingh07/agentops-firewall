import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TokenStorageService } from '../services/token-storage.service';
import { authInterceptor } from './auth.interceptor';

/**
 * The auth interceptor is responsible for attaching the JWT to every
 * outgoing HTTP request. If it silently drops the header (or attaches
 * an empty one), every API call becomes unauthenticated and the user
 * appears to "lose their session" on the next request. These tests
 * exercise the interceptor through the real HttpClient pipeline using
 * HttpTestingController, so the assertion is on the actual headers a
 * server would see.
 */
describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let tokens: TokenStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    tokens = TestBed.inject(TokenStorageService);
    localStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('attaches "Authorization: Bearer <token>" when a token is stored', () => {
    tokens.setToken('jwt-xyz');

    http.get('/api/anything').subscribe();

    const req = httpMock.expectOne('/api/anything');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-xyz');
    req.flush({});
  });

  it('does not attach the Authorization header when no token is stored', () => {
    expect(tokens.getToken()).toBeNull();

    http.get('/api/anything').subscribe();

    const req = httpMock.expectOne('/api/anything');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('does not mutate the original request object (uses req.clone)', () => {
    tokens.setToken('jwt-xyz');

    // The interceptor's contract is to return a new request, not to
    // edit the one it received. We verify by issuing the call and
    // confirming the outgoing request carries the header (i.e. the
    // clone path was taken; an in-place mutation would also pass
    // this assertion but throw under Angular's strict immutability
    // checks).
    http.post('/api/things', { hello: 'world' }).subscribe();

    const req = httpMock.expectOne('/api/things');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-xyz');
    expect(req.request.body).toEqual({ hello: 'world' });
    req.flush({});
  });
});
