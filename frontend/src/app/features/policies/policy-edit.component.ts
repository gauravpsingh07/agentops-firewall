import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { ACTION_TYPES, RISK_LEVELS } from '../../core/models/common.model';
import {
  CONDITION_OPERATORS,
  ConditionOperator,
  CreatePolicyRequest,
  Policy,
  PolicyCondition,
  UpdatePolicyRequest
} from '../../core/models/policy.model';
import { NotificationService } from '../../core/services/notification.service';
import { PolicyService } from '../../core/services/policy.service';

@Component({
  selector: 'app-policy-edit',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './policy-edit.component.html'
})
export class PolicyEditComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly policies = inject(PolicyService);
  private readonly notify = inject(NotificationService);

  readonly actionTypes = ACTION_TYPES;
  readonly riskLevels = RISK_LEVELS;
  readonly operators: ConditionOperator[] = CONDITION_OPERATORS;
  readonly effects = ['ALLOW', 'DENY', 'NEEDS_APPROVAL'] as const;

  readonly policyId = signal<string | null>(null);
  readonly saving = signal(false);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly isEdit = computed(() => this.policyId() !== null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(3)]],
    description: [''],
    effect: ['ALLOW' as 'ALLOW' | 'DENY' | 'NEEDS_APPROVAL', [Validators.required]],
    priority: [50, [Validators.required, Validators.min(0), Validators.max(1000)]],
    enabled: [true],
    actionType: [''],
    resourcePattern: [''],
    minRiskLevel: [''],
    conditions: this.fb.array<ReturnType<typeof this.makeConditionGroup>>([])
  });

  get conditions(): FormArray {
    return this.form.controls.conditions;
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) return;
    this.policyId.set(id);
    this.loading.set(true);
    this.policies.get(id).subscribe({
      next: (policy) => {
        this.applyToForm(policy);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load this policy.');
        this.loading.set(false);
      }
    });
  }

  addCondition(): void {
    this.conditions.push(this.makeConditionGroup({ field: '', operator: 'EQUALS', value: '' }));
  }

  removeCondition(index: number): void {
    this.conditions.removeAt(index);
  }

  submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.errorMessage.set(null);
    if (this.isEdit()) {
      const payload = this.toUpdateRequest();
      this.policies.update(this.policyId()!, payload).subscribe({
        next: () => this.afterSave('Policy updated.'),
        error: (err) => this.afterError(err)
      });
    } else {
      const payload = this.toCreateRequest();
      this.policies.create(payload).subscribe({
        next: () => this.afterSave('Policy created.'),
        error: (err) => this.afterError(err)
      });
    }
  }

  private applyToForm(policy: Policy): void {
    this.form.patchValue({
      name: policy.name,
      description: policy.description ?? '',
      effect: policy.effect,
      priority: policy.priority,
      enabled: policy.enabled,
      actionType: policy.actionType ?? '',
      resourcePattern: policy.resourcePattern ?? '',
      minRiskLevel: policy.minRiskLevel ?? ''
    });
    this.conditions.clear();
    for (const c of policy.conditions) {
      this.conditions.push(this.makeConditionGroup(c));
    }
  }

  private makeConditionGroup(c: PolicyCondition) {
    return this.fb.nonNullable.group({
      field: [c.field, [Validators.required]],
      operator: [c.operator, [Validators.required]],
      value: [c.value, [Validators.required]]
    });
  }

  private toCreateRequest(): CreatePolicyRequest {
    const raw = this.form.getRawValue();
    return {
      name: raw.name.trim(),
      description: raw.description.trim() || null,
      effect: raw.effect,
      priority: raw.priority,
      actionType: (raw.actionType || null) as CreatePolicyRequest['actionType'],
      resourcePattern: raw.resourcePattern.trim() || null,
      minRiskLevel: (raw.minRiskLevel || null) as CreatePolicyRequest['minRiskLevel'],
      conditions: raw.conditions.map((c) => ({
        field: c.field.trim(),
        operator: c.operator as ConditionOperator,
        value: c.value.trim()
      }))
    };
  }

  private toUpdateRequest(): UpdatePolicyRequest {
    const create = this.toCreateRequest();
    return { ...create, enabled: this.form.controls.enabled.value };
  }

  private afterSave(message: string): void {
    this.saving.set(false);
    this.notify.success(message);
    this.router.navigateByUrl('/dashboard/policies');
  }

  private afterError(err: HttpErrorResponse): void {
    this.saving.set(false);
    this.errorMessage.set(err.error?.message ?? 'Could not save the policy.');
  }
}
