import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';

import { ActionFilters, ActionRequestSummary } from '../../core/models/action.model';
import { Agent } from '../../core/models/agent.model';
import {
  ACTION_REQUEST_STATUSES,
  ACTION_TYPES,
  ActionRequestStatus,
  ActionType,
  PageResponse,
  RISK_LEVELS,
  RiskLevel
} from '../../core/models/common.model';
import { ActionService } from '../../core/services/action.service';
import { AgentService } from '../../core/services/agent.service';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-action-feed',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './action-feed.component.html'
})
export class ActionFeedComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly actions = inject(ActionService);
  private readonly agents = inject(AgentService);

  readonly actionTypes: ActionType[] = ACTION_TYPES;
  readonly statuses: ActionRequestStatus[] = ACTION_REQUEST_STATUSES;
  readonly riskLevels: RiskLevel[] = RISK_LEVELS;

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly page = signal<PageResponse<ActionRequestSummary> | null>(null);
  readonly agentOptions = signal<Agent[]>([]);
  readonly currentPage = signal(0);

  readonly hasResults = computed(() => (this.page()?.content.length ?? 0) > 0);

  readonly filterForm = this.fb.nonNullable.group({
    agentId: [''],
    actionType: [''],
    status: [''],
    riskLevel: ['']
  });

  ngOnInit(): void {
    this.agents.list().subscribe({
      next: (list) => this.agentOptions.set(list),
      error: () => this.agentOptions.set([])
    });
    this.load();
  }

  applyFilters(): void {
    this.currentPage.set(0);
    this.load();
  }

  clearFilters(): void {
    this.filterForm.reset({ agentId: '', actionType: '', status: '', riskLevel: '' });
    this.currentPage.set(0);
    this.load();
  }

  goToPage(delta: number): void {
    const current = this.page();
    if (!current) return;
    const next = current.number + delta;
    if (next < 0 || next >= current.totalPages) return;
    this.currentPage.set(next);
    this.load();
  }

  riskColor(level: RiskLevel): string {
    switch (level) {
      case 'CRITICAL':
        return 'bg-red-900/40 text-red-200 border-red-700/40';
      case 'HIGH':
        return 'bg-orange-900/40 text-orange-200 border-orange-700/40';
      case 'MEDIUM':
        return 'bg-yellow-900/40 text-yellow-200 border-yellow-700/40';
      default:
        return 'bg-emerald-900/40 text-emerald-200 border-emerald-700/40';
    }
  }

  statusColor(status: ActionRequestStatus): string {
    switch (status) {
      case 'ALLOWED':
      case 'APPROVED':
        return 'text-emerald-300';
      case 'DENIED':
      case 'REJECTED':
        return 'text-red-300';
      case 'PENDING_APPROVAL':
        return 'text-yellow-300';
      default:
        return 'text-slate-300';
    }
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    const raw = this.filterForm.getRawValue();
    const filters: ActionFilters = {
      agentId: raw.agentId || undefined,
      actionType: (raw.actionType || undefined) as ActionType | undefined,
      status: (raw.status || undefined) as ActionRequestStatus | undefined,
      riskLevel: (raw.riskLevel || undefined) as RiskLevel | undefined,
      page: this.currentPage(),
      size: PAGE_SIZE
    };
    this.actions.list(filters).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load the action feed. Try again in a moment.');
        this.loading.set(false);
      }
    });
  }
}
