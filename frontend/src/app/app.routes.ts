import { Routes } from '@angular/router';

import { adminGuard } from './core/guards/admin.guard';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Sign in · BTC Flow',
    loadComponent: () => import('./features/auth/pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'forgot-password',
    title: 'Reset password · BTC Flow',
    loadComponent: () =>
      import('./features/auth/pages/forgot-password/forgot-password.component').then((m) => m.ForgotPasswordComponent),
  },
  {
    // No guest guard: a reset link must work even if the browser still holds an old session.
    path: 'reset-password',
    title: 'Choose a new password · BTC Flow',
    loadComponent: () =>
      import('./features/auth/pages/reset-password/reset-password.component').then((m) => m.ResetPasswordComponent),
  },
  {
    path: '',
    // Lazy: the sign-in screens do not download the application shell.
    loadComponent: () => import('./layout/app-shell.component').then((m) => m.AppShellComponent),
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Dashboard · BTC Flow',
        loadChildren: () =>
          import('./features/dashboard/dashboard.routes').then((m) => m.DASHBOARD_ROUTES),
      },
      {
        path: 'trips',
        title: 'Trips · BTC Flow',
        loadChildren: () => import('./features/trips/trips.routes').then((m) => m.TRIPS_ROUTES),
      },
      {
        path: 'expenses',
        title: 'Expenses · BTC Flow',
        loadChildren: () =>
          import('./features/expenses/expenses.routes').then((m) => m.EXPENSES_ROUTES),
      },
      {
        path: 'claims',
        title: 'Claims · BTC Flow',
        loadChildren: () => import('./features/claims/claims.routes').then((m) => m.CLAIMS_ROUTES),
      },
      {
        path: 'employees',
        title: 'Employee directory · BTC Flow',
        canActivate: [adminGuard],
        loadChildren: () => import('./features/employees/employees.routes').then((m) => m.EMPLOYEES_ROUTES),
      },
      {
        path: 'reports',
        title: 'Reports · BTC Flow',
        canActivate: [adminGuard],
        loadChildren: () => import('./features/reports/reports.routes').then((m) => m.REPORTS_ROUTES),
      },
      {
        path: 'policy',
        title: 'Travel policy · BTC Flow',
        loadChildren: () => import('./features/policy/policy.routes').then((m) => m.POLICY_ROUTES),
      },
      {
        path: 'reimbursements',
        title: 'Reimbursements · BTC Flow',
        loadChildren: () => import('./features/reimbursements/reimbursements.routes').then((m) => m.REIMBURSEMENTS_ROUTES),
      },
      {
        path: 'audit',
        title: 'Audit log · BTC Flow',
        canActivate: [adminGuard],
        loadChildren: () => import('./features/audit/audit.routes').then((m) => m.AUDIT_ROUTES),
      },
      {
        path: 'profile',
        title: 'Profile & settings · BTC Flow',
        loadComponent: () => import('./features/profile/profile.component').then((m) => m.ProfileComponent),
      },
      {
        path: 'notifications',
        title: 'Notifications · BTC Flow',
        loadChildren: () =>
          import('./features/notifications/notifications.routes').then((m) => m.NOTIFICATIONS_ROUTES),
      },
      {
        path: 'users',
        title: 'Users · BTC Flow',
        canActivate: [adminGuard],
        loadChildren: () => import('./features/users/users.routes').then((m) => m.USERS_ROUTES),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
