import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const appRoutes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent)
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/layout/dashboard-shell.component').then((m) => m.DashboardShellComponent),
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () =>
          import('./features/dashboard/dashboard-home.component').then((m) => m.DashboardHomeComponent)
      },
      {
        path: 'actions',
        data: { feature: 'Action feed' },
        loadComponent: () =>
          import('./features/shared/coming-soon.component').then((m) => m.ComingSoonComponent)
      },
      {
        path: 'policies',
        data: { feature: 'Policy management' },
        loadComponent: () =>
          import('./features/shared/coming-soon.component').then((m) => m.ComingSoonComponent)
      },
      {
        path: 'simulator',
        data: { feature: 'Policy simulator' },
        loadComponent: () =>
          import('./features/shared/coming-soon.component').then((m) => m.ComingSoonComponent)
      },
      {
        path: 'approvals',
        canActivate: [roleGuard('ADMIN', 'REVIEWER')],
        data: { feature: 'Approval inbox' },
        loadComponent: () =>
          import('./features/shared/coming-soon.component').then((m) => m.ComingSoonComponent)
      },
      {
        path: 'audit',
        data: { feature: 'Audit log' },
        loadComponent: () =>
          import('./features/shared/coming-soon.component').then((m) => m.ComingSoonComponent)
      }
    ]
  },
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: '**', redirectTo: 'dashboard' }
];
