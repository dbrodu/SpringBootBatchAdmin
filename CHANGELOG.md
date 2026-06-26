# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project aims
to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

**Building blocks derived from existing jobs**
- `ExistingStepCatalog` — automatically exposes the host's already-registered jobs as reusable
  building blocks, at two granularities: each **step** (type `<stepName>`, or `<jobName>.<stepName>`
  when names collide) and each **whole job's flow** (type `job:<jobName>` — all its steps, in order),
  so a new on-the-fly job can drop in steps the application already defines, with no code. Reuse shares
  the existing step instances; dynamic (component-created) jobs are excluded. Listed at
  `GET <base-path>/api/jobs/reusable-steps` and `…/reusable-jobs`. Toggle with
  `batch.admin.dynamic-jobs.reuse-existing-steps` (default `true`).
- The **Create job** GUI gains a **building-block picker** (dropdown of every composable block —
  providers, reusable steps and whole-job blocks) that appends a ready-made step line, so operators
  pick blocks instead of typing their types.
- The **Create job** / **Edit job** GUI gains a **drag-and-drop step-order** widget — drag the ⠿
  handle to reorder steps without hand-editing the text area (kept in sync with it,
  progressive-enhancement vanilla JS; the text area still works without JavaScript).

**GUI**
- The **Jobs** screen gains a **Schedule** column showing each job's next run (or *paused* + cron when
  disabled), linking to the Schedules screen — so the schedule is visible at a glance without leaving
  the page.

**Previewing and cloning jobs**
- **Preview a composition** before creating it — `POST <base-path>/api/jobs/preview` (and a *Preview
  steps* button on the **Create job** screen) returns the ordered, fully expanded list of steps the
  job would run (with `job:<name>` whole-job blocks expanded into their constituent steps), validating
  it without building, registering or persisting anything.
- **Clone an existing job** in one action — `POST <base-path>/api/jobs/<name>/clone` and a *Clone*
  button on the **Jobs** screen. A dynamic job is copied definition-for-definition; a declared host job
  is cloned by reusing its whole flow.
- **Edit a dynamic job** in place — `PUT <base-path>/api/jobs/<name>` and an *Edit* action on the
  **Jobs** screen that reopens the composer pre-filled with the job's steps. The new composition is
  built and validated before the old job is swapped out, so a bad edit never drops the running
  definition. (Declared host jobs cannot be edited.)
- **Export / import job definitions as JSON** to move dynamic jobs between environments —
  `GET <base-path>/api/jobs/export` (all) / `…/<name>/export` (one), and
  `POST <base-path>/api/jobs/import?overwrite=` (existing jobs are skipped, or overwritten when
  `overwrite=true`; names colliding with a declared job are reported as failed). The **Jobs** screen
  gains an *Import / export* panel (download + paste-to-import).
- **Version history & rollback** for dynamic jobs — every create/edit/rollback appends an immutable
  snapshot to a new `BATCH_ADMIN_JOB_DEFINITION_VERSION` table. View the history and **roll back** to a
  previous definition via `GET <base-path>/api/jobs/<name>/versions` and
  `POST <base-path>/api/jobs/<name>/rollback?version=N`, or the *History* action on the **Jobs**
  screen. A rollback is recorded as a new version, so it is itself reversible.

**Reusable SQL → JSON → target building blocks**
- `GenericSqlItemReader` / `GenericSqlItemReaderBuilder` — a configurable `ItemReader` that streams the
  rows of an arbitrary SQL query as `Map<String, Object>` (or maps to your own type) with no mapping
  code.
- `GenericSqlPagingItemReader` / `…Builder` — a paging counterpart for large result sets.
- `JsonItemProcessor` — turns reader rows into JSON documents.
- `GenericJsonItemWriter` writing to a pluggable `JsonDocumentSink`, with two sinks shipped:
  `OpenSearchBulkJsonSink` (the OpenSearch `_bulk` API, via the JDK `HttpClient`) and
  `LoggingJsonDocumentSink` (dry-run to the log).

**Composable `sql-export` job step (no-code SQL → OpenSearch/log export)**
- `StepProvider` SPI — contribute whole chunk-oriented steps (reader/processor/writer) to dynamic
  jobs, a richer counterpart to `TaskletProvider`.
- `SqlExportStepProvider` (type `sql-export`) — a paged SQL reader → `JsonItemProcessor` →
  `GenericJsonItemWriter` step targeting **OpenSearch** or the **log**. Exposed both as a dedicated
  **Create job** GUI form and through the REST API. Auto-configured when a `DataSource` is present.

**Metadata-driven parameters & SpEL** (integrate into a metadata-driven architecture)
- `MetadataService` SPI with a default `PropertiesMetadataService` backed by `batch.admin.metadata.*`;
  replace the bean to plug a real metadata catalog.
- `ValueResolver` — resolves `#{…}` **SpEL** template expressions in job parameters (launch time) and
  dynamic-job step properties (build time), against a **sandboxed** context exposing `metadata`,
  `today`, `now` and `timestamp` (read-only property access + instance methods only; type references
  and constructors are blocked). Toggle with `batch.admin.expressions.enabled`.

**Optional OAuth2 / OIDC security** (`batch.admin.security.enabled=true`, off by default)
- `BatchAdminSecurityAutoConfiguration` installs up to two filter chains **scoped to the component's
  own paths** (it never takes over the host app): the **REST API** (`<base-path>/api/**`) becomes a
  stateless **OAuth2 resource server** validating bearer JWTs, and the **GUI** uses interactive
  **OIDC login** — both configured from the standard `spring.security.oauth2.*` properties.
- Optional per-surface authority requirements `batch.admin.security.api-authority` /
  `…ui-authority`. The Spring Security / OAuth2 dependencies are **optional** (pulled in only when you
  add them), so consumers that don't opt in are unaffected.

**Pub/sub job-lifecycle events**
- `BatchEvent` / `BatchEventType` (`JOB_STARTED`, `JOB_COMPLETED`, `JOB_STOPPED`, `JOB_FAILED`) emitted
  for every administered job through a `BatchEventPublisher` abstraction.
- Default `ApplicationBatchEventPublisher` (in-process Spring `ApplicationEvent` + log, zero
  infrastructure) and an opt-in `RabbitBatchEventPublisher` (`batch.admin.events.broker=rabbit`,
  Spring AMQP **optional**) that fans events to a RabbitMQ topic exchange keyed
  `<prefix>.<jobName>.<eventType>`. Host apps can register their own `BatchEventPublisher` for any
  other transport. Configured under `batch.admin.events.*`; publisher failures never break the job.

### Changed
- The component can now **optionally secure itself** (see above). When the opt-in security layer is
  disabled (the default), protecting `batch.admin.base-path` remains the host application's
  responsibility.
- `DynamicJobService` and the host-job registrar now attach a **list** of component listeners (log
  capture **and** event publishing) to every job, instead of the single log listener.

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

[Unreleased]: https://github.com/dbrodu/SpringBootBatchAdmin
[0.1.0]: https://github.com/dbrodu/SpringBootBatchAdmin
