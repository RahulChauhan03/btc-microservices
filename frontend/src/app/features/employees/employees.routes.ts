import { Routes } from '@angular/router';

export const EMPLOYEES_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/employee-directory/employee-directory.component').then((m) => m.EmployeeDirectoryComponent),
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./pages/employee-profile/employee-profile.component').then((m) => m.EmployeeProfileComponent),
  },
];
