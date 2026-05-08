import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { delay, tap } from 'rxjs/operators';

import { AuthResponse, AuthUser, LoginRequest } from '../models/auth.models';
import { ApiError, ApiSuccess, runRequest } from './api-callbacks';

const TOKEN_KEY = 'btc_access_token';
const USER_KEY = 'btc_current_user';
const DEMO_EMAIL = 'admin@btcsystem.com';
const DEMO_PASSWORD = 'Password@123';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly router = inject(Router);

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
    if (payload.email !== DEMO_EMAIL || payload.password !== DEMO_PASSWORD) {
      return throwError(() => ({
        status: 401,
        error: {
          message: 'Use the demo admin credentials to sign in locally.',
        },
      }));
    }

    const response: AuthResponse = {
      token: this.createMockToken(),
      user: {
        id: 1,
        name: 'BTC Administrator',
        email: DEMO_EMAIL,
        role: 'ADMIN',
        department: 'Operations',
      },
      expiresIn: 3600,
    };

    return of(response).pipe(
      delay(250),
      tap((mockResponse) => this.persistSession(mockResponse, rememberMe)),
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

  private createMockToken(): string {
    const header = this.toBase64Url({ alg: 'HS256', typ: 'JWT' });
    const payload = this.toBase64Url({
      sub: '1',
      name: 'BTC Administrator',
      email: DEMO_EMAIL,
      role: 'ADMIN',
      department: 'Operations',
    });

    return `${header}.${payload}.mock-signature`;
  }

  private toBase64Url(value: object): string {
    return btoa(JSON.stringify(value))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/g, '');
  }
}
