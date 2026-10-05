import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { EMPTY, catchError, filter, map, switchMap, timer } from 'rxjs';

import { AuthService } from '../core/services/auth.service';
import { NotificationService } from '../core/services/notification.service';
import { BrandMarkComponent } from '../shared/components/brand-mark/brand-mark.component';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  adminOnly?: boolean;
}

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatMenuModule,
    MatSidenavModule,
    BrandMarkComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.css',
})
export class AppShellComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  /** Unread notifications for the bell; refreshed quietly every minute while signed in. */
  readonly unread = this.notifications.unread;

  private readonly handset = toSignal(inject(BreakpointObserver).observe('(max-width: 960px)').pipe(map((s) => s.matches)), {
    initialValue: false,
  });
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  private readonly navItems: NavItem[] = [
    { label: 'Dashboard', icon: 'space_dashboard', route: '/dashboard' },
    { label: 'Trips', icon: 'flight_takeoff', route: '/trips' },
    { label: 'Expenses', icon: 'receipt_long', route: '/expenses' },
    { label: 'Claims', icon: 'fact_check', route: '/claims' },
    { label: 'Reimbursements', icon: 'payments', route: '/reimbursements' },
    { label: 'Travel policy', icon: 'policy', route: '/policy' },
    { label: 'Employees', icon: 'badge', route: '/employees', adminOnly: true },
    { label: 'Reports', icon: 'insights', route: '/reports', adminOnly: true },
    { label: 'Users', icon: 'manage_accounts', route: '/users', adminOnly: true },
    { label: 'Audit log', icon: 'history', route: '/audit', adminOnly: true },
  ];

  readonly user = this.authService.currentUser;
  readonly isMobile = this.handset;
  /** Desktop keeps the sidebar open by default; on phones it starts closed. */
  readonly sidebarOpen = signal(!this.handset());

  /** Menu visibility mirrors the route guards (adminGuard); the backend enforces the same rules. */
  readonly visibleNavItems = computed(() =>
    this.navItems.filter((item) => !item.adminOnly || this.user()?.role === 'ADMIN'),
  );
  readonly sectionTitle = computed(
    () => this.navItems.find((item) => this.url().startsWith(item.route))?.label ?? 'BTC Flow',
  );
  readonly roleLabel = computed(() => (this.user()?.role === 'ADMIN' ? 'Administrator' : 'Employee'));
  readonly initials = computed(() => {
    const parts = (this.user()?.name ?? '').trim().split(/\s+/).filter(Boolean);
    return ((parts[0]?.[0] ?? 'U') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  });

  constructor() {
    timer(0, 60_000)
      .pipe(
        switchMap(() => this.notifications.refreshUnreadCount().pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  closeOnMobile(): void {
    if (this.isMobile()) {
      this.sidebarOpen.set(false);
    }
  }

  toggleSidebar(): void {
    this.sidebarOpen.update((open) => !open);
  }

  logout(): void {
    this.authService.logout();
  }
}
