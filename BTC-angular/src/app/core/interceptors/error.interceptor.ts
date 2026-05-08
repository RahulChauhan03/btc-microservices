import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { TOAST_MESSAGES } from '../constants/toast-messages';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toastService = inject(ToastService);
  const authService = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const message =
        error.error?.message ??
        error.error?.error ??
        (error.status === 0 ? TOAST_MESSAGES.errors.gatewayUnavailable : TOAST_MESSAGES.errors.requestFailed);

      if (error.status === 401 && router.url !== '/login') {
        authService.logout();
      }

      toastService.error(message);
      return throwError(() => error);
    }),
  );
};
