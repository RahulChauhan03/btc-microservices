import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { TOAST_MESSAGES } from '../../../../core/constants/toast-messages';
import { User } from '../../../../core/models/domain.models';
import { ConfirmationService } from '../../../../core/services/confirmation.service';
import { ToastService } from '../../../../core/services/toast.service';
import { UserService } from '../../../../core/services/user.service';
import {
  DataTableAction,
  DataTableColumn,
  DataTableComponent,
  DataTableFilter,
} from '../../../../shared/components/data-table/data-table.component';

@Component({
  selector: 'app-user-management',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    DataTableComponent,
  ],
  templateUrl: './user-management.component.html',
  styleUrl: './user-management.component.css',
})
export class UserManagementComponent {
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly toastService = inject(ToastService);

  readonly users = signal<User[]>([]);
  readonly editingUserId = signal<number | null>(null);
  readonly roleFilter = signal<User['role'] | 'ALL'>('ALL');
  readonly tableColumns: DataTableColumn<User>[] = [
    { key: 'name', header: 'Name' },
    { key: 'email', header: 'Email' },
    { key: 'department', header: 'Department' },
    {
      key: 'role',
      header: 'Role',
      type: 'select',
      options: [
        { label: 'Admin', value: 'ADMIN' },
        { label: 'Manager', value: 'MANAGER' },
        { label: 'Employee', value: 'EMPLOYEE' },
      ],
    },
    { key: 'status', header: 'Status', type: 'chip' },
  ];
  readonly tableActions: DataTableAction<User>[] = [
    { id: 'view', label: 'View', icon: 'visibility', handler: (user) => this.viewUser(user) },
    { id: 'edit', label: 'Edit', icon: 'edit', handler: (user) => this.editUser(user) },
    { id: 'delete', label: 'Delete', icon: 'delete', handler: (user) => this.deleteUser(user) },
  ];
  readonly roleFilters = computed<DataTableFilter[]>(() => [
    {
      key: 'role',
      label: 'Role',
      value: this.roleFilter(),
      options: [
        { label: 'All roles', value: 'ALL' },
        { label: 'Admin', value: 'ADMIN' },
        { label: 'Manager', value: 'MANAGER' },
        { label: 'Employee', value: 'EMPLOYEE' },
      ],
    },
  ]);

  readonly userForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    department: ['', Validators.required],
    role: ['EMPLOYEE' as User['role'], Validators.required],
    status: ['ACTIVE' as User['status'], Validators.required],
  });

  readonly filteredUsers = computed(() => {
    const role = this.roleFilter();

    return this.users().filter((user) => {
      return role === 'ALL' || user.role === role;
    });
  });

  updateFilter(event: { key: string; value: string }): void {
    if (event.key === 'role') {
      this.roleFilter.set(event.value as User['role'] | 'ALL');
    }
  }

  updateCell(event: { row: User; column: DataTableColumn<User>; value: string }): void {
    if (event.column.key !== 'role' || event.row.role === event.value) {
      return;
    }

    const payload: Omit<User, 'id'> = {
      name: event.row.name,
      email: event.row.email,
      department: event.row.department,
      role: event.value as User['role'],
      status: event.row.status,
    };

    this.userService.updateUser(event.row.id, payload, () => {
      this.toastService.success(TOAST_MESSAGES.users.roleUpdated);
      this.loadUsers();
    });
  }

  constructor() {
    this.loadUsers();
  }

  submit(): void {
    if (this.userForm.invalid) {
      this.userForm.markAllAsTouched();
      return;
    }

    const payload = this.userForm.getRawValue();
    const onSaved = () => {
      this.toastService.success(this.editingUserId() ? TOAST_MESSAGES.users.updated : TOAST_MESSAGES.users.created);
      this.resetForm();
      this.loadUsers();
    };

    if (this.editingUserId()) {
      this.userService.updateUser(this.editingUserId() as number, payload, onSaved);
      return;
    }

    this.userService.createUser(payload, onSaved);
  }

  editUser(user: User): void {
    this.editingUserId.set(user.id);
    this.userForm.patchValue({
      name: user.name,
      email: user.email,
      department: user.department,
      role: user.role,
      status: user.status,
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
    this.editingUserId.set(null);
    this.userForm.reset({
      name: '',
      email: '',
      department: '',
      role: 'EMPLOYEE',
      status: 'ACTIVE',
    });
  }

  private loadUsers(): void {
    this.userService.getUsers((users) => this.users.set(users));
  }
}
