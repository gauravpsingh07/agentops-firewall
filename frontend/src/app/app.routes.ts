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
        loadComponent: () =>
          import('./features/actions/action-feed.component').then((m) => m.ActionFeedComponent)
      },
      {
        path: 'policies',
        children: [
          {
            path: '',
            pathMatch: 'full',
            loadComponent: () =>
              import('./features/policies/policy-list.component').then((m) => m.PolicyListComponent)
          },
          {
            path: 'new',
            canActivate: [roleGuard('ADMIN')],
            loadComponent: () =>
              import('./features/policies/policy-edit.component').then((m) => m.PolicyEditComponent)
          },
          {
            path: ':id/edit',
            canActivate: [roleGuard('ADMIN')],
            loadComponent: () =>
              import('./features/policies/policy-edit.component').then((m) => m.PolicyEditComponent)
          }
        ]
      },
      {
        path: 'simulator',
        loadComponent: () =>
          import('./features/simulator/simulator.component').then((m) => m.SimulatorComponent)
      },
      {
        path: 'approvals',
        canActivate: [roleGuard('ADMIN', 'REVIEWER')],
        loadComponent: () =>
          import('./features/approvals/approval-inbox.component').then((m) => m.ApprovalInboxComponent)
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
