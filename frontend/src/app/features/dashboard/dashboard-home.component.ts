import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ActionRequestSummary } from '../../core/models/action.model';
import { AuditLogEntry } from '../../core/models/audit.model';
import {
  DashboardSummary,
  DecisionDistributionEntry,
  RiskDistributionEntry
} from '../../core/models/dashboard.model';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';

interface BarSlice {
  label: string;
  count: number;
  percent: number;
  tone: string;
}

@Component({
  selector: 'app-dashboard-home',
  standalone: true,
  imports: [CommonModule, RouterLink, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-home.component.html'
})
export class DashboardHomeComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly dashboard = inject(DashboardService);

  readonly user = () => this.auth.currentUser;

  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly summary = signal<DashboardSummary | null>(null);
  readonly recentActions = signal<ActionRequestSummary[]>([]);
  readonly recentAudit = signal<AuditLogEntry[]>([]);
  readonly riskDist = signal<RiskDistributionEntry[]>([]);
  readonly decisionDist = signal<DecisionDistributionEntry[]>([]);

  readonly riskBars = computed<BarSlice[]>(() => {
    const data = this.riskDist();
    const max = data.reduce((m, e) => Math.max(m, e.count), 0) || 1;
    const tones: Record<string, string> = {
      LOW: 'bg-emerald-500/70',
      MEDIUM: 'bg-yellow-500/70',
      HIGH: 'bg-orange-500/70',
      CRITICAL: 'bg-red-500/70'
    };
    return data.map((e) => ({
      label: e.riskLevel,
      count: e.count,
      percent: Math.round((e.count / max) * 100),
      tone: tones[e.riskLevel] ?? 'bg-slate-500/70'
    }));
  });

  readonly decisionBars = computed<BarSlice[]>(() => {
    const data = this.decisionDist();
    const max = data.reduce((m, e) => Math.max(m, e.count), 0) || 1;
    const tones: Record<string, string> = {
      ALLOWED: 'bg-emerald-500/70',
      APPROVED: 'bg-emerald-400/70',
      DENIED: 'bg-red-500/70',
      REJECTED: 'bg-red-400/70',
      PENDING_APPROVAL: 'bg-yellow-500/70',
      RECEIVED: 'bg-slate-500/70'
    };
    return data.map((e) => ({
      label: e.status,
      count: e.count,
      percent: Math.round((e.count / max) * 100),
      tone: tones[e.status] ?? 'bg-slate-500/70'
    }));
  });

  ngOnInit(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    forkJoin({
      summary: this.dashboard.summary(),
      recentActions: this.dashboard.recentActions(),
      recentAudit: this.dashboard.recentAuditEvents(),
      risk: this.dashboard.riskDistribution(),
      decision: this.dashboard.decisionDistribution()
    }).subscribe({
      next: (data) => {
        this.summary.set(data.summary);
        this.recentActions.set(data.recentActions);
        this.recentAudit.set(data.recentAudit);
        this.riskDist.set(data.risk);
        this.decisionDist.set(data.decision);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load dashboard data.');
        this.loading.set(false);
      }
    });
  }

  statusTone(status: string): string {
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
}
