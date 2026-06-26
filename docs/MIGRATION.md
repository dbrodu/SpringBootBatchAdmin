# Migrating your existing Spring Batch jobs into Spring Boot Batch Admin

This guide explains how to onboard the Spring Batch jobs you already have into the admin component,
so you can run, stop, restart, schedule and observe them (and read their logs) from the GUI and the
REST API — without rewriting them.

**The golden rule: you keep your jobs as they are.** The component *wraps* your application; it does
not require you to change how your jobs are written. Most migrations are just "add the dependency,
make sure your jobs are beans, disable auto-run, secure the route".

---

## 1. Prerequisites

| Requirement | Why |
| ----------- | --- |
| Java 21+ | The starter targets Java 21. |
| Spring Boot 3.3.x, Spring Batch 5 | The component is built on these APIs. |
| A relational `DataSource` | Spring Batch needs one; the component reuses it. |
| Spring Batch meta-data tables | The standard `BATCH_*` tables must exist (see §4). |
| Logback (Spring Boot default) | Required only for per-execution **log capture** (§9). |

The component **auto-activates** when Spring Batch, a `DataSource` and Spring Web are on the
classpath. Concretely it is conditional on the beans `JobRepository`, `JobExplorer`, `JobRegistry`
and `DataSource`, which Spring Boot auto-configures for any batch application.

---

## 2. Add the dependency

```xml
<dependency>
    <groupId>io.batchadmin</groupId>
    <artifactId>spring-boot-batch-admin-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

It transitively brings `spring-boot-starter-web`, `-thymeleaf`, `-jdbc` and `-actuator`. If your app
is not already a web app, it becomes one (the GUI/API are served on the application port).

---

## 3. Make sure your jobs are discoverable

The component discovers every Spring-managed `Job` bean and registers it into the `JobRegistry` so it
becomes **launchable**. Two things to check:

1. **Your jobs are `@Bean`s** (or otherwise Spring-managed). Jobs created only inside a
   `CommandLineRunner`, or built ad-hoc and never exposed as beans, are not discovered.

   ```java
   @Bean
   public Job invoiceJob(JobRepository repo, PlatformTransactionManager tx) {
       return new JobBuilder("invoiceJob", repo)
               .start(extractStep(repo, tx))
               .next(loadStep(repo, tx))
               .build();
   }
   ```

2. **Job names are stable and unique.** The component keys everything on `job.getName()` (the name
   you pass to `JobBuilder`, not the bean name). Two jobs with the same name will collide.

> Jobs that have run before but are no longer registered (e.g. removed from the code) still appear in
> the **Jobs** list as *unregistered* (history is visible) but cannot be launched.

### Already using `@EnableBatchProcessing`?

That is fine — it still provides the `JobRepository`, `JobExplorer`, `JobRegistry` and `JobLauncher`
beans the component needs. You do not have to remove it. (If you have a *custom* `JobOperator` bean,
the component backs off and reuses yours.)

---

## 4. Database

Spring Batch stores its run metadata in `BATCH_*` tables. In production these are usually created by
your DBA or a migration tool (Flyway/Liquibase). For dev/test you can let Boot create them:

```yaml
spring:
  batch:
    jdbc:
      initialize-schema: always   # dev/test only; use a migration tool in prod
```

The admin component persists its own small state (dynamic job definitions and schedules) in two
extra tables, **`BATCH_ADMIN_JOB_DEFINITION`** and **`BATCH_ADMIN_JOB_SCHEDULE`**, which it creates
automatically with `CREATE TABLE IF NOT EXISTS` (H2 and PostgreSQL are supported out of the box). It
uses plain JDBC on the shared `DataSource` and **never touches your JPA/ORM or transaction
configuration**.

---

## 5. Stop jobs from auto-running at startup

If today your app runs jobs at boot, decide whether you still want that once the admin drives them.
Usually you want launches to be **on demand** from the GUI/API:

```yaml
spring:
  batch:
    job:
      enabled: false   # do not run jobs automatically on startup
