import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';

import { AppNotification } from '../../../../core/models/domain.models';
import { describeHttpError } from '../../../../core/http/describe-error';
import { NotificationService } from '../../../../core/services/notification.service';
import { ToastService } from '../../../../core/services/toast.service';
import { BtcLoaderComponent } from '../../../../shared/components/btc-loader/btc-loader.component';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-notifications',
  imports: [DatePipe, MatButtonModule, MatButtonToggleModule, MatIconModule, BtcLoaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.css',
})
export class NotificationsComponent {
  private readonly notifications = inject(NotificationService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly items = signal<AppNotification[]>([]);
  readonly total = signal(0);
  readonly unreadOnly = signal(false);
  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly loadingMore = signal(false);
  readonly loadError = signal('');
  readonly unread = this.notifications.unread;
  private page = 0;

  constructor() {
    this.load();
  }

  setFilter(unreadOnly: boolean): void {
    this.unreadOnly.set(unreadOnly);
    this.load();
  }

  load(): void {
    this.page = 0;
    this.state.set('loading');
    this.fetch(false);
    this.notifications.refreshUnreadCount().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ error: () => undefined });
  }

  loadMore(): void {
    this.page++;
    this.loadingMore.set(true);
    this.fetch(true);
  }

  open(item: AppNotification): void {
    const go = () => (item.link ? this.router.navigateByUrl(item.link) : undefined);
    if (item.read) {
      go();
      return;
    }
    this.notifications.markRead(item.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.items.update((list) => list.map((n) => (n.id === item.id ? { ...n, read: true } : n)));
        go();
      },
      error: () => this.toast.error('Could not mark the notification as read. Please try again.'),
    });
  }

  markAllRead(): void {
    this.notifications.markAllRead().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.items.update((list) => list.map((n) => ({ ...n, read: true })));
        if (this.unreadOnly()) {
          this.load();
        }
        this.toast.success('All notifications marked as read.');
      },
      error: () => this.toast.error('Could not mark notifications as read. Please try again.'),
    });
  }

  icon(type: string): string {
    switch (type) {
      case 'CLAIM_APPROVED':
        return 'task_alt';
      case 'CLAIM_REJECTED':
        return 'cancel';
      case 'CLAIM_SUBMITTED':
        return 'pending_actions';
      case 'REIMBURSEMENT_UPDATED':
        return 'payments';
      default:
        return 'notifications';
    }
  }

  private fetch(append: boolean): void {
    this.notifications
      .list({ unreadOnly: this.unreadOnly(), page: this.page, size: PAGE_SIZE })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.items.update((list) => (append ? [...list, ...result.items] : result.items));
          this.total.set(result.total);
          this.state.set('ready');
          this.loadingMore.set(false);
        },
        error: (error: unknown) => {
          this.loadingMore.set(false);
          this.loadError.set(describeHttpError(error, 'notification service'));
          if (append) {
            this.page--;
            this.toast.error('Could not load more notifications.');
          } else {
            this.state.set('error');
          }
        },
      });
  }
}
