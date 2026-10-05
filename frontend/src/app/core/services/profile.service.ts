import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { inlineFeedbackRequest } from '../http/request-context';
import { User } from '../models/domain.models';
import { AuthService } from './auth.service';
import { CrudHttpService } from './crud-http.service';

/** The signed-in user's own profile (user-service /users/me: identity comes from the token). */
@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);
  private readonly crudHttp = inject(CrudHttpService);
  private readonly auth = inject(AuthService);
  private readonly endpoint = `${environment.apiBaseUrl}/users/me`;

  me(): Observable<User> {
    return this.crudHttp.fetch<User>(this.endpoint);
  }

  update(name: string, phone: string): Observable<User> {
    return this.http
      .put<User>(this.endpoint, { name, phone }, { context: inlineFeedbackRequest() })
      .pipe(tap((user) => this.auth.updateCurrentUserName(user.name)));
  }

  changePassword(currentPassword: string, newPassword: string, confirmPassword: string): Observable<void> {
    return this.http.post<void>(`${this.endpoint}/password`, { currentPassword, newPassword, confirmPassword }, {
      context: inlineFeedbackRequest(),
    });
  }
}