```

(With Spring Boot 3 nothing runs at startup unless `spring.batch.job.name` is set, but making this
explicit avoids surprises.)

---

## 6. Secure the endpoints (important)

The GUI and API are exposed on the application port under `batch.admin.base-path`
(default `/batch-admin`). By default the component adds **no** authentication, so if your app is
reachable by anyone you must protect the route. You have two options.

### Option A — let the component secure itself (OAuth2 / OIDC, opt-in)

Set `batch.admin.security.enabled=true` and add Spring Security (the OAuth2 starters). The component
then installs two filter chains **scoped to its own paths** — it never takes over the rest of your
app: the **REST API** becomes a stateless **OAuth2 resource server** (bearer JWTs) and the **GUI**
uses interactive **OIDC login**, both configured from the standard `spring.security.oauth2.*`
properties:

```yaml
batch:
  admin:
    security:
      enabled: true
      api-authority: SCOPE_batch.admin   # optional; any authenticated token otherwise
      ui-authority: ROLE_BATCH_ADMIN     # optional; any authenticated OIDC user otherwise
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://idp.example.com/realms/batch
      client:
        registration:
          batch-admin: { client-id: batch-admin, client-secret: ${OIDC_CLIENT_SECRET}, scope: openid }
        provider:
          batch-admin: { issuer-uri: https://idp.example.com/realms/batch }
```

The two chains are independent, so an API-only or GUI-only deployment works without the other.

### Option B — protect the route with your own Spring Security

If you already run your own filter chain (or don't use OAuth2), just guard the base path yourself:

```java
@Bean
SecurityFilterChain security(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/batch-admin/**").hasRole("BATCH_ADMIN")
            .anyRequest().permitAll())
        .httpBasic(Customizer.withDefaults());
    return http.build();
}
```

Either way, you can also disable parts you don't want exposed: `batch.admin.ui-enabled=false`
(API only), `batch.admin.dynamic-jobs.enabled=false`, `batch.admin.scheduling.enabled=false`.

---

## 7. Run and verify

Start the app and open **`/batch-admin`**. You should see your jobs under **Jobs**. Verify the API:

```bash
curl http://localhost:8080/batch-admin/api/jobs
curl -X POST http://localhost:8080/batch-admin/api/jobs/invoiceJob/executions \
     -H 'Content-Type: application/json' -d '{"parameters":{"region":"EU"}}'
```

That's a complete migration for most applications. The sections below cover behaviours worth
understanding when your jobs are non-trivial.

---

## 8. How launches, parameters, stop and restart behave

### Launching is asynchronous
The admin starts jobs through its **own** asynchronous launcher (a `TaskExecutorJobLauncher` backed
by a `SimpleAsyncTaskExecutor`), so the HTTP call returns immediately and the GUI polls for progress.
**Your application's own `jobLauncher` bean is untouched** — your existing launch code keeps working.

> The default executor spawns one thread per launch with no upper bound. If you launch many jobs
> concurrently and want a bounded pool, define your own bean named `batchAdminJobLauncher` to
> override it.

### Job parameters
Parameters entered in the GUI/API are passed as **identifying `String` parameters**, and the admin
adds a unique `run.id` (Long) on every manual start so each launch creates a **new job instance**
(you can always re-run). Implications:

- If a step reads a parameter expecting a specific type, e.g.
  `@Value("#{jobParameters['asOfDate']}") LocalDate date`, be aware the admin supplies it as a
  `String`. Either accept a `String` and parse it, or launch such jobs programmatically with typed
  parameters. (The REST API currently accepts string values only.)
- If your job has a `JobParametersValidator`, a manual launch missing required parameters returns
  **HTTP 400** with the validator's message — pass the required parameters in the request.
- If your job defines a `JobParametersIncrementer`, note the admin already injects `run.id`; you do
  not need to add one for manual launches.

> **Metadata & SpEL.** Parameter values may contain `#{…}` SpEL expressions, resolved at launch time
> against a `MetadataService` and the built-ins `today`/`now`/`timestamp` — useful to wire values from
> a metadata-driven platform instead of hard-coding them, e.g.
> `{"region":"#{metadata.get('region')}","asOfDate":"#{today}"}`. The default `MetadataService` reads
> `batch.admin.metadata.*`; replace the bean to plug your real source. Evaluation is sandboxed and can
> be switched off with `batch.admin.expressions.enabled=false`.

### Stop
**Stop** sets the execution to `STOPPING` via `JobOperator`. Chunk-oriented steps stop at the next
chunk boundary. A single long-running custom `Tasklet` only stops when it cooperates — make long
tasklets interruptible by checking the stop signal, e.g.:

```java
public RepeatStatus execute(StepContribution contribution, ChunkContext context) {
    while (workRemaining()) {
        if (context.getStepContext().getStepExecution().isTerminateOnly()) {
            return RepeatStatus.FINISHED; // honour the stop request
        }
        doOneUnit();
    }
    return RepeatStatus.FINISHED;
}
```

### Restart
**Restart** uses `JobOperator.restart` on the same job instance (for `FAILED`/`STOPPED` executions),
so your jobs must be restartable in the usual Spring Batch sense (persistent `ExecutionContext`,
idempotent steps) to resume correctly.

---

## 9. Read execution logs

The component captures the log lines emitted while a job runs and attributes them to that execution.
It is on by default (requires Logback). Read them per execution, filtered by a configurable level:

```bash
curl "http://localhost:8080/batch-admin/api/executions/42/logs?level=WARN"
```

…or use the **Min level** selector on the execution detail screen. Configure capture/read levels and
buffer sizes:

```yaml
batch:
  admin:
    logs:
      capture-level: INFO        # minimum level stored
      default-read-level: INFO   # default when reading
      max-records-per-execution: 2000
      max-executions: 200
```

> Logs below your application's effective logger level are never emitted, so to capture `DEBUG` for a
> package you must also lower that logger (`logging.level.com.acme=DEBUG`). Capture relies on an MDC
> value set on the job's thread; logs produced on **child threads** of multi-threaded/partitioned
> steps are not attributed to the execution.

---

## 10. Schedule your jobs

Any launchable job can be given a schedule, entered in **plain language** (converted to cron) or as a
raw cron expression / Spring macro:

```bash
curl -X POST http://localhost:8080/batch-admin/api/schedules -H 'Content-Type: application/json' \
     -d '{"jobName":"invoiceJob","cron":"tous les jours à 2h30"}'   # -> 0 30 2 * * *
```

Schedules are persisted and re-armed on restart.

---

## 11. Subscribe to job-lifecycle events (optional)

Every administered job emits lifecycle events — `JOB_STARTED`, then
`JOB_COMPLETED` / `JOB_STOPPED` / `JOB_FAILED` — carrying the job name, execution id, status, exit
code and parameters. By default they are published **in-process** (no infrastructure), so any bean can
react to them:

```java
@Component
class JobAlerts {
    @EventListener
    void on(BatchEvent event) {
        if (event.type() == BatchEventType.JOB_FAILED) { /* page on-call, etc. */ }
    }
}
```

To fan them out to a broker, add Spring AMQP and switch to RabbitMQ — events go to a topic exchange
keyed `<prefix>.<jobName>.<eventType>` so consumers bind precisely:

```yaml
batch:
  admin:
    events:
      broker: rabbit               # default: application (in-process); set to publish to RabbitMQ
      exchange: batch.admin.events
      routing-key-prefix: batch.admin
