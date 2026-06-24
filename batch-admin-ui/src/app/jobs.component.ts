import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BatchAdminService } from './batch-admin.service';
import { formatDate, formatDuration, paramsToText, statusClass } from './format';
import { ExecutionSummary, JobSummary } from './models';

@Component({
  selector: 'ba-jobs',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="toolbar">
      <button class="btn" (click)="reload()">↻ Refresh</button>
      <span *ngIf="error" class="error">{{ error }}</span>
    </div>

    <table class="grid">
      <thead>
        <tr><th>Job</th><th>Type</th><th>Instances</th><th>Last status</th><th>Last run</th><th>Actions</th></tr>
      </thead>
      <tbody>
        <ng-container *ngFor="let job of jobs">
          <tr>
            <td>
              <button class="link" (click)="toggle(job)">{{ expanded === job.name ? '▾' : '▸' }} {{ job.name }}</button>
            </td>
            <td>
              <span class="tag" *ngIf="job.dynamic">dynamic</span>
              <span class="tag tag-muted" *ngIf="!job.dynamic">declared</span>
              <span class="tag tag-warn" *ngIf="!job.launchable">unregistered</span>
            </td>
            <td>{{ job.instanceCount }}</td>
            <td><span [class]="statusClass(job.lastStatus)">{{ job.lastStatus ?? '—' }}</span></td>
            <td>{{ formatDate(job.lastExecution) }}</td>
            <td class="actions">
              <button class="btn btn-primary" [disabled]="!job.launchable" (click)="openStart(job)">▶ Start</button>
              <button class="btn btn-danger" *ngIf="job.dynamic" (click)="remove(job)">🗑 Delete</button>
            </td>
          </tr>

          <tr *ngIf="starting === job.name">
            <td colspan="6" class="subrow">
              <form class="param-form" (ngSubmit)="start(job)">
                <strong>Parameters</strong>
                <div class="param-row" *ngFor="let p of startParams; let i = index">
                  <input placeholder="key" [(ngModel)]="p.key" [name]="'k' + i" />
                  <input placeholder="value" [(ngModel)]="p.value" [name]="'v' + i" />
                  <button type="button" class="btn btn-small" (click)="removeParam(i)">−</button>
                </div>
                <button type="button" class="btn btn-small" (click)="addParam()">+ parameter</button>
                <div class="param-actions">
                  <button type="submit" class="btn btn-primary">Launch</button>
                  <button type="button" class="btn" (click)="starting = null">Cancel</button>
                </div>
              </form>
            </td>
          </tr>

          <tr *ngIf="expanded === job.name">
            <td colspan="6" class="subrow">
              <h4>Recent executions</h4>
              <p *ngIf="jobExecutions.length === 0" class="muted">No executions yet.</p>
              <table class="grid grid-inner" *ngIf="jobExecutions.length > 0">
                <thead>
                  <tr><th>#</th><th>Status</th><th>Params</th><th>Started</th><th>Duration</th><th></th></tr>
                </thead>
                <tbody>
                  <tr *ngFor="let e of jobExecutions">
                    <td>{{ e.executionId }}</td>
                    <td><span [class]="statusClass(e.status)">{{ e.status }}</span></td>
                    <td class="muted">{{ paramsText(e) }}</td>
                    <td>{{ formatDate(e.startTime ?? e.createTime) }}</td>
                    <td>{{ formatDuration(e.durationMs) }}</td>
                    <td class="actions">
                      <button class="btn btn-small" *ngIf="e.running" (click)="stop(e, job)">⏹ Stop</button>
                      <button class="btn btn-small" *ngIf="e.restartable" (click)="restart(e, job)">↻ Restart</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </td>
          </tr>
        </ng-container>
        <tr *ngIf="jobs.length === 0"><td colspan="6" class="muted">No jobs found.</td></tr>
      </tbody>
    </table>
  `,
})
export class JobsComponent implements OnInit {
  jobs: JobSummary[] = [];
  expanded: string | null = null;
  starting: string | null = null;
  jobExecutions: ExecutionSummary[] = [];
  startParams: Array<{ key: string; value: string }> = [];
  error: string | null = null;

  readonly statusClass = statusClass;
  readonly formatDate = formatDate;
  readonly formatDuration = formatDuration;

  constructor(private readonly api: BatchAdminService) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.error = null;
    this.api.listJobs().subscribe({
      next: (jobs) => (this.jobs = jobs),
      error: (err) => (this.error = this.message(err)),
    });
  }

  toggle(job: JobSummary): void {
    if (this.expanded === job.name) {
      this.expanded = null;
      return;
    }
    this.expanded = job.name;
    this.jobExecutions = [];
    this.api.jobExecutions(job.name).subscribe((execs) => (this.jobExecutions = execs));
  }

  openStart(job: JobSummary): void {
    this.starting = job.name;
    this.startParams = [{ key: '', value: '' }];
  }

  addParam(): void {
    this.startParams.push({ key: '', value: '' });
  }

  removeParam(index: number): void {
    this.startParams.splice(index, 1);
  }

  start(job: JobSummary): void {
    const params: Record<string, string> = {};
    for (const p of this.startParams) {
      if (p.key.trim()) {
        params[p.key.trim()] = p.value;
      }
    }
    this.api.startJob(job.name, params).subscribe({
      next: () => {
        this.starting = null;
        this.reload();
        if (this.expanded === job.name) {
          this.toggle(job);
          this.toggle(job);
        }
      },
      error: (err) => (this.error = this.message(err)),
    });
  }

  stop(execution: ExecutionSummary, job: JobSummary): void {
    this.api.stop(execution.executionId).subscribe({
      next: () => this.refreshExecutions(job),
      error: (err) => (this.error = this.message(err)),
    });
  }

  restart(execution: ExecutionSummary, job: JobSummary): void {
    this.api.restart(execution.executionId).subscribe({
      next: () => this.refreshExecutions(job),
      error: (err) => (this.error = this.message(err)),
    });
  }

  remove(job: JobSummary): void {
    if (!confirm(`Delete dynamic job '${job.name}'?`)) {
      return;
    }
    this.api.deleteJob(job.name).subscribe({
      next: () => this.reload(),
      error: (err) => (this.error = this.message(err)),
    });
  }

  paramsText(execution: ExecutionSummary): string {
    return paramsToText(execution.parameters);
  }

  private refreshExecutions(job: JobSummary): void {
    this.api.jobExecutions(job.name).subscribe((execs) => (this.jobExecutions = execs));
    this.reload();
  }

  private message(err: unknown): string {
    const e = err as { error?: { message?: string }; message?: string };
    return e?.error?.message ?? e?.message ?? 'Request failed';
  }
}
