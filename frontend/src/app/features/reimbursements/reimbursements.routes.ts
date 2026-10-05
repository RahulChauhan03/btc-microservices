import { Routes } from '@angular/router';

export const REIMBURSEMENTS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/reimbursements/reimbursements.component').then((m) => m.ReimbursementsComponent),
  },
];
