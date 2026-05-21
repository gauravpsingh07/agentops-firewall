import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { Approval, ApprovalFilters } from '../../core/models/approval.model';
import {
  ACTION_REQUEST_STATUSES,
  ACTION_TYPES,
  ActionType,
  ApprovalStatus,
  PageResponse,
  RISK_LEVELS,
  RiskLevel
} from '../../core/models/common.model';
import { ApprovalService } from '../../core/services/approval.service';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';

const PAGE_SIZE = 20;
const APPROVAL_STATUSES: ApprovalStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'];

@Component({
  selector: 'app-approval-inbox',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './approval-inbox.component.html'
})
export class ApprovalInboxComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly approvals = inject(ApprovalService);
  private readonly auth = inject(AuthService);
  private readonly notify = inject(NotificationService);

  readonly statuses = APPROVAL_STATUSES;
  readonly actionTypes = ACTION_TYPES;
  readonly riskLevels = RISK_LEVELS;
  readonly actionRequestStatuses = ACTION_REQUEST_STATUSES;

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly page = signal<PageResponse<Approval> | null>(null);
  readonly currentPage = signal(0);
  readonly notes = signal<Record<string, string>>({});
  readonly busyIds = signal<Set<string>>(new Set());

  readonly canDecide = computed(() => {
    const role = this.auth.currentUser?.role;
    return role === 'ADMIN' || role === 'REVIEWER';
  });

  readonly hasResults = computed(() => (this.page()?.content.length ?? 0) > 0);

  readonly filterForm = this.fb.nonNullable.group({
    status: ['PENDING' as ApprovalStatus | ''],
    actionType: [''],
    riskLevel: ['']
  });

  ngOnInit(): void {
    this.load();
  }

  applyFilters(): void {
    this.currentPage.set(0);
    this.load();
  }

  clearFilters(): void {
    this.filterForm.reset({ status: '', actionType: '', riskLevel: '' });
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

  setNote(id: string, note: string): void {
    this.notes.update((map) => ({ ...map, [id]: note }));
  }

  approve(approval: Approval): void {
    if (!this.canDecide()) return;
    this.decide(approval, 'approve');
  }

  reject(approval: Approval): void {
    if (!this.canDecide()) return;
    this.decide(approval, 'reject');
  }

  riskTone(level: RiskLevel): string {
    switch (level) {
      case 'CRITICAL': return 'bg-red-900/40 text-red-200 border-red-700/40';
      case 'HIGH': return 'bg-orange-900/40 text-orange-200 border-orange-700/40';
      case 'MEDIUM': return 'bg-yellow-900/40 text-yellow-200 border-yellow-700/40';
      default: return 'bg-emerald-900/40 text-emerald-200 border-emerald-700/40';
    }
  }

  statusTone(status: ApprovalStatus): string {
    switch (status) {
      case 'APPROVED': return 'text-emerald-300';
      case 'REJECTED': return 'text-red-300';
      case 'EXPIRED': return 'text-slate-500';
      default: return 'text-yellow-300';
    }
  }

  private decide(approval: Approval, kind: 'approve' | 'reject'): void {
    const note = this.notes()[approval.id]?.trim() || undefined;
    this.markBusy(approval.id, true);
    const call = kind === 'approve'
      ? this.approvals.approve(approval.id, note)
      : this.approvals.reject(approval.id, note);
    call.subscribe({
      next: (updated) => {
        this.replaceInPage(updated);
        this.markBusy(approval.id, false);
        this.notify.success(
          kind === 'approve' ? 'Approval granted.' : 'Approval rejected.'
        );
      },
      error: (err: HttpErrorResponse) => {
        this.markBusy(approval.id, false);
        if (err.status === 409) {
          this.notify.error('This approval was already decided. Refreshing the list.');
          this.load();
          return;
        }
        this.notify.error(err.error?.message ?? `Could not ${kind} this approval.`);
      }
    });
  }

  private replaceInPage(updated: Approval): void {
    const current = this.page();
    if (!current) return;
    this.page.set({
      ...current,
      content: current.content.map((a) => (a.id === updated.id ? updated : a))
    });
  }

  private markBusy(id: string, busy: boolean): void {
    this.busyIds.update((set) => {
      const next = new Set(set);
      if (busy) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  isBusy(id: string): boolean {
    return this.busyIds().has(id);
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    const raw = this.filterForm.getRawValue();
    const filters: ApprovalFilters = {
      status: (raw.status || undefined) as ApprovalStatus | undefined,
      actionType: (raw.actionType || undefined) as ActionType | undefined,
      riskLevel: (raw.riskLevel || undefined) as RiskLevel | undefined,
      page: this.currentPage(),
      size: PAGE_SIZE
    };
    this.approvals.list(filters).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load the approval inbox.');
        this.loading.set(false);
      }
    });
  }
}
