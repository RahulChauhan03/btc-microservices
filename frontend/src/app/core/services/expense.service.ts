import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { MOCK_EXPENSES } from '../data/mock-data';
import { Expense, ExpensePayload } from '../models/domain.models';
import { ApiError, ApiSuccess, runRequest } from './api-callbacks';

@Injectable({ providedIn: 'root' })
export class ExpenseService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${environment.apiBaseUrl}/expenses`;
  private readonly useMockData = environment.useMockData;
  private expenses: Expense[] = structuredClone(MOCK_EXPENSES);

  getExpenses(onSuccess?: ApiSuccess<Expense[]>, onError?: ApiError): void {
    runRequest(this.getExpensesRequest(), onSuccess, onError);
  }

  createExpense(payload: ExpensePayload, onSuccess?: ApiSuccess<Expense>, onError?: ApiError): void {
    runRequest(this.createExpenseRequest(payload), onSuccess, onError);
  }

  updateExpense(id: number, payload: ExpensePayload, onSuccess?: ApiSuccess<Expense>, onError?: ApiError): void {
    runRequest(this.updateExpenseRequest(id, payload), onSuccess, onError);
  }

  deleteExpense(id: number, onSuccess?: ApiSuccess<void>, onError?: ApiError): void {
    runRequest(this.deleteExpenseRequest(id), onSuccess, onError);
  }

  private getExpensesRequest(): Observable<Expense[]> {
    if (this.useMockData) {
      return of(structuredClone(this.expenses));
    }

    return this.http.get<Expense[]>(this.endpoint);
  }

  private createExpenseRequest(payload: ExpensePayload): Observable<Expense> {
    if (this.useMockData) {
      const expense: Expense = {
        id: this.nextId(),
        ...payload,
        category: payload.category as Expense['category'],
      };
      this.expenses = [expense, ...this.expenses];
      return of(structuredClone(expense));
    }

    return this.http.post<Expense>(this.endpoint, payload);
  }

  private updateExpenseRequest(id: number, payload: ExpensePayload): Observable<Expense> {
    if (this.useMockData) {
      const updatedExpense: Expense = {
        id,
        ...payload,
        category: payload.category as Expense['category'],
      };
      this.expenses = this.expenses.map((expense) => (expense.id === id ? updatedExpense : expense));
      return of(structuredClone(updatedExpense));
    }

    return this.http.put<Expense>(`${this.endpoint}/${id}`, payload);
  }

  private deleteExpenseRequest(id: number): Observable<void> {
    if (this.useMockData) {
      this.expenses = this.expenses.filter((expense) => expense.id !== id);
      return of(void 0);
    }

    return this.http.delete<void>(`${this.endpoint}/${id}`);
  }

  private nextId(): number {
    return Math.max(...this.expenses.map((expense) => expense.id), 200) + 1;
  }
}
