import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { inlineFeedbackRequest, withLoaderMessage } from '../http/request-context';
import { PagedResult } from '../models/domain.models';

export type QueryParams = Record<string, string | number | boolean | null | undefined | readonly string[]>;

/** Drops empty values so optional filters are simply omitted. */
export function toHttpParams(params: QueryParams): HttpParams {
  let httpParams = new HttpParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === '' || (Array.isArray(value) && !value.length)) {
      continue;
    }
    httpParams = httpParams.set(key, Array.isArray(value) ? value.join(',') : String(value));
  }
  return httpParams;
}

@Injectable({ providedIn: 'root' })
export class CrudHttpService {
  private readonly http = inject(HttpClient);

  list<T>(endpoint: string, loaderMessage?: string): Observable<T[]> {
    return this.http.get<T[]>(endpoint, loaderMessage ? { context: withLoaderMessage(loaderMessage) } : {});
  }

  /**
   * One page of a list endpoint with its total (X-Total-Count). For pages that show their own loading and
   * error states, so neither the global loader nor the error toast is used.
   */
  page<T>(endpoint: string, params: QueryParams = {}): Observable<PagedResult<T>> {
    return this.http
      .get<T[]>(endpoint, { params: toHttpParams(params), observe: 'response', context: inlineFeedbackRequest() })
      .pipe(
        map((response) => {
          const items = response.body ?? [];
          const total = Number(response.headers.get('X-Total-Count'));
          return { items, total: Number.isFinite(total) && response.headers.has('X-Total-Count') ? total : items.length };
        }),
      );
  }

  /** A single resource or summary, with inline feedback (see {@link page}). */
  fetch<T>(url: string, params: QueryParams = {}): Observable<T> {
    return this.http.get<T>(url, { params: toHttpParams(params), context: inlineFeedbackRequest() });
  }

  create<TResponse, TPayload>(endpoint: string, payload: TPayload): Observable<TResponse> {
    return this.http.post<TResponse>(endpoint, payload);
  }

  update<TResponse, TPayload>(endpoint: string, id: number, payload: TPayload): Observable<TResponse> {
    return this.http.put<TResponse>(`${endpoint}/${id}`, payload);
  }

  delete(endpoint: string, id: number): Observable<void> {
    return this.http.delete<void>(`${endpoint}/${id}`);
  }
}
