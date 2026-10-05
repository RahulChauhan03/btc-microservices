import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PasswordResetService } from '../services/password-reset.service';
import { AuthService } from '../services/auth.service';
import { authTokenInterceptor } from './auth-token.interceptor';

describe('authTokenInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authTokenInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { getAuthorizationToken: () => 'stale-token' } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('does not attach a stored token to public POST auth requests', () => {
    for (const path of ['/auth/login', '/auth/forgot-password', '/auth/reset-password']) {
      http.post(`http://localhost:8080${path}`, {}).subscribe();
      expect(backend.expectOne(`http://localhost:8080${path}`).request.headers.has('Authorization')).toBe(false);
    }
  });

  it('sends password recovery POSTs to the gateway paths without a bearer token', () => {
    const passwordReset = TestBed.inject(PasswordResetService);

    passwordReset.requestReset('person@example.com').subscribe();
    const forgotRequest = backend.expectOne('http://localhost:8080/auth/forgot-password');
    expect(forgotRequest.request.method).toBe('POST');
    expect(forgotRequest.request.body).toEqual({ email: 'person@example.com' });
    expect(forgotRequest.request.headers.has('Authorization')).toBe(false);
    forgotRequest.flush({ message: 'accepted' });

    passwordReset.resetPassword('reset-token', 'new-password', 'new-password').subscribe();
    const resetRequest = backend.expectOne('http://localhost:8080/auth/reset-password');
    expect(resetRequest.request.method).toBe('POST');
    expect(resetRequest.request.body).toEqual({
      token: 'reset-token',
      newPassword: 'new-password',
      confirmPassword: 'new-password',
    });
    expect(resetRequest.request.headers.has('Authorization')).toBe(false);
    resetRequest.flush({ message: 'reset' });
  });

  it('keeps bearer authentication on protected API requests', () => {
    http.get('http://localhost:8080/users').subscribe();

    expect(backend.expectOne('http://localhost:8080/users').request.headers.get('Authorization')).toBe(
      'Bearer stale-token',
    );
  });
});