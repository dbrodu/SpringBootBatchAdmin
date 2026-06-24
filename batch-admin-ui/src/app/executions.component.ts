import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { BatchAdminService } from './batch-admin.service';
import { formatDate, formatDuration, paramsToText, statusClass } from './format';
import { ExecutionSummary } from './models';

@Component({
  selector: 'ba-executions',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toolbar">
      <label class="switch">
        <input type="checkbox" [checked]="autoRefresh" (change)="toggleAuto()" />
        <span>Auto-refresh</span>
      </label>
      <button class="btn" (click)="reload()">↻ Refresh</button>
    </div>

    <table class="grid">
      <thead>
        <tr><th></th><th>#</th><th>Job</th><th>Status</th><th>Params</th><th>Started</th><th>Duration</th><th>Actions</th></tr>
      </thead>
      <tbody>
        <ng-container *ngFor="let e of executions">
          <tr>
            <td><button class="link" (click)="toggle(e)">{{ open === e.executionId ? '▾' : '▸' }}</button></td>
            <td>{{ e.executionId }}</td>
            <td>{{ e.jobName }}</td>
            <td><span [class]="statusClass(e.status)">{{ e.status }}</span></td>
            <td class="muted">{{ paramsText(e) }}</td>
            <td>{{ formatDate(e.startTime ?? e.createTime) }}</td>
            <td>{{ formatDuration(e.durationMs) }}</td>
            <td class="actions">
              <button class="btn btn-small" *ngIf="e.running" (click)="stop(e)">⏹ Stop</button>
              <button class="btn btn-small" *ngIf="e.restartable" (click)="restart(e)">↻ Restart</button>
            </td>
          </tr>
          <tr *ngIf="open === e.executionId">
            <td colspan="8" class="subrow">
              <div *ngIf="detail as d">
                <div class="muted" *ngIf="d.exitDescription">{{ d.exitDescription }}</div>
                <table class="grid grid-inner">
                  <thead>
                    <tr><th>Step</th><th>Status</th><th>Read</th><th>Write</th><th>Commit</th><th>Skip</th><th>Duration</th></tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let s of d.steps">
                      <td>{{ s.stepName }}</td>
                      <td><span [class]="statusClass(s.status)">{{ s.status }}</span></td>
                      <td>{{ s.readCount }}</td>
                      <td>{{ s.writeCount }}</td>
                      <td>{{ s.commitCount }}</td>
                      <td>{{ s.skipCount }}</td>
                      <td>{{ formatDuration(s.durationMs) }}</td>
                    </tr>
                    <tr *ngIf="d.steps.length === 0"><td colspan="7" class="muted">No steps recorded.</td></tr>
                  </tbody>
                </table>
              </div>
            </td>
          </tr>
        </ng-container>
        <tr *ngIf="executions.length === 0"><td colspan="8" class="muted">No executions.</td></tr>
      </tbody>
    </table>
  `,
})
export class ExecutionsComponent implements OnInit, OnDestroy {
  executions: ExecutionSummary[] = [];
  open: number | null = null;
  detail: ExecutionSummary | null = null;
  autoRefresh = true;
  private sub?: Subscription;

  readonly statusClass = statusClass;
  readonly formatDate = formatDate;
  readonly formatDuration = formatDuration;

  constructor(private readonly api: BatchAdminService) {}

  ngOnInit(): void {
    this.startAuto();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  toggleAuto(): void {
    this.autoRefresh = !this.autoRefresh;
    this.sub?.unsubscribe();
    if (this.autoRefresh) {
      this.startAuto();
    }
  }

  reload(): void {
    this.api.recentExecutions(50).subscribe((e) => (this.executions = e));
  }

  toggle(execution: ExecutionSummary): void {
    if (this.open === execution.executionId) {
      this.open = null;
      this.detail = null;
      return;
    }
    this.open = execution.executionId;
    this.detail = null;
    this.api.getExecution(execution.executionId).subscribe((d) => (this.detail = d));
  }

  stop(execution: ExecutionSummary): void {
    this.api.stop(execution.executionId).subscribe(() => this.reload());
  }

  restart(execution: ExecutionSummary): void {
    this.api.restart(execution.executionId).subscribe(() => this.reload());
  }

  paramsText(execution: ExecutionSummary): string {
    return paramsToText(execution.parameters);
  }

  private startAuto(): void {
    this.sub = interval(3000)
      .pipe(
        startWith(0),
        switchMap(() => this.api.recentExecutions(50)),
      )
      .subscribe((e) => {
        this.executions = e;
        if (this.open != null) {
          const match = e.find((x) => x.executionId === this.open);
          if (match) {
            this.api.getExecution(this.open).subscribe((d) => (this.detail = d));
          }
        }
      });
  }
}