```

A publisher failure is logged and never breaks the job. To target another transport (Kafka, SNS, a
webhook…), register your own `BatchEventPublisher` bean. Disable entirely with
`batch.admin.events.enabled=false`.

---

## 12. Optional: expose your domain as building blocks

You don't need this to administer existing jobs. But if you want operators to **compose new jobs on
the fly** from your domain operations, expose each operation as a `TaskletProvider` bean:

```java
@Component
public class PurgeTableTaskletProvider implements TaskletProvider {
    public String getType() { return "purge-table"; }
    public String getDisplayName() { return "Purge a table"; }
    public Map<String,String> describeProperties() { return Map.of("table", "Table to purge"); }
    public Tasklet create(Map<String,Object> props) {
        String table = String.valueOf(props.get("table"));
        return (contribution, chunkContext) -> { purge(table); return RepeatStatus.FINISHED; };
    }
}
```

It then appears in the **Create job** screen, and operators can assemble steps like
`cleanup = purge-table (table=TMP_IMPORT)`.

For richer **chunk-oriented** building blocks (a full reader → processor → writer step), implement the
`StepProvider` SPI instead. One ships out of the box — `sql-export` — a paged SQL query → JSON →
**OpenSearch** (or the log) export, available as a dedicated **Create job** form and through the API,
plus generic `GenericSqlItemReader` / `JsonItemProcessor` / `GenericJsonItemWriter` pieces you can
reuse in your own steps. Building these from the GUI lets operators run arbitrary `SELECT`s and push
to a configured URL — another reason to protect `/batch-admin/**` (§6).

And you may not need to write anything at all: your **existing jobs** are derived into reusable blocks
automatically — each step (`<jobName>.<stepName>`) and each whole job's flow (`job:<jobName>`, all its
steps in order). A new on-the-fly job can reuse them with no code, and the **Create job** screen has a
picker to insert them — see the building-blocks guide. Disable with
`batch.admin.dynamic-jobs.reuse-existing-steps=false`.

> See the **[Building blocks guide](BUILDING_BLOCKS.md)** for a step-by-step walkthrough of both SPIs
> (`TaskletProvider` and `StepProvider`), deriving blocks from existing jobs, with full worked
> examples, property handling and tips.

---

## 13. What the component does and does not change

**Does:**
- Registers your `Job` beans into the `JobRegistry` (so they are launchable) and attaches its own
  listeners (per-execution log capture and lifecycle-event publishing) to them.
- Adds two `BATCH_ADMIN_*` tables and serves a GUI + REST API under `batch.admin.base-path`.

**Does not:**
- Touch your JPA/Hibernate, transaction manager, or your own `jobLauncher`.
- Override beans you already define (`JobOperator`, a `batchAdminJobLauncher`, a `MetadataService`, a
  `BatchEventPublisher`, etc. are `@ConditionalOnMissingBean`).
- Run your jobs unless asked (launches are on demand or via schedules you create).
- Add authentication **unless you opt in** (`batch.admin.security.enabled=true`, §6); otherwise
  securing the route is your responsibility.

Its exception handling is scoped to the `io.batchadmin.web` controllers, so it won't interfere with
your application's own error handling.

---

## 14. Coming from Spring Cloud Data Flow / the old Spring Batch Admin

| You used to… | Now |
| ------------ | --- |
| Register/launch tasks in SCDF | Your jobs are auto-discovered; launch from `/batch-admin` or the API |
| Schedule via SCDF/cron | Create schedules in the **Schedules** screen (plain language or cron) |
| Inspect executions in the SCDF dashboard | **Dashboard** + **Executions** screens, with step metrics and logs |
| A separate SCDF server/deployment | Everything runs **inside your Spring Boot app** — no extra server |

---

## 15. Troubleshooting

| Symptom | Likely cause / fix |
| ------- | ------------------ |
| Job not listed at all | It isn't a Spring `Job` bean, or the component didn't activate (no `DataSource`/batch beans). |
| Job listed but **not launchable** | It exists in history but isn't registered (removed from code, or duplicate name). |
| `409 Conflict` on start | An execution is already running, or the instance already completed for those parameters. |
| `400 Bad Request` on start | A `JobParametersValidator` rejected the parameters — pass the required ones. |
| Stop doesn't take effect quickly | A long custom `Tasklet` isn't checking `isTerminateOnly()` (see §8). |
| Logs empty | Not using Logback, the logger level is above `capture-level`, or logs were on a child thread (§9). |
| GUI reachable by anyone | Enable the built-in OAuth2/OIDC layer or add your own Spring Security on `/batch-admin/**` (§6). |
| `#{…}` parameter passed through literally | Expression resolution is off (`batch.admin.expressions.enabled=false`) or the key is missing from the `MetadataService` (§8). |
| No lifecycle events received | `batch.admin.events.enabled=false`, or `broker: rabbit` is set but no reachable `RabbitTemplate`/broker (§11). |

---

## 16. Migration checklist

- [ ] Add the `spring-boot-batch-admin-starter` dependency.
- [ ] Confirm each job is a Spring `Job` bean with a unique, stable name.
- [ ] Ensure the Spring Batch `BATCH_*` tables exist.
- [ ] Set `spring.batch.job.enabled=false` (launch on demand).
- [ ] Secure `/batch-admin/**` — enable the built-in OAuth2/OIDC layer or add your own filter chain.
- [ ] Start the app, open `/batch-admin`, confirm your jobs appear and launch.
- [ ] (If needed) make long tasklets interruptible for prompt **Stop**.
- [ ] (Optional) expose domain operations as `TaskletProvider` / `StepProvider` beans.
- [ ] (Optional) create schedules and adjust `batch.admin.logs.*` levels.
- [ ] (Optional) wire parameters from metadata/SpEL and subscribe to lifecycle events.
