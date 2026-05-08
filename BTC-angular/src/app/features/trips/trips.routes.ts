import { Routes } from '@angular/router';

export const TRIPS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/trip-management/trip-management.component').then(
        (m) => m.TripManagementComponent,
      ),
  },
];
