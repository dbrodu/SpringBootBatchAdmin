# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project aims
to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] — 2026-06-24

Initial version: a **self-initializing, job-agnostic administration component** for Spring Batch jobs
wrapped in Spring Boot — a lightweight, embedded replacement for Spring Cloud Data Flow that ships
inside a single Spring Boot application.

### Added

**Core / auto-configuration**
- Auto-configuration that activates when Spring Batch, a `DataSource` and Spring Web are present.
- Automatic discovery of existing `Job` beans, registered into the `JobRegistry` so they become
  launchable — the component stays agnostic of the jobs that already exist.
- Non-intrusive persistence via plain JDBC (`BATCH_ADMIN_JOB_DEFINITION` and
  `BATCH_ADMIN_JOB_SCHEDULE`, created with `CREATE TABLE IF NOT EXISTS`; H2 and PostgreSQL
  supported), never touching the host's JPA/transaction configuration.
- Configuration under `batch.admin.*` (base path, UI toggle, scheduling, dynamic jobs, observability,
  logs).

**Job administration**
- Start jobs asynchronously (dedicated `TaskExecutorJobLauncher`; the host's own launcher is
  untouched), stop, restart, and abandon executions.
- Browse jobs, executions and step executions with read/write/commit/rollback/skip counters.

**On-the-fly job creation**
- `TaskletProvider` SPI so host applications expose their domain operations as building blocks.
- Compose jobs at runtime from those blocks; definitions are persisted and re-registered on restart.
- Generic providers shipped: `log`, `sleep` (interruptible), and an opt-in `command` (OS command).

**Scheduling**
- Per-job cron schedules, persisted and re-armed on startup, with the next fire time computed.
- **Natural-language schedule input** (French/English) converted to Spring cron server-side
  (e.g. `tous les jours à 2h30` → `0 30 2 * * *`); raw cron expressions and macros (`@daily`, …)
  still pass through. Applies to both the GUI and the REST API.

**Per-execution logs**
- Capture of the log lines emitted while a job runs, attributed to the execution via an MDC key and a
  Logback appender, kept in a bounded in-memory buffer.
- Read them per execution with a **configurable minimum level** (GUI selector and `?level=` on the
  API); capture level, default read level and buffer sizes are configurable.

**Observability**
- Aggregated dashboard summary (jobs, dynamic jobs, active schedules, running executions, status
  breakdown, recent executions).
- Micrometer gauges `batch.admin.executions.running` and `batch.admin.schedules.active` (plus the
  Spring Batch `spring.batch.job` timers), exposed through Actuator.

**GUI**
- Server-rendered **Thymeleaf** GUI (dashboard, jobs, on-the-fly job builder, executions with step
  detail and logs, schedules), served on the application port under `batch.admin.base-path`. All
  actions are plain HTML form POSTs (Post/Redirect/Get) — no client-side framework or Node build.

**REST API**
- JSON API under `<base-path>/api` covering jobs, providers, executions (incl. logs and level list),
  schedules and the observability summary.

**Tooling & documentation**
- A `SessionStart` hook for Claude Code on the web (warms the Maven cache, provisions Playwright) and
  `scripts/take-screenshots.sh` to smoke-test the GUI and capture screenshots.
- README, a step-by-step **[migration guide](docs/MIGRATION.md)**, and bundled GUI screenshots.

### Notes
- The GUI is server-rendered with Thymeleaf, embedding everything in one Spring Boot application. An
  Angular-based GUI was the initial implementation and was superseded by the Thymeleaf one.
- The component adds no authentication; secure `batch.admin.base-path` (e.g. with Spring Security) in
  exposed environments.
- Requirements: Java 21+, Spring Boot 3.3 / Spring Batch 5, a relational `DataSource`; per-execution
  log capture requires Logback (the Spring Boot default).

[0.1.0]: https://github.com/dbrodu/SpringBootBatchAdmin
