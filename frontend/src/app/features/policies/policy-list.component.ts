import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { Policy } from '../../core/models/policy.model';
import { PolicyService } from '../../core/services/policy.service';

@Component({
  selector: 'app-policy-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './policy-list.component.html'
})
export class PolicyListComponent implements OnInit {
  private readonly policies = inject(PolicyService);
  private readonly notify = inject(NotificationService);
  private readonly auth = inject(AuthService);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly items = signal<Policy[]>([]);

  readonly canEdit = computed(() => this.auth.currentUser?.role === 'ADMIN');

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.policies.list().subscribe({
      next: (list) => {
        this.items.set([...list].sort((a, b) => b.priority - a.priority));
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load policies.');
        this.loading.set(false);
      }
    });
  }

  toggleEnabled(policy: Policy): void {
    if (!this.canEdit()) return;
    this.policies.update(policy.id, { enabled: !policy.enabled }).subscribe({
      next: (updated) => {
        this.items.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
        this.notify.success(`${updated.name} ${updated.enabled ? 'enabled' : 'disabled'}.`);
      },
      error: () => this.notify.error('Could not update the policy.')
    });
  }

  effectColor(effect: string): string {
    switch (effect) {
      case 'ALLOW':
        return 'text-emerald-300';
      case 'DENY':
        return 'text-red-300';
      default:
        return 'text-yellow-300';
    }
  }
}
