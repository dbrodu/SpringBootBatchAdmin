import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BatchAdminService } from './batch-admin.service';
import { ProviderInfo, StepDefinition } from './models';

interface StepDraft {
  name: string;
  type: string;
  properties: Array<{ key: string; value: string }>;
}

@Component({
  selector: 'ba-create-job',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="panel">
      <h3>Create a job on the fly</h3>
      <p class="muted">
        Compose a job from the building blocks (tasklet providers) exposed by the application.
        It is registered immediately and becomes launchable and schedulable.
      </p>

      <label class="field">
        <span>Job name</span>
        <input [(ngModel)]="jobName" placeholder="e.g. nightlyExport" />
      </label>
      <label class="field">
        <span>Description</span>
        <input [(ngModel)]="description" placeholder="optional" />
      </label>

      <h4>Steps</h4>
      <p *ngIf="steps.length === 0" class="muted">Add at least one step.</p>

      <div class="step-card" *ngFor="let step of steps; let i = index">
        <div class="step-head">
          <span class="step-index">{{ i + 1 }}</span>
          <input class="step-name" [(ngModel)]="step.name" [name]="'sn' + i" placeholder="step name" />
          <select [(ngModel)]="step.type" [name]="'st' + i" (ngModelChange)="onTypeChange(step)">
            <option *ngFor="let p of providers" [value]="p.type">{{ p.displayName }} ({{ p.type }})</option>
          </select>
          <button class="btn btn-small btn-danger" (click)="removeStep(i)">Remove</button>
        </div>
        <div class="step-props">
          <div class="param-row" *ngFor="let prop of step.properties; let j = index">
            <input placeholder="property" [(ngModel)]="prop.key" [name]="'pk' + i + '_' + j" />
            <input placeholder="value" [(ngModel)]="prop.value" [name]="'pv' + i + '_' + j" />
            <button type="button" class="btn btn-small" (click)="step.properties.splice(j, 1)">−</button>
          </div>
          <button type="button" class="btn btn-small" (click)="step.properties.push({ key: '', value: '' })">
            + property
          </button>
          <span class="hint" *ngIf="hintFor(step.type) as hint">{{ hint }}</span>
        </div>
      </div>

      <div class="toolbar">
        <button class="btn" (click)="addStep()">+ Add step</button>
        <button class="btn btn-primary" [disabled]="!canSubmit()" (click)="submit()">Create job</button>
      </div>

      <p *ngIf="message" [class]="error ? 'error' : 'success'">{{ message }}</p>
    </div>
  `,
})
export class CreateJobComponent implements OnInit {
  @Output() created = new EventEmitter<string>();

  providers: ProviderInfo[] = [];
  jobName = '';
  description = '';
  steps: StepDraft[] = [];
  message: string | null = null;
  error = false;

  constructor(private readonly api: BatchAdminService) {}

  ngOnInit(): void {
    this.api.listProviders().subscribe((p) => {
      this.providers = p;
      if (this.steps.length === 0 && p.length > 0) {
        this.addStep();
      }
    });
  }

  addStep(): void {
    const type = this.providers[0]?.type ?? '';
    const draft: StepDraft = { name: `step${this.steps.length + 1}`, type, properties: [] };
    this.prefillProperties(draft);
    this.steps.push(draft);
  }

  removeStep(index: number): void {
    this.steps.splice(index, 1);
  }

  onTypeChange(step: StepDraft): void {
    step.properties = [];
    this.prefillProperties(step);
  }

  hintFor(type: string): string | null {
    const provider = this.providers.find((p) => p.type === type);
    if (!provider) {
      return null;
    }
    const keys = Object.keys(provider.properties);
    return keys.length ? `Properties: ${keys.join(', ')}` : null;
  }

  canSubmit(): boolean {
    return this.jobName.trim().length > 0 && this.steps.length > 0 && this.steps.every((s) => s.name.trim() && s.type);
  }

  submit(): void {
    const steps: StepDefinition[] = this.steps.map((s) => ({
      name: s.name.trim(),
      type: s.type,
      properties: this.toMap(s.properties),
    }));
    this.api.createJob({ jobName: this.jobName.trim(), description: this.description.trim(), steps }).subscribe({
      next: (job) => {
        this.error = false;
        this.message = `Job '${job.name}' created.`;
        this.created.emit(job.name);
        this.reset();
      },
      error: (err) => {
        this.error = true;
        const e = err as { error?: { message?: string } };
        this.message = e?.error?.message ?? 'Could not create job';
      },
    });
  }

  private prefillProperties(step: StepDraft): void {
    const provider = this.providers.find((p) => p.type === step.type);
    if (provider) {
      step.properties = Object.keys(provider.properties).map((key) => ({ key, value: '' }));
    }
  }

  private toMap(props: Array<{ key: string; value: string }>): Record<string, unknown> {
    const map: Record<string, unknown> = {};
    for (const p of props) {
      if (p.key.trim()) {
        map[p.key.trim()] = p.value;
      }
    }
    return map;
  }

  private reset(): void {
    this.jobName = '';
    this.description = '';
    this.steps = [];
    this.addStep();
  }
}
