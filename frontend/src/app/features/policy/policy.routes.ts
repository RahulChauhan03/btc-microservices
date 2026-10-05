import { Routes } from '@angular/router';

export const POLICY_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/travel-policy/travel-policy.component').then((m) => m.TravelPolicyComponent),
  },
];
