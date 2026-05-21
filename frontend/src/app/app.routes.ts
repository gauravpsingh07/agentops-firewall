import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';

export const appRoutes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent)
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    // Real shell + child routes land in commit 26.
    loadComponent: () =>
      import('./features/auth/login.component').then((m) => m.LoginComponent)
  },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' }
];
