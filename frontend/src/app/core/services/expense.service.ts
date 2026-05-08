import { Injectable } from '@angular/core';

import { environment } from '../../../environments/environment';
import { Expense, ExpensePayload } from '../models/domain.models';
import { ApiError, ApiSuccess, runRequest } from './api-callbacks';
import { CrudHttpService } from './crud-http.service';

@Injectable({ providedIn: 'root' })
export class ExpenseService {
  constructor(private readonly crudHttp: CrudHttpService) {}

  private readonly endpoint = `${environment.apiBaseUrl}/expenses`;

  getExpenses(onSuccess?: ApiSuccess<Expense[]>, onError?: ApiError): void {
    runRequest(this.crudHttp.list<Expense>(this.endpoint), onSuccess, onError);
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
