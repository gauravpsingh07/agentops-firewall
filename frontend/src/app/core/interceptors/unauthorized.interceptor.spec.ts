import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { TokenStorageService } from '../services/token-storage.service';
import { unauthorizedInterceptor } from './unauthorized.interceptor';

/**
 * The unauthorized interceptor watches every response for HTTP 401 and
 * — for anything except the login call itself — clears the stored
 * token and bounces the user to /login. The carve-out for /auth/login
 * exists because a 401 there means "wrong password" and is meant to
 * be handled by the login component, not the global session reset.
 *
 * Both the happy path (401 on a protected route) and the carve-out
 * (401 on the login endpoint) are exercised here, plus the no-op
 * behaviour on non-401 errors that the rest of the app depends on.
 */
describe('unauthorizedInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let tokens: TokenStorageService;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([unauthorizedInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerSpy }
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

  it('on 401 from a protected route: clears the token and navigates to /login', (done) => {
    tokens.setToken('jwt-xyz');

    http.get('/api/dashboard/summary').subscribe({
      next: () => fail('should have errored'),
      error: (err: HttpErrorResponse) => {
        // The error still propagates so callers can react if they want.
        expect(err.status).toBe(401);
        expect(tokens.getToken()).toBeNull();
        expect(routerSpy.navigateByUrl).toHaveBeenCalledOnceWith('/login');
        done();
      }
    });

    const req = httpMock.expectOne('/api/dashboard/summary');
    req.flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('on 401 from /auth/login: does NOT clear the token and does NOT redirect', (done) => {
    tokens.setToken('previous-session-token');

    http.post('/api/auth/login', { username: 'admin', password: 'wrong' }).subscribe({
      next: () => fail('should have errored'),
      error: (err: HttpErrorResponse) => {
        expect(err.status).toBe(401);
        // Carve-out: the login component owns the user-facing
        // "wrong password" message; the global interceptor must
        // stay out of the way.
        expect(tokens.getToken()).toBe('previous-session-token');
        expect(routerSpy.navigateByUrl).not.toHaveBeenCalled();
        done();
      }
    });

    const req = httpMock.expectOne('/api/auth/login');
    req.flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('on non-401 errors: does not clear the token and does not redirect', (done) => {
    tokens.setToken('jwt-xyz');

    http.get('/api/dashboard/summary').subscribe({
      next: () => fail('should have errored'),
      error: (err: HttpErrorResponse) => {
        expect(err.status).toBe(500);
        expect(tokens.getToken()).toBe('jwt-xyz');
        expect(routerSpy.navigateByUrl).not.toHaveBeenCalled();
        done();
      }
    });

    const req = httpMock.expectOne('/api/dashboard/summary');
    req.flush(null, { status: 500, statusText: 'Internal Server Error' });
  });

  it('on 2xx success: passes through with no side effects', () => {
    tokens.setToken('jwt-xyz');

    http.get('/api/dashboard/summary').subscribe((body) => {
      expect(body).toEqual({ ok: true });
    });

    const req = httpMock.expectOne('/api/dashboard/summary');
    req.flush({ ok: true });

    expect(tokens.getToken()).toBe('jwt-xyz');
    expect(routerSpy.navigateByUrl).not.toHaveBeenCalled();
  });
});
