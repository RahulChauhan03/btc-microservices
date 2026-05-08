import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { MOCK_USERS } from '../data/mock-data';
import { User } from '../models/domain.models';
import { ApiError, ApiSuccess, runRequest } from './api-callbacks';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${environment.apiBaseUrl}/users`;
  private readonly useMockData = environment.useMockData;
  private users: User[] = structuredClone(MOCK_USERS);

  getUsers(onSuccess?: ApiSuccess<User[]>, onError?: ApiError): void {
    runRequest(this.getUsersRequest(), onSuccess, onError);
  }

  createUser(payload: Omit<User, 'id'>, onSuccess?: ApiSuccess<User>, onError?: ApiError): void {
    runRequest(this.createUserRequest(payload), onSuccess, onError);
  }

  updateUser(id: number, payload: Omit<User, 'id'>, onSuccess?: ApiSuccess<User>, onError?: ApiError): void {
    runRequest(this.updateUserRequest(id, payload), onSuccess, onError);
  }

  deleteUser(id: number, onSuccess?: ApiSuccess<void>, onError?: ApiError): void {
    runRequest(this.deleteUserRequest(id), onSuccess, onError);
  }

  private getUsersRequest(): Observable<User[]> {
    if (this.useMockData) {
      return of(structuredClone(this.users));
    }

    return this.http.get<User[]>(this.endpoint);
  }

  private createUserRequest(payload: Omit<User, 'id'>): Observable<User> {
    if (this.useMockData) {
      const user: User = { id: this.nextId(), ...payload };
      this.users = [user, ...this.users];
      return of(structuredClone(user));
    }

    return this.http.post<User>(this.endpoint, payload);
  }

  private updateUserRequest(id: number, payload: Omit<User, 'id'>): Observable<User> {
    if (this.useMockData) {
      const updatedUser: User = { id, ...payload };
      this.users = this.users.map((user) => (user.id === id ? updatedUser : user));
      return of(structuredClone(updatedUser));
    }

    return this.http.put<User>(`${this.endpoint}/${id}`, payload);
  }

  private deleteUserRequest(id: number): Observable<void> {
    if (this.useMockData) {
      this.users = this.users.filter((user) => user.id !== id);
      return of(void 0);
    }

    return this.http.delete<void>(`${this.endpoint}/${id}`);
  }

  private nextId(): number {
    return Math.max(...this.users.map((user) => user.id), 0) + 1;
  }
}
