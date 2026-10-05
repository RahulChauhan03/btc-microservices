import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { inlineFeedbackRequest } from '../http/request-context';
import { AppNotification, PagedResult } from '../models/domain.models';
import { CrudHttpService } from './crud-http.service';

/**
 * The signed-in user's notifications (notification-service only ever returns the caller's own).
 * Holds the unread count shown on the bell so every view stays in sync.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly crudHttp = inject(CrudHttpService);
  private readonly endpoint = `${environment.apiBaseUrl}/notifications`;
  private readonly unreadSignal = signal(0);

  readonly unread = this.unreadSignal.asReadonly();

  list(params: { unreadOnly?: boolean; page?: number; size?: number }): Observable<PagedResult<AppNotification>> {
    return this.crudHttp.page<AppNotification>(this.endpoint, params);
  }

  refreshUnreadCount(): Observable<number> {
    return this.crudHttp.fetch<{ count: number }>(`${this.endpoint}/unread-count`).pipe(
      map((response) => response.count),
      tap((count) => this.unreadSignal.set(count)),
    );
  }

  markRead(id: number): Observable<void> {
    return this.http
      .post<void>(`${this.endpoint}/${id}/read`, null, { context: inlineFeedbackRequest() })
      .pipe(tap(() => this.unreadSignal.update((count) => Math.max(0, count - 1))));
  }

  markAllRead(): Observable<void> {
    return this.http
      .post<{ updated: number }>(`${this.endpoint}/read-all`, null, { context: inlineFeedbackRequest() })
      .pipe(
        tap(() => this.unreadSignal.set(0)),
        map(() => undefined),
      );
  }
}
