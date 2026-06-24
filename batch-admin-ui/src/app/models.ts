export interface JobSummary {
  name: string;
  dynamic: boolean;
  launchable: boolean;
  instanceCount: number;
  running: boolean;
  lastStatus: string | null;
  lastExecution: string | null;
}

export interface StepExecutionSummary {
  stepExecutionId: number;
  stepName: string;
  status: string;
  exitCode: string | null;
  exitDescription: string | null;
  startTime: string | null;
  endTime: string | null;
  durationMs: number | null;
  readCount: number;
  writeCount: number;
  commitCount: number;
  rollbackCount: number;
  filterCount: number;
  skipCount: number;
}

export interface ExecutionSummary {
  executionId: number;
  jobInstanceId: number;
  jobName: string;
  status: string;
  exitCode: string | null;
  exitDescription: string | null;
  createTime: string | null;
  startTime: string | null;
  endTime: string | null;
  durationMs: number | null;
  running: boolean;
  restartable: boolean;
  parameters: Record<string, string>;
  steps: StepExecutionSummary[];
}

export interface ProviderInfo {
  type: string;
  displayName: string;
  properties: Record<string, string>;
}

export interface ScheduleInfo {
  id: number;
  jobName: string;
  cron: string;
  description: string | null;
  enabled: boolean;
  parameters: Record<string, string>;
  nextExecution: string | null;
  createdAt: string | null;
}

export interface ObservabilitySummary {
  totalJobs: number;
  dynamicJobs: number;
  activeSchedules: number;
  runningExecutions: number;
  statusCounts: Record<string, number>;
  recentExecutions: ExecutionSummary[];
}

export interface StepDefinition {
  name: string;
  type: string;
  properties: Record<string, unknown>;
}

export interface CreateJobRequest {
  jobName: string;
  description?: string;
  steps: StepDefinition[];
}

export interface ScheduleRequest {
  jobName: string;
  cron: string;
  description?: string;
  enabled?: boolean;
  parameters?: Record<string, string>;
}
