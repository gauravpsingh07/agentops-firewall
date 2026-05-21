import { TestBed } from '@angular/core/testing';

import { TokenStorageService } from './token-storage.service';

describe('TokenStorageService', () => {
  let service: TokenStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TokenStorageService);
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('returns null when no token is stored', () => {
    expect(service.getToken()).toBeNull();
  });

  it('round-trips a token through localStorage', () => {
    service.setToken('jwt-abc');
    expect(service.getToken()).toBe('jwt-abc');
  });

  it('clear() removes the stored token', () => {
    service.setToken('jwt-abc');
    service.clear();
    expect(service.getToken()).toBeNull();
  });
});
