import { BreakpointObserver } from '@angular/cdk/layout';
import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';

import { AuthService } from '../core/services/auth.service';

@Component({
  selector: 'app-shell',
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatSidenavModule,
    MatToolbarModule,
  ],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.css',
})
export class AppShellComponent {
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly authService = inject(AuthService);

  private readonly handset = toSignal(this.breakpointObserver.observe('(max-width: 960px)'), {
    initialValue: { matches: false, breakpoints: {} },
  });

  readonly isMobile = computed(() => this.handset().matches);
  readonly user = this.authService.currentUser;
  readonly sidebarOpen = signal(true);
  readonly navItems = [
    { label: 'Dashboard', icon: 'dashboard', route: '/dashboard' },
    { label: 'Trips', icon: 'flight_takeoff', route: '/trips' },
    { label: 'Expenses', icon: 'receipt_long', route: '/expenses' },
    { label: 'Claims', icon: 'fact_check', route: '/claims' },
    { label: 'Users', icon: 'groups', route: '/users', adminOnly: true },
  ];

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
