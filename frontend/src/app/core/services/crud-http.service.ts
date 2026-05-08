import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class CrudHttpService {
  private readonly http = inject(HttpClient);

  list<T>(endpoint: string): Observable<T[]> {
    return this.http.get<T[]>(endpoint);
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
