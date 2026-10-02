import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { AuthService } from './auth.service';

const TOKEN_KEY = 'btc_access_token';
const USER_KEY = 'btc_current_user';

function tokenWithExp(exp: number | undefined): string {
  const encode = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, '');
  return `${encode({ alg: 'HS256' })}.${encode({ sub: '1', role: 'EMPLOYEE', exp })}.sig`;
}

function createService(): AuthService {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideRouter([])] });
  return TestBed.inject(AuthService);
}

describe('AuthService session restore', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('restores a session whose token has not expired', () => {
    const token = tokenWithExp(Math.floor(Date.now() / 1000) + 600);
    localStorage.setItem(TOKEN_KEY, token);

    const service = createService();

    expect(service.isAuthenticated()).toBe(true);
    expect(service.getAuthorizationToken()).toBe(token);
  });

  it('discards an expired token and its stored user', () => {
    localStorage.setItem(TOKEN_KEY, tokenWithExp(Math.floor(Date.now() / 1000) - 60));
    localStorage.setItem(USER_KEY, JSON.stringify({ id: 1 }));

    const service = createService();

    expect(service.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(localStorage.getItem(USER_KEY)).toBeNull();
  });

  it('discards tokens without a readable exp claim', () => {
    sessionStorage.setItem(TOKEN_KEY, tokenWithExp(undefined));

    expect(createService().isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });
});
