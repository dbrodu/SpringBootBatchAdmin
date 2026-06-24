import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BatchAdminService } from './batch-admin.service';
import { formatDate, paramsToText } from './format';
import { JobSummary, ScheduleInfo } from './models';

@Component({
  selector: 'ba-schedules',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="panel">
      <h3>Schedule a job</h3>
      <form class="inline-form" (ngSubmit)="create()">
        <select [(ngModel)]="jobName" name="job">
          <option value="" disabled>Choose a job…</option>
          <option *ngFor="let j of jobs" [value]="j.name">{{ j.name }}</option>
        </select>
        <input [(ngModel)]="cron" name="cron" placeholder="cron e.g. 0 0 2 * * *" class="cron-input" />
        <input [(ngModel)]="description" name="desc" placeholder="description (optional)" />
        <button type="submit" class="btn btn-primary" [disabled]="!jobName || !cron">Add schedule</button>
      </form>
      <p class="hint">Spring cron: second minute hour day-of-month month day-of-week.</p>
      <p *ngIf="error" class="error">{{ error }}</p>
    </div>

    <table class="grid">
      <thead>
        <tr><th>Job</th><th>Cron</th><th>Next run</th><th>Parameters</th><th>Enabled</th><th>Actions</th></tr>
      </thead>
      <tbody>
        <tr *ngFor="let s of schedules">
          <td>{{ s.jobName }}</td>
          <td><code>{{ s.cron }}</code><div class="muted" *ngIf="s.description">{{ s.description }}</div></td>
          <td>{{ formatDate(s.nextExecution) }}</td>
          <td class="muted">{{ paramsText(s) }}</td>
          <td>
            <label class="switch">
              <input type="checkbox" [checked]="s.enabled" (change)="toggle(s)" />
              <span>{{ s.enabled ? 'on' : 'off' }}</span>
            </label>
          </td>
          <td class="actions">
            <button class="btn btn-small btn-danger" (click)="remove(s)">Delete</button>
          </td>
        </tr>
        <tr *ngIf="schedules.length === 0"><td colspan="6" class="muted">No schedules defined.</td></tr>
      </tbody>
    </table>
  `,
})
export class SchedulesComponent implements OnInit {
  schedules: ScheduleInfo[] = [];
  jobs: JobSummary[] = [];
  jobName = '';
  cron = '';
  description = '';
  error: string | null = null;

  readonly formatDate = formatDate;

  constructor(private readonly api: BatchAdminService) {}

  ngOnInit(): void {
    this.reload();
    this.api.listJobs().subscribe((jobs) => (this.jobs = jobs.filter((j) => j.launchable)));
  }

  reload(): void {
    this.api.listSchedules().subscribe((s) => (this.schedules = s));
  }

  create(): void {
    this.error = null;
    this.api
      .createSchedule({ jobName: this.jobName, cron: this.cron.trim(), description: this.description.trim() })
      .subscribe({
        next: () => {
          this.cron = '';
          this.description = '';
          this.reload();
        },
        error: (err) => {
          const e = err as { error?: { message?: string } };
          this.error = e?.error?.message ?? 'Could not create schedule';
        },
      });
  }

  toggle(schedule: ScheduleInfo): void {
    this.api.setScheduleEnabled(schedule.id, !schedule.enabled).subscribe(() => this.reload());
  }

  remove(schedule: ScheduleInfo): void {
    if (!confirm(`Delete schedule for '${schedule.jobName}'?`)) {
      return;
    }
    this.api.deleteSchedule(schedule.id).subscribe(() => this.reload());
  }

  paramsText(schedule: ScheduleInfo): string {
    return paramsToText(schedule.parameters);
  }
}
