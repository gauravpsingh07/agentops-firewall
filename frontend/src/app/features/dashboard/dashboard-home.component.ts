import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-dashboard-home',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="space-y-4">
      <header>
        <h2 class="text-2xl font-semibold text-white">
          Welcome{{ user()?.username ? ', ' + user()?.username : '' }}.
        </h2>
        <p class="text-sm text-slate-400">
          Use the navigation to inspect agent activity, manage policies, or review approvals.
        </p>
      </header>
      <div class="rounded-xl border border-slate-800 bg-agentops-card p-6 text-sm text-slate-300">
        Summary cards and charts arrive in the final commit of this phase.
      </div>
    </section>
  `
})
export class DashboardHomeComponent {
  private readonly auth = inject(AuthService);
  readonly user = () => this.auth.currentUser;
}
