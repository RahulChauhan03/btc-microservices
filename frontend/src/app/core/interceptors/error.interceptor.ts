import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { TOAST_MESSAGES } from '../constants/toast-messages';
import { SKIP_ERROR_TOAST } from '../http/request-context';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toastService = inject(ToastService);
  const authService = inject(AuthService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const message =
        error.error?.message ??
        error.error?.error ??
        (error.status === 0 ? TOAST_MESSAGES.errors.gatewayUnavailable : TOAST_MESSAGES.errors.requestFailed);

      // Only an expired or revoked session ends the session; a 401 for a signed-out visitor (login,
      // password recovery) must not redirect them away from the page they are on.
      if (error.status === 401 && authService.isAuthenticated()) {
        authService.logout();
      }

      if (!req.context.get(SKIP_ERROR_TOAST)) {
        toastService.error(message);
      }
      return throwError(() => error);
    }),
  );
};
