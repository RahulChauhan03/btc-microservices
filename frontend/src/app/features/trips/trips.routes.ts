import { Routes } from '@angular/router';

export const TRIPS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/trip-management/trip-management.component').then(
        (m) => m.TripManagementComponent,
      ),
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./pages/trip-management/trip-management.component').then(
        (m) => m.TripManagementComponent,
      ),
  },
  {
    path: 'view/:id',
    loadComponent: () =>
      import('./pages/trip-details/trip-details.component').then((m) => m.TripDetailsComponent),
  },
  {
    path: 'edit/:id',
    loadComponent: () =>
      import('./pages/trip-management/trip-management.component').then(
        (m) => m.TripManagementComponent,
      ),
  },
];
