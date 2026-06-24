import { HttpClient } from '@angular/common/http';
import { Inject, Injectable, InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateJobRequest,
  ExecutionSummary,
  JobSummary,
  ObservabilitySummary,
  ProviderInfo,
  ScheduleInfo,
  ScheduleRequest,
} from './models';

/** Base URL of the admin REST API. Provided at bootstrap so the component is portable. */
export const BATCH_ADMIN_API_BASE = new InjectionToken<string>('BATCH_ADMIN_API_BASE');

@Injectable({ providedIn: 'root' })
export class BatchAdminService {
  constructor(
    private readonly http: HttpClient,
    @Inject(BATCH_ADMIN_API_BASE) private readonly base: string,
  ) {}

  // Jobs ---------------------------------------------------------------------
  listJobs(): Observable<JobSummary[]> {
    return this.http.get<JobSummary[]>(`${this.base}/jobs`);
  }

  getJob(name: string): Observable<JobSummary> {
    return this.http.get<JobSummary>(`${this.base}/jobs/${encodeURIComponent(name)}`);
  }

  listProviders(): Observable<ProviderInfo[]> {
    return this.http.get<ProviderInfo[]>(`${this.base}/jobs/providers`);
  }

  startJob(name: string, parameters: Record<string, string>): Observable<ExecutionSummary> {
    return this.http.post<ExecutionSummary>(
      `${this.base}/jobs/${encodeURIComponent(name)}/executions`,
      { parameters },
    );
  }

  createJob(request: CreateJobRequest): Observable<JobSummary> {
    return this.http.post<JobSummary>(`${this.base}/jobs`, request);
  }

  deleteJob(name: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/jobs/${encodeURIComponent(name)}`);
  }

  jobExecutions(name: string, limit = 20): Observable<ExecutionSummary[]> {
    return this.http.get<ExecutionSummary[]>(
      `${this.base}/jobs/${encodeURIComponent(name)}/executions?limit=${limit}`,
    );
  }

  // Executions ---------------------------------------------------------------
  recentExecutions(limit = 50): Observable<ExecutionSummary[]> {
    return this.http.get<ExecutionSummary[]>(`${this.base}/executions?limit=${limit}`);
  }

  getExecution(id: number): Observable<ExecutionSummary> {
    return this.http.get<ExecutionSummary>(`${this.base}/executions/${id}`);
  }

  stop(id: number): Observable<ExecutionSummary> {
    return this.http.post<ExecutionSummary>(`${this.base}/executions/${id}/stop`, {});
  }

  restart(id: number): Observable<ExecutionSummary> {
    return this.http.post<ExecutionSummary>(`${this.base}/executions/${id}/restart`, {});
  }

  abandon(id: number): Observable<ExecutionSummary> {
    return this.http.post<ExecutionSummary>(`${this.base}/executions/${id}/abandon`, {});
  }

  // Schedules ----------------------------------------------------------------
  listSchedules(): Observable<ScheduleInfo[]> {
    return this.http.get<ScheduleInfo[]>(`${this.base}/schedules`);
  }

  createSchedule(request: ScheduleRequest): Observable<ScheduleInfo> {
    return this.http.post<ScheduleInfo>(`${this.base}/schedules`, request);
  }

  setScheduleEnabled(id: number, value: boolean): Observable<ScheduleInfo> {
    return this.http.put<ScheduleInfo>(`${this.base}/schedules/${id}/enabled?value=${value}`, {});
  }

  deleteSchedule(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/schedules/${id}`);
  }

  // Observability ------------------------------------------------------------
  summary(): Observable<ObservabilitySummary> {
    return this.http.get<ObservabilitySummary>(`${this.base}/observability/summary`);
  }
}
