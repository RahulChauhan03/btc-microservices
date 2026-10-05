import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { AuthService } from '../services/auth.service';

const PUBLIC_AUTH_PATHS = new Set(['/auth/login', '/auth/forgot-password', '/auth/reset-password']);

export const authTokenInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.method === 'POST' && isPublicAuthPath(req.url)) {
    return next(req);
  }

  const authService = inject(AuthService);
  const token = authService.getAuthorizationToken();

  if (!token) {
    return next(req);
  }

  return next(
    req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    }),
  );
};

function isPublicAuthPath(url: string): boolean {
  const pathname = new URL(url, 'http://localhost').pathname.replace(/\/+$/, '') || '/';
  return PUBLIC_AUTH_PATHS.has(pathname);
}
