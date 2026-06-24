# Spring Boot Batch Admin

A **job-agnostic administration component** for Spring Batch jobs wrapped in Spring Boot. It
self-initializes when your application starts and exposes, on the application's own port and a
dedicated route, everything you need to operate your jobs:

- **create jobs on the fly** from reusable building blocks;
- **administer jobs from a browser GUI** (an Angular app) served at a dedicated route;
- **expose the full observability** required to run those jobs (REST + Micrometer metrics).

It is meant as a lightweight, embedded replacement for **Spring Cloud Data Flow** inside a single
Spring Boot Batch application, with the orchestration GUI written as an Angular component you can
also drop into your own Angular front-end.

---

## Modules

| Module | Description |
| ------ | ----------- |
| `batch-admin-starter` | The auto-configured, reusable component: REST API, dynamic job engine, scheduler, observability, and the bundled Angular GUI. |
| `batch-admin-ui` | The Angular orchestration component (source). Builds into the starter's static resources. |
| `batch-admin-sample` | A runnable demo Spring Boot Batch application that includes the starter and declares a couple of jobs. |

---

## Quick start

```bash
# Build everything (uses the prebuilt Angular GUI committed in the starter)
mvn -q -DskipTests install

# Run the sample app
java -jar batch-admin-sample/target/spring-boot-batch-admin-sample-0.1.0-SNAPSHOT.jar
```

Then open the GUI at **http://localhost:8080/batch-admin** and the REST API under
**http://localhost:8080/batch-admin/api**.

### Use it in your own app

Add the dependency to any Spring Boot Batch application:

```xml
<dependency>
    <groupId>io.batchadmin</groupId>
    <artifactId>spring-boot-batch-admin-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

That is all. The component auto-configures itself as soon as Spring Batch, a `DataSource` and Spring
Web are on the classpath. It discovers your existing `Job` beans, registers them so they are
launchable, and never touches your application's own JPA/transaction configuration (its own state is
persisted through plain JDBC, in two `BATCH_ADMIN_*` tables created automatically).

---

## What it does

### 1. Administer existing jobs
Your jobs — declared as ordinary Spring beans — are discovered automatically. From the API or GUI you
can **start** them (asynchronously, so the call returns immediately), **stop** a running execution,
**restart** a failed/stopped one, **abandon**, and browse the full execution/step history.

### 2. Create jobs on the fly
Jobs can be composed at runtime from **building blocks**. A building block is a
`TaskletProvider` bean; the component ships generic ones (`log`, `sleep`, and an opt-in `command`)
and **your application contributes its own** simply by declaring a bean:

```java
@Component
public class ImportFileTaskletProvider implements TaskletProvider {
    public String getType() { return "import-file"; }
    public Tasklet create(Map<String, Object> properties) {
        return (contribution, chunkContext) -> { /* ... */ return RepeatStatus.FINISHED; };
    }
}
```

Operators then assemble these blocks into a multi-step job from the GUI. Dynamically created jobs are
**persisted** and re-registered on every restart.

### 3. Schedule jobs
Any launchable job can be given one or more **cron schedules**. Schedules are persisted and re-armed
on startup. Spring cron syntax is used (`second minute hour day-of-month month day-of-week`).

### 4. Observe everything
A dashboard aggregates jobs, dynamic jobs, active schedules, currently running executions and a
status breakdown, plus a live feed of recent executions with per-step read/write/commit/skip
counters. Job timing is additionally published to **Micrometer** (`spring.batch.job` timers, plus
`batch.admin.executions.running` and `batch.admin.schedules.active` gauges), available through
Spring Boot Actuator.

---

## REST API

All paths are relative to `${batch.admin.base-path}/api` (default `/batch-admin/api`).

| Method & path | Purpose |
| ------------- | ------- |
| `GET /jobs` | List all jobs |
| `GET /jobs/{name}` | Job detail |
| `GET /jobs/providers` | Available building blocks (tasklet providers) |
| `POST /jobs` | Create a job on the fly |
| `DELETE /jobs/{name}` | Delete a dynamic job |
| `POST /jobs/{name}/executions` | Start a job (async); body `{ "parameters": { ... } }` |
| `GET /jobs/{name}/executions` | Execution history of a job |
| `GET /executions?limit=` | Recent executions across all jobs |
| `GET /executions/{id}` | Execution detail with steps |
| `POST /executions/{id}/stop` | Stop a running execution |
| `POST /executions/{id}/restart` | Restart a failed/stopped execution |
| `POST /executions/{id}/abandon` | Abandon an execution |
| `GET /schedules` · `POST /schedules` | List / create cron schedules |
| `PUT /schedules/{id}` · `PUT /schedules/{id}/enabled?value=` · `DELETE /schedules/{id}` | Update / toggle / delete |
| `GET /observability/summary` | Dashboard snapshot |
| `GET /observability/last-status` | Last status per job |

---

## Configuration

All properties are optional and prefixed with `batch.admin`:

```yaml
batch:
  admin:
    enabled: true                 # master switch
    base-path: /batch-admin       # route for the GUI and (with /api) the REST API
    ui-enabled: true              # serve the Angular GUI from the app port
    scheduling:
      enabled: true
      pool-size: 4
    dynamic-jobs:
      enabled: true
      allow-command-tasklets: false   # enable the OS-command building block (trusted envs only)
    observability:
      recent-executions-limit: 100
      metrics-enabled: true
```

---

## Developing the Angular GUI

The GUI lives in `batch-admin-ui` and builds straight into the starter's
`src/main/resources/static/batch-admin`. The compiled assets are committed so a plain `mvn install`
needs no Node.

```bash
cd batch-admin-ui
npm install
npm run build          # production build into the starter's static resources
npm start              # dev server on :4200, proxying the API to :8080
```

To rebuild the GUI as part of the Maven build (downloads Node automatically):

```bash
mvn -Pui -q -DskipTests install
```

The Angular pieces (`BatchAdminService`, the models and the standalone components) are reusable, so
you can embed the same orchestration component inside your own Angular front-end by pointing
`BATCH_ADMIN_API_BASE` at your backend.

---

## Requirements

- Java 21+
- A relational `DataSource` (the component creates its two tables with `CREATE TABLE IF NOT EXISTS`;
  H2 and PostgreSQL are supported out of the box)
- Node 22+ only if you want to rebuild the GUI
