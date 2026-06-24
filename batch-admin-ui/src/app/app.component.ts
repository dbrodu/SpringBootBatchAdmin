import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { CreateJobComponent } from './create-job.component';
import { DashboardComponent } from './dashboard.component';
import { ExecutionsComponent } from './executions.component';
import { JobsComponent } from './jobs.component';
import { SchedulesComponent } from './schedules.component';

type Tab = 'dashboard' | 'jobs' | 'create' | 'executions' | 'schedules';

@Component({
  selector: 'ba-root',
  standalone: true,
  imports: [
    CommonModule,
    DashboardComponent,
    JobsComponent,
    CreateJobComponent,
    ExecutionsComponent,
    SchedulesComponent,
  ],
  template: `
    <header class="app-header">
      <div class="brand">⚙️ <span>Spring Batch Admin</span></div>
      <nav class="tabs">
        <button [class.active]="tab === 'dashboard'" (click)="tab = 'dashboard'">Dashboard</button>
        <button [class.active]="tab === 'jobs'" (click)="tab = 'jobs'">Jobs</button>
        <button [class.active]="tab === 'create'" (click)="tab = 'create'">Create job</button>
        <button [class.active]="tab === 'executions'" (click)="tab = 'executions'">Executions</button>
        <button [class.active]="tab === 'schedules'" (click)="tab = 'schedules'">Schedules</button>
      </nav>
    </header>

    <main class="app-main">
      <ba-dashboard *ngIf="tab === 'dashboard'"></ba-dashboard>
      <ba-jobs *ngIf="tab === 'jobs'"></ba-jobs>
      <ba-create-job *ngIf="tab === 'create'" (created)="onJobCreated()"></ba-create-job>
      <ba-executions *ngIf="tab === 'executions'"></ba-executions>
      <ba-schedules *ngIf="tab === 'schedules'"></ba-schedules>
    </main>

    <footer class="app-footer">
      Self-hosted job orchestration — a Spring Cloud Data Flow replacement embedded in your Spring Boot Batch app.
    </footer>
  `,
})
export class AppComponent {
  tab: Tab = 'dashboard';

  onJobCreated(): void {
    this.tab = 'jobs';
  }
}
