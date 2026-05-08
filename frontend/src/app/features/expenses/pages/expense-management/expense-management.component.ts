import { CommonModule, CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { TOAST_MESSAGES } from '../../../../core/constants/toast-messages';
import { Expense, ExpensePayload, Trip } from '../../../../core/models/domain.models';
import { ConfirmationService } from '../../../../core/services/confirmation.service';
import { ExpenseService } from '../../../../core/services/expense.service';
import { ToastService } from '../../../../core/services/toast.service';
import { TripService } from '../../../../core/services/trip.service';
import {
  DataTableAction,
  DataTableColumn,
  DataTableComponent,
} from '../../../../shared/components/data-table/data-table.component';
import { DatepickerHeaderComponent } from '../../../../shared/components/datepicker-header/datepicker-header.component';

@Component({
  selector: 'app-expense-management',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    CurrencyPipe,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    DataTableComponent,
  ],
  templateUrl: './expense-management.component.html',
  styleUrl: './expense-management.component.css',
})
export class ExpenseManagementComponent {
  private readonly fb = inject(FormBuilder);
  private readonly tripService = inject(TripService);
  private readonly expenseService = inject(ExpenseService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly toastService = inject(ToastService);
  readonly calendarHeaderComponent = DatepickerHeaderComponent;

  readonly trips = signal<Trip[]>([]);
  readonly expenses = signal<Expense[]>([]);
  readonly editingExpenseId = signal<number | null>(null);
  readonly totalExpenses = computed(() =>
    this.expenses().reduce((sum, expense) => sum + Number(expense.amount ?? 0), 0),
  );
  readonly tableColumns: DataTableColumn<Expense>[] = [
    { key: 'trip', header: 'Trip', value: (expense) => this.resolveTripName(expense.tripId) },
    { key: 'category', header: 'Category' },
    { key: 'merchant', header: 'Merchant' },
    { key: 'expenseDate', header: 'Date', type: 'date' },
    { key: 'amount', header: 'Amount', type: 'currency' },
    { key: 'reimbursable', header: 'Reimbursable', type: 'boolean' },
  ];
  readonly tableActions: DataTableAction<Expense>[] = [
    { id: 'view', label: 'View', icon: 'visibility', handler: (expense) => this.viewExpense(expense) },
    { id: 'edit', label: 'Edit', icon: 'edit', handler: (expense) => this.editExpense(expense) },
    { id: 'delete', label: 'Delete', icon: 'delete', handler: (expense) => this.deleteExpense(expense) },
  ];

  readonly expenseForm = this.fb.nonNullable.group({
    tripId: [0, [Validators.required, Validators.min(1)]],
    category: ['TRAVEL', Validators.required],
    amount: [0, [Validators.required, Validators.min(1)]],
    expenseDate: ['' as string | Date, Validators.required],
    merchant: ['', Validators.required],
    description: ['', [Validators.required, Validators.minLength(5)]],
    reimbursable: [true],
  });

  constructor() {
    this.loadData();
  }

  submit(): void {
    if (this.expenseForm.invalid) {
      this.expenseForm.markAllAsTouched();
      return;
    }

    const formValue = this.expenseForm.getRawValue();
    const payload: ExpensePayload = {
      tripId: formValue.tripId,
      category: formValue.category,
      amount: formValue.amount,
      expenseDate: this.toDateString(formValue.expenseDate),
      merchant: formValue.merchant,
      description: formValue.description,
      reimbursable: formValue.reimbursable,
    };
    const onSaved = () => {
      this.toastService.success(
        this.editingExpenseId() ? TOAST_MESSAGES.expenses.updated : TOAST_MESSAGES.expenses.created,
      );
      this.resetForm();
      this.loadData();
    };

    if (this.editingExpenseId()) {
      this.expenseService.updateExpense(this.editingExpenseId() as number, payload, onSaved);
      return;
    }

    this.expenseService.createExpense(payload, onSaved);
  }

  editExpense(expense: Expense): void {
    this.editingExpenseId.set(expense.id);
    this.expenseForm.patchValue({
      tripId: expense.tripId,
      category: expense.category,
      amount: expense.amount,
      expenseDate: this.toDate(expense.expenseDate) ?? '',
      merchant: expense.merchant,
      description: expense.description,
      reimbursable: expense.reimbursable,
    });
  }

  viewExpense(expense: Expense): void {
    this.toastService.info(TOAST_MESSAGES.expenses.viewed(expense, this.resolveTripName(expense.tripId)));
  }

  deleteExpense(expense: Expense): void {
    this.confirmationService
      .confirmDelete({
        tableName: 'Expenses',
        columnName: 'Merchant',
        value: expense.merchant,
      })
      .subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }

      this.expenseService.deleteExpense(expense.id, () => {
        this.toastService.success(TOAST_MESSAGES.expenses.deleted);
        if (this.editingExpenseId() === expense.id) {
          this.resetForm();
        }
        this.loadData();
      });
      });
  }

  resolveTripName(tripId: number): string {
    return this.trips().find((trip) => trip.id === tripId)?.destination ?? `Trip #${tripId}`;
  }

  resetForm(): void {
    this.editingExpenseId.set(null);
    this.expenseForm.reset({
      tripId: 0,
      category: 'TRAVEL',
      amount: 0,
      expenseDate: '',
      merchant: '',
      description: '',
      reimbursable: true,
    });
  }

  private loadData(): void {
    this.tripService.getTrips(
      (trips) => this.trips.set(trips),
      () => this.trips.set([]),
    );
    this.expenseService.getExpenses(
      (expenses) => this.expenses.set(expenses),
      () => this.expenses.set([]),
    );
  }

  private toDate(value: string | Date | null | undefined): Date | null {
    if (!value) {
      return null;
    }

    const date = value instanceof Date ? value : new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  private toDateString(value: string | Date): string {
    const date = this.toDate(value);

    if (!date) {
      return '';
    }

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
