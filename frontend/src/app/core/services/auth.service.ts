import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { AuthResponse, AuthUser, LoginRequest } from '../models/auth.models';
import { ApiError, ApiSuccess, runRequest } from './api-callbacks';

const TOKEN_KEY = 'btc_access_token';
const USER_KEY = 'btc_current_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly endpoint = `${environment.apiBaseUrl}/auth/login`;

  private readonly tokenSignal = signal<string | null>(null);
  private readonly userSignal = signal<AuthUser | null>(null);

  readonly token = this.tokenSignal.asReadonly();
  readonly currentUser = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => !!this.tokenSignal());

  constructor() {
    this.restoreSession();
  }

  login(
    payload: LoginRequest,
    rememberMe: boolean,
    onSuccess?: ApiSuccess<AuthResponse>,
    onError?: ApiError,
  ): void {
    runRequest(this.loginRequest(payload, rememberMe), onSuccess, onError);
  }

  private loginRequest(payload: LoginRequest, rememberMe: boolean): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(this.endpoint, payload).pipe(
      tap((response) => this.persistSession(response, rememberMe)),
    );
  }

  logout(redirect = true): void {
    this.clearStorage(localStorage);
    this.clearStorage(sessionStorage);
    this.tokenSignal.set(null);
    this.userSignal.set(null);

    if (redirect) {
      this.router.navigate(['/login']);
    }
  }

  hasRole(role: AuthUser['role']): boolean {
    return this.userSignal()?.role === role;
  }

  getAuthorizationToken(): string | null {
    return this.tokenSignal();
  }

  private restoreSession(): void {
    const storedToken = localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY);
    const storedUser = localStorage.getItem(USER_KEY) ?? sessionStorage.getItem(USER_KEY);

    if (!storedToken) {
      return;
    }

    this.tokenSignal.set(storedToken);
    this.userSignal.set(storedUser ? (JSON.parse(storedUser) as AuthUser) : this.getUserFromToken(storedToken));
  }

  private persistSession(response: AuthResponse, rememberMe: boolean): void {
    const storage = rememberMe ? localStorage : sessionStorage;
    const fallbackStorage = rememberMe ? sessionStorage : localStorage;
    const user = response.user ?? this.getUserFromToken(response.token);

    this.clearStorage(fallbackStorage);
    storage.setItem(TOKEN_KEY, response.token);
    storage.setItem(USER_KEY, JSON.stringify(user));
    this.tokenSignal.set(response.token);
    this.userSignal.set(user);
  }

  private clearStorage(storage: Storage): void {
    storage.removeItem(TOKEN_KEY);
    storage.removeItem(USER_KEY);
  }

  private getUserFromToken(token: string): AuthUser {
    try {
      const payload = JSON.parse(atob(token.split('.')[1] ?? ''));
      return {
        id: Number(payload.sub ?? payload.userId ?? 0),
        name: payload.name ?? payload.preferred_username ?? 'Business Traveler',
        email: payload.email ?? 'employee@company.com',
        role: payload.role ?? payload.roles?.[0] ?? 'EMPLOYEE',
        department: payload.department ?? 'Operations',
      };
    } catch {
      return {
        id: 0,
        name: 'Business Traveler',
        email: 'employee@company.com',
        role: 'EMPLOYEE',
        department: 'Operations',
      };
    }
  }

}
