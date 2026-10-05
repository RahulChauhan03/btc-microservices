import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
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

const PAGE_SIZE = 20;

/** Administrator-only directory; search and filtering run in user-service (paged). */
@Component({
  selector: 'app-employee-directory',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, BtcLoaderComponent],
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
  readonly pages = computed(() => Math.max(1, Math.ceil(this.total() / PAGE_SIZE)));

  constructor() {
    merge(this.search.valueChanges.pipe(debounceTime(300), distinctUntilChanged()), this.role.valueChanges)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.load(0));
    this.load(0);
  }

  load(page = this.page()): void {
    this.state.set('loading');
    this.users
      .listUsers({ q: this.search.value.trim(), role: this.role.value, page, size: PAGE_SIZE, sort: 'name,asc' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.rows.set(result.items);
          this.total.set(result.total);
          this.page.set(page);
          this.state.set('ready');
        },
        error: () => this.state.set('error'),
      });
  }

  roleLabel(role: string): string {
    return role === 'ADMIN' ? 'Administrator' : 'Employee';
  }
}
