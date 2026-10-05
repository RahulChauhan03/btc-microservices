import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { TOAST_MESSAGES } from '../../../../core/constants/toast-messages';
import { describeHttpError } from '../../../../core/http/describe-error';
import { Expense, ExpensePayload, Trip } from '../../../../core/models/domain.models';
import { AuthService } from '../../../../core/services/auth.service';
import { ConfirmationService } from '../../../../core/services/confirmation.service';
import { ExpenseService } from '../../../../core/services/expense.service';
import { ToastService } from '../../../../core/services/toast.service';
import { TripService } from '../../../../core/services/trip.service';
import {
  DataTableAction,
  DataTableColumn,
  DataTableComponent,
  DataTablePageChange,
} from '../../../../shared/components/data-table/data-table.component';
import { StatCardComponent } from '../../../../shared/components/stat-card/stat-card.component';
import { DatepickerHeaderComponent } from '../../../../shared/components/datepicker-header/datepicker-header.component';

@Component({
  selector: 'app-expense-management',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    RouterLink,
    DataTableComponent,
    StatCardComponent,
  ],
  templateUrl: './expense-management.component.html',
  styleUrl: './expense-management.component.css',
})
export class ExpenseManagementComponent {
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly expenseService = inject(ExpenseService);
  private readonly tripService = inject(TripService);
  private readonly authService = inject(AuthService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly toastService = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly calendarHeaderComponent = DatepickerHeaderComponent;

  readonly expenses = signal<Expense[]>([]);
  readonly tableTotal = signal(0);
  readonly tablePage = signal(0);
  readonly tablePageSize = signal(20);
  readonly tableLoading = signal(false);
  readonly tableError = signal<string | null>(null);
  readonly totalExpenses = signal(0);
  /** The caller's own trips: an expense can only be linked to one of these (enforced by the backend). */
  readonly ownTrips = signal<Trip[]>([]);
  readonly editingExpenseId = signal<number | null>(null);
  readonly isFormPage = signal(false);
  private requestedEditId: number | null = null;
  readonly tableColumns: DataTableColumn<Expense>[] = [
    { key: 'title', header: 'Title' },
    { key: 'category', header: 'Category', mobilePriority: 'secondary' },
    { key: 'expenseDate', header: 'Date', type: 'date', mobilePriority: 'secondary' },
    { key: 'amount', header: 'Amount', type: 'currency' },
    { key: 'description', header: 'Description', mobilePriority: 'secondary' },
  ];
  readonly tableActions: DataTableAction<Expense>[] = [
    // Only owners may change an expense, and not while a claim covers it. The backend enforces both.
    {
      id: 'edit',
      label: 'Edit',
      icon: 'edit',
      handler: (expense) => this.editExpense(expense),
      visible: (expense) => this.isMine(expense) && expense.claimId === null,
    },
    {
      id: 'delete',
      label: 'Delete',
      icon: 'delete',
      handler: (expense) => this.deleteExpense(expense),
      visible: (expense) => this.isMine(expense) && expense.claimId === null,
    },
  ];

  readonly expenseForm = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.minLength(3)]],
    category: ['TRAVEL', Validators.required],
    amount: [0, [Validators.required, Validators.min(1)]],
    expenseDate: ['' as string | Date, Validators.required],
    description: ['', [Validators.required, Validators.minLength(5)]],
    tripId: [null as number | null],
  });

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = Number(params.get('id'));
      this.isFormPage.set(this.router.url.includes('/new') || this.router.url.includes('/edit/'));
      this.editingExpenseId.set(Number.isFinite(id) && id > 0 ? id : null);
      this.patchEditingExpense();
      if (this.isFormPage()) {
        this.loadFormData();
      } else {
        this.loadData(0);
      }
    });
  }

  submit(): void {
    if (this.expenseForm.invalid) {
      this.expenseForm.markAllAsTouched();
      return;
    }

    const formValue = this.expenseForm.getRawValue();
    const payload: ExpensePayload = {
      title: formValue.title,
      category: formValue.category,
      amount: formValue.amount,
      expenseDate: this.toDateString(formValue.expenseDate),
      description: formValue.description,
      tripId: formValue.tripId,
    };
    const onSaved = () => {
      this.toastService.success(
        this.editingExpenseId() ? TOAST_MESSAGES.expenses.updated : TOAST_MESSAGES.expenses.created,
      );
      this.resetForm();
      this.router.navigate(['/expenses']);
    };

    if (this.editingExpenseId()) {
      this.expenseService.updateExpense(this.editingExpenseId() as number, payload, onSaved);
      return;
    }

    this.expenseService.createExpense(payload, onSaved);
  }

  private isMine(expense: Expense): boolean {
    return expense.ownerId !== null && expense.ownerId === this.authService.currentUser()?.id;
  }

  editExpense(expense: Expense): void {
    this.router.navigate(['/expenses/edit', expense.id]);
  }

  private patchEditingExpense(): void {
    const expense = this.expenses().find((item) => item.id === this.editingExpenseId());

    if (!expense) {
      if (this.isFormPage() && !this.editingExpenseId()) {
        this.resetForm();
      } else if (this.isFormPage() && this.editingExpenseId() && this.requestedEditId !== this.editingExpenseId()) {
        const id = this.editingExpenseId()!;
        this.requestedEditId = id;
        this.expenseService.getExpense(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
          next: (editingExpense) => this.patchForm(editingExpense),
        });
      }
      return;
    }

    this.patchForm(expense);
  }

  private patchForm(expense: Expense): void {
    this.expenseForm.patchValue({
      title: expense.title,
      category: expense.category,
      amount: expense.amount,
      expenseDate: this.toDate(expense.expenseDate) ?? '',
      description: expense.description,
      tripId: expense.tripId,
    });
  }

  viewExpense(expense: Expense): void {
    this.toastService.info(TOAST_MESSAGES.expenses.viewed(expense));
  }

  deleteExpense(expense: Expense): void {
    this.confirmationService
      .confirmDelete({
        tableName: 'Expenses',
        columnName: 'Title',
        value: expense.title,
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
        this.loadData(this.tablePage(), this.tablePageSize());
      });
      });
  }

  resetForm(): void {
    this.expenseForm.reset({
      title: '',
      category: 'TRAVEL',
      amount: 0,
      expenseDate: '',
      description: '',
      tripId: null,
    });
  }

  private loadFormData(): void {
    this.tripService.getTrips(
      (trips) => this.ownTrips.set(trips.filter((trip) => trip.ownerId === this.authService.currentUser()?.id)),
      () => this.ownTrips.set([]),
    );
  }

  loadData(page = this.tablePage(), size = this.tablePageSize()): void {
    this.loadExpensePage(page, size);
    this.expenseService.getSummary().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (summary) => this.totalExpenses.set(summary.total),
      error: () => this.totalExpenses.set(0),
    });
  }

  private loadExpensePage(page: number, size: number): void {
    this.tableLoading.set(true);
    this.tableError.set(null);
    this.expenseService
      .listExpenses({ page, size, sort: 'expenseDate,desc' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          const lastPage = Math.max(0, Math.ceil(result.total / size) - 1);
          if (page > lastPage) {
            this.loadExpensePage(lastPage, size);
            return;
          }
          this.expenses.set(result.items);
          this.tableTotal.set(result.total);
          this.tablePage.set(page);
          this.tablePageSize.set(size);
          this.tableLoading.set(false);
        },
        error: (error: unknown) => {
          this.tableError.set(describeHttpError(error, 'expense service'));
          this.tableLoading.set(false);
        },
      });
  }

  onTablePageChange(event: DataTablePageChange): void {
    this.loadExpensePage(event.pageIndex, event.pageSize);
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
