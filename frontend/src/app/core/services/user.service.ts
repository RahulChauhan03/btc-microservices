import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PagedResult, User, UserPayload, UserStats } from '../models/domain.models';
import { ApiError, ApiSuccess, runRequest } from './api-callbacks';
import { CrudHttpService, QueryParams } from './crud-http.service';

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private readonly crudHttp: CrudHttpService) {}

  private readonly endpoint = `${environment.apiBaseUrl}/users`;

  /** Administrators only. Filters: q (name or email), role, page, size, sort. */
  listUsers(params: QueryParams): Observable<PagedResult<User>> {
    return this.crudHttp.page<User>(this.endpoint, params);
  }

  /** Administrators may read anyone; others only themselves (enforced by user-service). */
  getUser(id: number): Observable<User> {
    return this.crudHttp.fetch<User>(`${this.endpoint}/${id}`);
  }

  /** Administrators only. */
  getStats(): Observable<UserStats> {
    return this.crudHttp.fetch<UserStats>(`${this.endpoint}/stats`);
  }

  getUsers(onSuccess?: ApiSuccess<User[]>, onError?: ApiError): void {
    runRequest(this.crudHttp.list<User>(this.endpoint, 'Loading team members…'), onSuccess, onError);
  }

  createUser(payload: UserPayload, onSuccess?: ApiSuccess<User>, onError?: ApiError): void {
    runRequest(this.crudHttp.create<User, UserPayload>(this.endpoint, payload), onSuccess, onError);
  }

  updateUser(
    id: number,
    payload: UserPayload,
    onSuccess?: ApiSuccess<User>,
    onError?: ApiError,
  ): void {
    runRequest(this.crudHttp.update<User, UserPayload>(this.endpoint, id, payload), onSuccess, onError);
  }

  deleteUser(id: number, onSuccess?: ApiSuccess<void>, onError?: ApiError): void {
    runRequest(this.crudHttp.delete(this.endpoint, id), onSuccess, onError);
  }
}
