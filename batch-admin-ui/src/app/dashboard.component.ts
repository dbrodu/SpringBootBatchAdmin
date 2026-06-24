import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { BatchAdminService } from './batch-admin.service';
import { formatDate, formatDuration, statusClass } from './format';
import { ObservabilitySummary } from './models';

@Component({
  selector: 'ba-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section *ngIf="summary as s">
      <div class="cards">
        <div class="card"><span class="card-value">{{ s.totalJobs }}</span><span class="card-label">Jobs</span></div>
        <div class="card"><span class="card-value">{{ s.dynamicJobs }}</span><span class="card-label">Dynamic jobs</span></div>
        <div class="card"><span class="card-value">{{ s.activeSchedules }}</span><span class="card-label">Active schedules</span></div>
        <div class="card" [class.card-live]="s.runningExecutions > 0">
          <span class="card-value">{{ s.runningExecutions }}</span><span class="card-label">Running now</span>
        </div>
      </div>

      <h3>Executions by status</h3>
      <div class="status-bar">
        <ng-container *ngFor="let entry of statusEntries(s)">
          <span [class]="statusClass(entry[0])">{{ entry[0] }}: {{ entry[1] }}</span>
        </ng-container>
        <span *ngIf="statusEntries(s).length === 0" class="muted">No executions yet.</span>
      </div>

      <h3>Recent executions</h3>
      <table class="grid">
        <thead>
          <tr><th>#</th><th>Job</th><th>Status</th><th>Started</th><th>Duration</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let e of s.recentExecutions">
            <td>{{ e.executionId }}</td>
            <td>{{ e.jobName }}</td>
            <td><span [class]="statusClass(e.status)">{{ e.status }}</span></td>
            <td>{{ formatDate(e.startTime ?? e.createTime) }}</td>
            <td>{{ formatDuration(e.durationMs) }}</td>
          </tr>
          <tr *ngIf="s.recentExecutions.length === 0"><td colspan="5" class="muted">Nothing has run yet.</td></tr>
        </tbody>
      </table>
    </section>
    <p *ngIf="!summary" class="muted">Loading…</p>
  `,
})
export class DashboardComponent implements OnInit, OnDestroy {
  summary: ObservabilitySummary | null = null;
  private sub?: Subscription;

  readonly statusClass = statusClass;
  readonly formatDate = formatDate;
  readonly formatDuration = formatDuration;

  constructor(private readonly api: BatchAdminService) {}

  ngOnInit(): void {
    this.sub = interval(3000)
      .pipe(
        startWith(0),
        switchMap(() => this.api.summary()),
      )
      .subscribe((s) => (this.summary = s));
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  statusEntries(s: ObservabilitySummary): Array<[string, number]> {
    return Object.entries(s.statusCounts);
  }
}
