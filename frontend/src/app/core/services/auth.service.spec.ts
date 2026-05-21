import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { LoginResponse } from '../models/auth.model';
import { AuthService } from './auth.service';
import { TokenStorageService } from './token-storage.service';

describe('AuthService', () => {
  let service: AuthService;
  let tokens: TokenStorageService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AuthService);
    tokens = TestBed.inject(TokenStorageService);
    httpMock = TestBed.inject(HttpTestingController);
    localStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('stores the token and emits the user on successful login', (done) => {
    const response: LoginResponse = {
      token: 'jwt-xyz',
      tokenType: 'Bearer',
      expiresInSeconds: 3600,
      user: { id: '00000000-0000-0000-0000-000000000001', username: 'admin', role: 'ADMIN' }
    };

    service.currentUser$.subscribe((user) => {
      if (user) {
        expect(user.username).toBe('admin');
        expect(tokens.getToken()).toBe('jwt-xyz');
        done();
      }
    });

    service.login({ username: 'admin', password: 'admin123' }).subscribe();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush(response);
  });

  it('logout clears the token and resets the user', () => {
    tokens.setToken('jwt-xyz');
    service.logout();
    expect(tokens.getToken()).toBeNull();
    expect(service.currentUser).toBeNull();
    expect(service.isAuthenticated).toBeFalse();
  });

  it('isAuthenticated reflects token presence', () => {
    expect(service.isAuthenticated).toBeFalse();
    tokens.setToken('jwt-xyz');
    expect(service.isAuthenticated).toBeTrue();
  });
});
