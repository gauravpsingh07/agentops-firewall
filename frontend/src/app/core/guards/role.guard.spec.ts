import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';

import { AuthenticatedUser } from '../models/auth.model';
import { UserRole } from '../models/user-role';
import { AuthService } from '../services/auth.service';
import { roleGuard } from './role.guard';

/**
 * The role guard enforces RBAC on routes that demand a specific user
 * role (e.g. the approval inbox is REVIEWER + ADMIN only). A bug that
 * silently allows VIEWERs into a write surface is the kind of issue
 * that doesn't surface until someone abuses it, so each branch is
 * covered explicitly.
 */
describe('roleGuard', () => {
  class MockAuthService {
    currentUser: AuthenticatedUser | null = null;
  }

  let auth: MockAuthService;

  function buildUser(role: UserRole): AuthenticatedUser {
    return {
      id: '00000000-0000-0000-0000-000000000001',
      username: 'someone',
      role
    };
  }

  beforeEach(() => {
    auth = new MockAuthService();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth }
      ]
    });
  });

  it('returns true when the current user has one of the allowed roles', () => {
    auth.currentUser = buildUser('ADMIN');
    const guard = roleGuard('ADMIN', 'REVIEWER');

    const result = TestBed.runInInjectionContext(() =>
      guard({} as never, {} as never)
    );

    expect(result).toBeTrue();
  });

  it('returns true when ANY of the allowed roles matches (multi-role factory)', () => {
    auth.currentUser = buildUser('REVIEWER');
    const guard = roleGuard('ADMIN', 'REVIEWER');

    const result = TestBed.runInInjectionContext(() =>
      guard({} as never, {} as never)
    );

    expect(result).toBeTrue();
  });

  it('redirects to /dashboard when the user has the wrong role', () => {
    auth.currentUser = buildUser('VIEWER');
    const guard = roleGuard('ADMIN');

    const result = TestBed.runInInjectionContext(() =>
      guard({} as never, {} as never)
    );

    expect(result).toEqual(jasmine.any(UrlTree));
    const router = TestBed.inject(Router);
    expect(router.serializeUrl(result as UrlTree)).toBe('/dashboard');
  });

  it('redirects to /dashboard when no user is loaded yet', () => {
    auth.currentUser = null;
    const guard = roleGuard('ADMIN');

    const result = TestBed.runInInjectionContext(() =>
      guard({} as never, {} as never)
    );

    expect(result).toEqual(jasmine.any(UrlTree));
    const router = TestBed.inject(Router);
    expect(router.serializeUrl(result as UrlTree)).toBe('/dashboard');
  });
});
