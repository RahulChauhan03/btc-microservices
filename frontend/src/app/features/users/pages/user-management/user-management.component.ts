import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { TOAST_MESSAGES } from '../../../../core/constants/toast-messages';
import { User, UserPayload } from '../../../../core/models/domain.models';
import { ConfirmationService } from '../../../../core/services/confirmation.service';
import { ToastService } from '../../../../core/services/toast.service';
import { UserService } from '../../../../core/services/user.service';
import {
  DataTableAction,
  DataTableColumn,
  DataTableComponent,
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
  readonly tableColumns: DataTableColumn<User>[] = [
    { key: 'name', header: 'Name' },
    { key: 'email', header: 'Email' },
    { key: 'phone', header: 'Phone' },
    { key: 'createdAt', header: 'Created', type: 'date' },
  ];
  readonly tableActions: DataTableAction<User>[] = [
    { id: 'view', label: 'View', icon: 'visibility', handler: (user) => this.viewUser(user) },
    { id: 'edit', label: 'Edit', icon: 'edit', handler: (user) => this.editUser(user) },
    { id: 'delete', label: 'Delete', icon: 'delete', handler: (user) => this.deleteUser(user) },
  ];
  readonly userForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', Validators.required],
    password: ['', [Validators.minLength(8)]],
  });

  constructor() {
    this.loadUsers();
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
      phone: user.phone,
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
    this.editingUserId.set(null);
    this.userForm.reset({
      name: '',
      email: '',
      phone: '',
      password: '',
    });
  }

  private loadUsers(): void {
    this.userService.getUsers((users) => this.users.set(users));
  }
}
