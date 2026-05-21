import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';

import { AuditFilters, AuditLogEntry } from '../../core/models/audit.model';
import { PageResponse } from '../../core/models/common.model';
import { AuditService } from '../../core/services/audit.service';

const PAGE_SIZE = 25;

@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './audit-log.component.html'
})
export class AuditLogComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly audit = inject(AuditService);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly page = signal<PageResponse<AuditLogEntry> | null>(null);
  readonly currentPage = signal(0);
  readonly expanded = signal<Set<string>>(new Set());

  readonly hasResults = computed(() => (this.page()?.content.length ?? 0) > 0);

  readonly filterForm = this.fb.nonNullable.group({
    eventType: [''],
    actorType: [''],
    subjectType: [''],
    from: [''],
    to: ['']
  });

  ngOnInit(): void {
    this.load();
  }

  applyFilters(): void {
    this.currentPage.set(0);
    this.load();
  }

  clearFilters(): void {
    this.filterForm.reset({ eventType: '', actorType: '', subjectType: '', from: '', to: '' });
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

  toggle(id: string): void {
    this.expanded.update((set) => {
      const next = new Set(set);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  isExpanded(id: string): boolean {
    return this.expanded().has(id);
  }

  formatDetails(json: string | null): string {
    if (!json) return '—';
    try {
      return JSON.stringify(JSON.parse(json), null, 2);
    } catch {
      return json;
    }
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    const raw = this.filterForm.getRawValue();
    const filters: AuditFilters = {
      eventType: raw.eventType.trim() || undefined,
      actorType: raw.actorType.trim() || undefined,
      subjectType: raw.subjectType.trim() || undefined,
      from: this.toIso(raw.from),
      to: this.toIso(raw.to),
      page: this.currentPage(),
      size: PAGE_SIZE
    };
    this.audit.search(filters).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load the audit log.');
        this.loading.set(false);
      }
    });
  }

  private toIso(value: string): string | undefined {
    if (!value) return undefined;
    const ms = Date.parse(value);
    return Number.isNaN(ms) ? undefined : new Date(ms).toISOString();
  }
}
