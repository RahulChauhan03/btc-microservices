import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { TOAST_MESSAGES } from '../../../../core/constants/toast-messages';
import { describeHttpError } from '../../../../core/http/describe-error';
import { User, UserPayload, UserRole } from '../../../../core/models/domain.models';
import { ConfirmationService } from '../../../../core/services/confirmation.service';
import { ToastService } from '../../../../core/services/toast.service';
import { UserService } from '../../../../core/services/user.service';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import {
  DataTableAction,
  DataTableColumn,
  DataTableComponent,
  DataTablePageChange,
} from '../../../../shared/components/data-table/data-table.component';

@Component({
  selector: 'app-user-management',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    RouterLink,
    DataTableComponent,
  ],
  templateUrl: './user-management.component.html',
  styleUrl: './user-management.component.css',
})
export class UserManagementComponent {
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly userService = inject(UserService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly toastService = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly users = signal<User[]>([]);
  readonly tableTotal = signal(0);
  readonly tablePage = signal(0);
  readonly tablePageSize = signal(20);
  readonly tableLoading = signal(false);
  readonly tableError = signal<string | null>(null);
  private readonly tableSearch = new Subject<string>();
  private searchQuery = '';
  readonly editingUserId = signal<number | null>(null);
  readonly isFormPage = signal(false);
  private requestedEditId: number | null = null;
  readonly roleOptions: { value: UserRole; label: string }[] = [
    { value: 'ADMIN', label: 'Admin' },
    { value: 'EMPLOYEE', label: 'Employee' },
  ];
  readonly tableColumns: DataTableColumn<User>[] = [
    { key: 'name', header: 'Name' },
    { key: 'email', header: 'Email' },
    { key: 'phone', header: 'Phone', mobilePriority: 'secondary' },
    { key: 'role', header: 'Role', type: 'chip' },
    { key: 'createdAt', header: 'Created', type: 'date', mobilePriority: 'secondary' },
  ];
  readonly tableActions: DataTableAction<User>[] = [
    { id: 'edit', label: 'Edit', icon: 'edit', handler: (user) => this.editUser(user) },
    { id: 'delete', label: 'Delete', icon: 'delete', handler: (user) => this.deleteUser(user) },
  ];
  readonly userForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', Validators.required],
    role: ['EMPLOYEE' as UserRole, Validators.required],
    password: ['', [Validators.minLength(8)]],
  });

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = Number(params.get('id'));
      this.isFormPage.set(this.router.url.includes('/new') || this.router.url.includes('/edit/'));
      this.editingUserId.set(Number.isFinite(id) && id > 0 ? id : null);
      this.patchEditingUser();
    });
    if (!this.isFormPage()) {
      this.loadUsers(0);
    }
    this.tableSearch
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((query) => {
        this.searchQuery = query.trim();
        this.loadUsers(0);
      });
  }

  submit(): void {
    if (this.userForm.invalid) {
      this.userForm.markAllAsTouched();
      return;
    }

    const formValue = this.userForm.getRawValue();
    const payload: UserPayload = {
      name: formValue.name,
      email: formValue.email,
      phone: formValue.phone,
      role: formValue.role,
      ...(formValue.password ? { password: formValue.password } : {}),
    };

    if (!this.editingUserId() && !payload.password) {
      this.userForm.controls.password.setErrors({ required: true });
      this.userForm.controls.password.markAsTouched();
      return;
    }

    const onSaved = () => {
      this.toastService.success(this.editingUserId() ? TOAST_MESSAGES.users.updated : TOAST_MESSAGES.users.created);
      this.resetForm();
      this.loadUsers();
      this.router.navigate(['/users']);
    };

    if (this.editingUserId()) {
      this.userService.updateUser(this.editingUserId() as number, payload, onSaved);
      return;
    }

    this.userService.createUser(payload, onSaved);
  }

  editUser(user: User): void {
    this.router.navigate(['/users/edit', user.id]);
  }

  private patchEditingUser(): void {
    const user = this.users().find((item) => item.id === this.editingUserId());

    if (!user) {
      if (this.isFormPage() && !this.editingUserId()) {
        this.resetForm();
      } else if (this.isFormPage() && this.editingUserId() && this.requestedEditId !== this.editingUserId()) {
        const id = this.editingUserId()!;
        this.requestedEditId = id;
        this.userService.getUser(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
          next: (editingUser) => this.patchForm(editingUser),
        });
      }
      return;
    }

    this.patchForm(user);
  }

  private patchForm(user: User): void {
    this.userForm.patchValue({
      name: user.name,
      email: user.email,
      phone: user.phone,
      role: user.role,
      password: '',
    });
  }

  viewUser(user: User): void {
    this.toastService.info(TOAST_MESSAGES.users.viewed(user));
  }

  deleteUser(user: User): void {
    this.confirmationService
      .confirmDelete({
        tableName: 'User Directory',
        columnName: 'Name',
        value: user.name,
      })
      .subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }

      this.userService.deleteUser(user.id, () => {
        this.toastService.success(TOAST_MESSAGES.users.deleted);
        if (this.editingUserId() === user.id) {
          this.resetForm();
        }
        this.loadUsers();
      });
      });
  }

  resetForm(): void {
    this.userForm.reset({
      name: '',
      email: '',
      phone: '',
      role: 'EMPLOYEE',
      password: '',
    });
  }

  loadUsers(page = this.tablePage(), size = this.tablePageSize()): void {
    this.tableLoading.set(true);
    this.tableError.set(null);
    this.userService
      .listUsers({ q: this.searchQuery, page, size, sort: 'name,asc' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          const lastPage = Math.max(0, Math.ceil(result.total / size) - 1);
          if (page > lastPage) {
            this.loadUsers(lastPage, size);
            return;
          }
          this.users.set(result.items);
          this.tableTotal.set(result.total);
          this.tablePage.set(page);
          this.tablePageSize.set(size);
          this.tableLoading.set(false);
          this.patchEditingUser();
        },
        error: (error: unknown) => {
          this.tableError.set(describeHttpError(error, 'user service'));
          this.tableLoading.set(false);
        },
      });
  }

  onTablePageChange(event: DataTablePageChange): void {
    this.loadUsers(event.pageIndex, event.pageSize);
  }

  onTableSearchChange(query: string): void {
    this.tableSearch.next(query);
  }
}
