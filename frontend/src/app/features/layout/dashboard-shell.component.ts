import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { UserRole } from '../../core/models/user-role';
import { AuthService } from '../../core/services/auth.service';

interface NavItem {
  label: string;
  path: string;
  allowedRoles?: UserRole[];
}

@Component({
  selector: 'app-dashboard-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-shell.component.html'
})
export class DashboardShellComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly menuOpen = signal(false);

  readonly user = computed(() => this.auth.currentUser);

  private readonly navItems: NavItem[] = [
    { label: 'Overview', path: '/dashboard' },
    { label: 'Action feed', path: '/dashboard/actions' },
    { label: 'Policies', path: '/dashboard/policies' },
    { label: 'Simulator', path: '/dashboard/simulator' },
    { label: 'Approvals', path: '/dashboard/approvals', allowedRoles: ['ADMIN', 'REVIEWER'] },
    { label: 'Audit log', path: '/dashboard/audit' }
  ];

  readonly visibleNav = computed<NavItem[]>(() => {
    const role = this.user()?.role;
    return this.navItems.filter(
      (item) => !item.allowedRoles || (role && item.allowedRoles.includes(role))
    );
  });

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
