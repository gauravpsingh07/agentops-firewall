import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthenticatedUser, LoginRequest, LoginResponse } from '../models/auth.model';
import { TokenStorageService } from './token-storage.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokens = inject(TokenStorageService);

  private readonly userSubject = new BehaviorSubject<AuthenticatedUser | null>(null);
  readonly currentUser$ = this.userSubject.asObservable();

  get currentUser(): AuthenticatedUser | null {
    return this.userSubject.value;
  }

  get isAuthenticated(): boolean {
    return this.tokens.getToken() !== null;
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiBaseUrl}/auth/login`, request).pipe(
      tap((response) => {
        this.tokens.setToken(response.token);
        this.userSubject.next(response.user);
      })
    );
  }

  loadCurrentUser(): Observable<AuthenticatedUser> {
    return this.http.get<AuthenticatedUser>(`${environment.apiBaseUrl}/auth/me`).pipe(
      tap((user) => this.userSubject.next(user))
    );
  }

  logout(): void {
    this.tokens.clear();
    this.userSubject.next(null);
  }
}
