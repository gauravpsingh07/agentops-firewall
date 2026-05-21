import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-coming-soon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="rounded-xl border border-slate-800 bg-agentops-card p-10 text-center">
      <h2 class="text-xl font-semibold text-white">{{ feature }}</h2>
      <p class="mt-2 text-sm text-slate-400">
        This page is part of Phase 5 and ships in an upcoming commit.
      </p>
    </div>
  `
})
export class ComingSoonComponent {
  private readonly route = inject(ActivatedRoute);
  readonly feature: string = this.route.snapshot.data['feature'] ?? 'Coming soon';
}
