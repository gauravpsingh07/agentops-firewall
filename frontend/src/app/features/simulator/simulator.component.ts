import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { ACTION_TYPES, ActionType, PolicyOutcome, RISK_LEVELS, RiskLevel } from '../../core/models/common.model';
import { Agent } from '../../core/models/agent.model';
import { SimulationRequest, SimulationResponse } from '../../core/models/simulator.model';
import { AgentService } from '../../core/services/agent.service';
import { SimulatorService } from '../../core/services/simulator.service';

@Component({
  selector: 'app-simulator',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './simulator.component.html'
})
export class SimulatorComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly simulator = inject(SimulatorService);
  private readonly agents = inject(AgentService);

  readonly actionTypes: ActionType[] = ACTION_TYPES;
  readonly riskLevels: RiskLevel[] = RISK_LEVELS;

  readonly running = signal(false);
  readonly agentOptions = signal<Agent[]>([]);
  readonly result = signal<SimulationResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly metadataError = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    actionType: ['SEND_EMAIL' as ActionType, [Validators.required]],
    riskLevel: ['MEDIUM' as RiskLevel, [Validators.required]],
    resource: [''],
    agentName: [''],
    metadata: ['{\n  "recipientDomain": "external.com"\n}']
  });

  readonly decisionTone = computed(() => {
    switch (this.result()?.decision as PolicyOutcome | undefined) {
      case 'ALLOW':
        return 'border-emerald-700/40 bg-emerald-900/30 text-emerald-100';
      case 'DENY':
        return 'border-red-700/40 bg-red-900/30 text-red-100';
      case 'NEEDS_APPROVAL':
        return 'border-yellow-700/40 bg-yellow-900/30 text-yellow-100';
      default:
        return 'border-slate-700 bg-slate-900/50 text-slate-200';
    }
  });

  ngOnInit(): void {
    this.agents.list().subscribe({
      next: (list) => this.agentOptions.set(list),
      error: () => this.agentOptions.set([])
    });
  }

  submit(): void {
    if (this.form.invalid || this.running()) {
      this.form.markAllAsTouched();
      return;
    }
    this.errorMessage.set(null);
    this.metadataError.set(null);
    const raw = this.form.getRawValue();
    let metadata: Record<string, unknown> | undefined;
    if (raw.metadata.trim()) {
      try {
        const parsed = JSON.parse(raw.metadata);
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
          metadata = parsed as Record<string, unknown>;
        } else {
          this.metadataError.set('Metadata must be a JSON object.');
          return;
        }
      } catch {
        this.metadataError.set('Metadata is not valid JSON.');
        return;
      }
    }
    const request: SimulationRequest = {
      actionType: raw.actionType,
      riskLevel: raw.riskLevel,
      resource: raw.resource.trim() || undefined,
      agentName: raw.agentName.trim() || undefined,
      metadata
    };
    this.running.set(true);
    this.simulator.simulate(request).subscribe({
      next: (response) => {
        this.result.set(response);
        this.running.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.running.set(false);
        this.errorMessage.set(err.error?.message ?? 'Simulation failed.');
      }
    });
  }
}
