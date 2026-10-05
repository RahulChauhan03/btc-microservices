import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Expense, ExpensePayload, ExpenseSummary, PagedResult } from '../models/domain.models';
import { ApiError, ApiSuccess, runRequest } from './api-callbacks';
import { CrudHttpService, QueryParams } from './crud-http.service';

@Injectable({ providedIn: 'root' })
export class ExpenseService {
  constructor(private readonly crudHttp: CrudHttpService) {}

  private readonly endpoint = `${environment.apiBaseUrl}/expenses`;

  /** Filters: tripId, ownerId (administrators), category, from, to, page, size, sort. */
  listExpenses(params: QueryParams): Observable<PagedResult<Expense>> {
    return this.crudHttp.page<Expense>(this.endpoint, params);
  }

  getExpense(id: number): Observable<Expense> {
    return this.crudHttp.fetch<Expense>(`${this.endpoint}/${id}`);
  }

  /** Totals by category and month, computed by expense-service; params: ownerId, tripId, from, to. */
  getSummary(params: QueryParams = {}): Observable<ExpenseSummary> {
    return this.crudHttp.fetch<ExpenseSummary>(`${this.endpoint}/summary`, params);
  }

  getExpenses(onSuccess?: ApiSuccess<Expense[]>, onError?: ApiError): void {
    runRequest(this.crudHttp.list<Expense>(this.endpoint, 'Loading expenses…'), onSuccess, onError);
  }

  createExpense(payload: ExpensePayload, onSuccess?: ApiSuccess<Expense>, onError?: ApiError): void {
    runRequest(this.crudHttp.create<Expense, ExpensePayload>(this.endpoint, payload), onSuccess, onError);
  }

  updateExpense(id: number, payload: ExpensePayload, onSuccess?: ApiSuccess<Expense>, onError?: ApiError): void {
    runRequest(this.crudHttp.update<Expense, ExpensePayload>(this.endpoint, id, payload), onSuccess, onError);
  }

  deleteExpense(id: number, onSuccess?: ApiSuccess<void>, onError?: ApiError): void {
    runRequest(this.crudHttp.delete(this.endpoint, id), onSuccess, onError);
  }
}
