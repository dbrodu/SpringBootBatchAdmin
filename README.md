# Spring Boot Batch Admin

A **job-agnostic administration component** for Spring Batch jobs wrapped in Spring Boot. It
self-initializes when your application starts and exposes, on the application's own port and a
dedicated route, everything you need to operate your jobs:

- **create jobs on the fly** from reusable building blocks;
- **administer jobs from a browser GUI** — a **server-rendered Thymeleaf UI**, so the whole thing
  ships inside a single Spring Boot application with no separate front-end to build or deploy;
- **expose the full observability** required to run those jobs (REST + Micrometer metrics).

It is meant as a lightweight, embedded replacement for **Spring Cloud Data Flow** inside a single
Spring Boot Batch application.

> This branch uses **Thymeleaf** for the GUI (everything embedded in one Spring Boot JAR). An
> alternative Angular-based GUI lives on the `main` branch.

---

## Modules

| Module | Description |
| ------ | ----------- |
| `batch-admin-starter` | The auto-configured, reusable component: REST API, dynamic job engine, scheduler, observability, and the server-rendered Thymeleaf GUI. |
| `batch-admin-sample` | A runnable demo Spring Boot Batch application that includes the starter and declares a couple of jobs. |

---

## Quick start

```bash
mvn -q -DskipTests install
java -jar batch-admin-sample/target/spring-boot-batch-admin-sample-0.1.0-SNAPSHOT.jar
```

Open the GUI at **http://localhost:8080/batch-admin** and the REST API under
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
launchable, serves its Thymeleaf GUI, and never touches your application's own JPA/transaction
configuration (its own state is persisted through plain JDBC in two `BATCH_ADMIN_*` tables created
automatically). The GUI pulls in `spring-boot-starter-thymeleaf` transitively.

---

## What it does

### 1. Administer existing jobs
Your jobs — declared as ordinary Spring beans — are discovered automatically. From the GUI or API you
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

In the GUI's **Create job** screen you describe the steps, one per line:

```
extract = import-file (path=/in/data.csv)
wait    = sleep (millis=2000)
notify  = log (message=done)
```

Dynamically created jobs are **persisted** and re-registered on every restart.

### 3. Schedule jobs
Any launchable job can be given a **cron schedule**. Schedules are persisted and re-armed on startup.
Spring cron syntax is used (`second minute hour day-of-month month day-of-week`).

### 4. Observe everything
The dashboard aggregates jobs, dynamic jobs, active schedules, currently running executions and a
status breakdown, plus a live feed (auto-refreshing) of recent executions with per-step
read/write/commit/skip counters. Job timing is additionally published to **Micrometer**
(`spring.batch.job` timers, plus `batch.admin.executions.running` and `batch.admin.schedules.active`
gauges), available through Spring Boot Actuator.

---

## GUI routes

The browser GUI is served under `${batch.admin.base-path}` (default `/batch-admin`):

| Path | Screen |
| ---- | ------ |
| `/batch-admin` | Dashboard |
| `/batch-admin/jobs` | Jobs list, start & delete |
| `/batch-admin/jobs/new` | Create a job on the fly |
| `/batch-admin/executions` | Executions & step detail |
| `/batch-admin/schedules` | Cron schedules |

All actions are plain HTML form POSTs that redirect back (Post/Redirect/Get) — no client-side
framework or JavaScript build is involved.

---

## REST API

The same capabilities are available as a JSON API under `${batch.admin.base-path}/api`
(default `/batch-admin/api`), useful for automation:

| Method & path | Purpose |
| ------------- | ------- |
| `GET /jobs` · `GET /jobs/{name}` | List / detail |
| `GET /jobs/providers` | Available building blocks |
| `POST /jobs` · `DELETE /jobs/{name}` | Create / delete a dynamic job |
| `POST /jobs/{name}/executions` | Start a job (async) |
| `GET /jobs/{name}/executions` | Execution history of a job |
| `GET /executions?limit=` · `GET /executions/{id}` | Recent / detail with steps |
| `POST /executions/{id}/stop` · `/restart` · `/abandon` | Control an execution |
| `GET /schedules` · `POST /schedules` · `PUT /schedules/{id}/enabled?value=` · `DELETE /schedules/{id}` | Manage schedules |
| `GET /observability/summary` | Dashboard snapshot |

---

## Configuration

All properties are optional and prefixed with `batch.admin`:

```yaml
batch:
  admin:
    enabled: true                 # master switch
    base-path: /batch-admin       # route for the GUI and (with /api) the REST API
    ui-enabled: true              # serve the Thymeleaf GUI
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

## Requirements

- Java 21+
- A relational `DataSource` (the component creates its two tables with `CREATE TABLE IF NOT EXISTS`;
  H2 and PostgreSQL are supported out of the box)
