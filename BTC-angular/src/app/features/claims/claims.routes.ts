import { Routes } from '@angular/router';

export const CLAIMS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/claim-management/claim-management.component').then(
        (m) => m.ClaimManagementComponent,
      ),
  },
];
