import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { inlineFeedbackRequest } from '../http/request-context';

export interface MessageResponse {
  message: string;
}

/**
 * user-service password recovery (public endpoints, routed by the API gateway):
 * - POST /auth/forgot-password {email} -> 202 with the same generic message for every address;
 *   429 when rate-limited; 503 when email delivery is not configured.
 * - POST /auth/reset-password {token, newPassword, confirmPassword} -> 200; 404 invalid or used link,
 *   410 expired link, 400 password rules, 429 rate-limited. Never signs the user in.
 * The token only ever lives in memory: it is never stored, logged or kept in the address bar.
 */
@Injectable({ providedIn: 'root' })
export class PasswordResetService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/auth`;

  requestReset(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.baseUrl}/forgot-password`, { email }, { context: inlineFeedbackRequest() });
  }

  resetPassword(token: string, newPassword: string, confirmPassword: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(
      `${this.baseUrl}/reset-password`,
      { token, newPassword, confirmPassword },
      { context: inlineFeedbackRequest() },
    );
  }
}
