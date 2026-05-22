import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { authGuard } from './auth.guard';

/**
 * The auth guard is the front door of every JWT-protected route in the
 * dashboard. A regression that lets unauthenticated traffic through —
 * or worse, sends authenticated users to /login on every navigation —
 * is invisible until QA notices, so this suite locks both behaviours
 * down.
 */
describe('authGuard', () => {
  class MockAuthService {
    isAuthenticated = false;
  }

  let auth: MockAuthService;

  beforeEach(() => {
    auth = new MockAuthService();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth }
      ]
    });
  });

  it('returns true when the user is authenticated', () => {
    auth.isAuthenticated = true;

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, {} as never)
    );

    expect(result).toBeTrue();
  });

  it('returns a UrlTree pointing at /login when the user is not authenticated', () => {
    auth.isAuthenticated = false;

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, {} as never)
    );

    expect(result).toEqual(jasmine.any(UrlTree));
    const router = TestBed.inject(Router);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });
});
