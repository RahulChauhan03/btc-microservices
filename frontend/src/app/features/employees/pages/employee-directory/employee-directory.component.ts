import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { debounceTime, distinctUntilChanged, merge } from 'rxjs';

import { User } from '../../../../core/models/domain.models';
import { UserService } from '../../../../core/services/user.service';
import { BtcLoaderComponent } from '../../../../shared/components/btc-loader/btc-loader.component';
import { DataTableColumn, DataTableComponent, DataTablePageChange } from '../../../../shared/components/data-table/data-table.component';

/** Administrator-only directory; search and filtering run in user-service (paged). */
@Component({
  selector: 'app-employee-directory',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, BtcLoaderComponent, DataTableComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './employee-directory.component.html',
})
export class EmployeeDirectoryComponent {
  private readonly users = inject(UserService);
  private readonly destroyRef = inject(DestroyRef);

  readonly search = new FormControl('', { nonNullable: true });
  readonly role = new FormControl('', { nonNullable: true });
  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly rows = signal<User[]>([]);
  readonly total = signal(0);
  readonly page = signal(0);
  readonly pageSize = signal(20);
  readonly tableColumns: DataTableColumn<User>[] = [
    { key: 'name', header: 'Name', type: 'link', link: (user) => ['/employees', user.id] },
    { key: 'email', header: 'Email', mobilePriority: 'secondary' },
    { key: 'role', header: 'Role', type: 'chip', value: (user) => this.roleLabel(user.role) },
    { key: 'createdAt', header: 'Joined', type: 'date', mobilePriority: 'secondary' },
  ];

  constructor() {
    merge(this.search.valueChanges.pipe(debounceTime(300), distinctUntilChanged()), this.role.valueChanges)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.load(0));
    this.load(0);
  }

  load(page = this.page(), pageSize = this.pageSize()): void {
    this.state.set('loading');
    this.users
      .listUsers({ q: this.search.value.trim(), role: this.role.value, page, size: pageSize, sort: 'name,asc' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          const lastPage = Math.max(0, Math.ceil(result.total / pageSize) - 1);
          if (page > lastPage) {
            this.load(lastPage, pageSize);
            return;
          }
          this.rows.set(result.items);
          this.total.set(result.total);
          this.page.set(page);
          this.pageSize.set(pageSize);
          this.state.set('ready');
        },
        error: () => this.state.set('error'),
      });
  }

    onTablePageChange(event: DataTablePageChange): void {
      this.load(event.pageIndex, event.pageSize);
    }

  roleLabel(role: string): string {
    return role === 'ADMIN' ? 'Administrator' : 'Employee';
  }
}
