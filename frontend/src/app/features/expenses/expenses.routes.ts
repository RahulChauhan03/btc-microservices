import { Routes } from '@angular/router';

export const EXPENSES_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/expense-management/expense-management.component').then(
        (m) => m.ExpenseManagementComponent,
      ),
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./pages/expense-management/expense-management.component').then(
        (m) => m.ExpenseManagementComponent,
      ),
  },
  {
    path: 'edit/:id',
    loadComponent: () =>
      import('./pages/expense-management/expense-management.component').then(
        (m) => m.ExpenseManagementComponent,
      ),
  },
];
