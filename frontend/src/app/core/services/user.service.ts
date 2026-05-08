import { Injectable } from '@angular/core';

import { environment } from '../../../environments/environment';
import { User, UserPayload } from '../models/domain.models';
import { ApiError, ApiSuccess, runRequest } from './api-callbacks';
import { CrudHttpService } from './crud-http.service';

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private readonly crudHttp: CrudHttpService) {}

  private readonly endpoint = `${environment.apiBaseUrl}/users`;

  getUsers(onSuccess?: ApiSuccess<User[]>, onError?: ApiError): void {
    runRequest(this.crudHttp.list<User>(this.endpoint), onSuccess, onError);
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
