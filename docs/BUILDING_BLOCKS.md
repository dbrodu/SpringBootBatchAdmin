# Adding a building block

**Building blocks** are how this component stays agnostic of your jobs while still letting operators
**compose new jobs on the fly** from the GUI and the REST API. A building block is just a Spring bean
that implements one of two SPIs:

| SPI | Contributes | Use it for |
| --- | ----------- | ---------- |
| [`TaskletProvider`](#1-taskletprovider-a-single-step) | a single `Tasklet` step | one elementary operation — purge a table, send a report, call an API |
| [`StepProvider`](#2-stepprovider-a-chunk-oriented-step) | a whole chunk-oriented `Step` (reader → processor → writer) | bulk data movement — read/transform/write many items |

There is also a **third, zero-effort source of blocks**: the component automatically
[**derives a block from every step of your existing jobs**](#3-reuse-a-step-from-an-existing-job-no-code),
so a new on-the-fly job can reuse a step the application already defines — see §3.

You add a building block by **declaring a bean** — nothing else. The component discovers every
`TaskletProvider` / `StepProvider` bean on the context, lists them as available step *types*, and the
[`DynamicJobService`](../batch-admin-starter/src/main/java/io/batchadmin/service/DynamicJobService.java)
wires them into real Spring Batch steps when a job is created. The component ships a few generic ones
(`log`, `sleep`, an opt-in `command`, and the `sql-export` step provider) so it is usable out of the
box; yours sit alongside them.

---

## 1. `TaskletProvider` (a single step)

Implement the SPI and expose it as a bean:

```java
@Component
public class PurgeTableTaskletProvider implements TaskletProvider {

    private final JdbcTemplate jdbc;

    public PurgeTableTaskletProvider(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String getType() {            // unique logical type, referenced by step definitions
        return "purge-table";
    }

    @Override
    public String getDisplayName() {     // label shown in the GUI (optional; defaults to getType())
        return "Purge a table";
    }

    @Override
    public Map<String, String> describeProperties() {   // documents the form fields in the GUI (optional)
        return Map.of("table", "Name of the table to purge");
    }

    @Override
    public Tasklet create(Map<String, Object> properties) {
        String table = String.valueOf(properties.get("table"));
        return (contribution, chunkContext) -> {
            int deleted = jdbc.update("DELETE FROM " + table);
            contribution.incrementWriteCount(deleted);
            return RepeatStatus.FINISHED;
        };
    }
}
```

That is the whole integration. The new `purge-table` type now appears in the **Create job** screen and
is accepted by the API.

### What each method does

- **`getType()`** — the logical step type. **Must be unique** across all providers; it is what a step
  definition references. Keep it short and kebab-case (`purge-table`, `send-report`).
- **`getDisplayName()`** *(default: `getType()`)* — the human-readable label in the GUI.
- **`describeProperties()`** *(default: empty)* — a `name → description` map. Each entry becomes a
  documented input on the GUI form, so operators know what to fill in. Purely descriptive — it does
  not validate.
- **`create(properties)`** — builds the `Tasklet`. `properties` is the step's
  configuration as a `Map<String, Object>` (see [Property handling](#property-handling) below). Must
  return a non-`null` tasklet.

> **Long-running tasklets** should be **interruptible** so the admin's **Stop** works promptly — check
> `chunkContext.getStepContext().getStepExecution().isTerminateOnly()` in your loop and return early.
> See [migration guide §8](MIGRATION.md#8-how-launches-parameters-stop-and-restart-behave).

---

## 2. `StepProvider` (a chunk-oriented step)

When a single tasklet isn't enough — you want a real **reader → processor → writer** chunk step —
implement `StepProvider` instead. It receives the batch infrastructure it needs (a `JobRepository` and
a `PlatformTransactionManager`) through a `Context`, and returns a fully built `Step`:

```java
@Component
public class CsvImportStepProvider implements StepProvider {

    @Override
    public String getType() {
        return "csv-import";
    }

    @Override
    public String getDisplayName() {
        return "Import a CSV file into a table";
    }

    @Override
    public Map<String, String> describeProperties() {
        return Map.of(
                "path", "Absolute path of the CSV file to import",
                "chunkSize", "Rows per transaction (default 200)");
    }

    @Override
    public Step buildStep(String stepName, Map<String, Object> properties, Context context) {
        String path = required(properties, "path");
        int chunk = toInt(properties.get("chunkSize"), 200);

        return new StepBuilder(stepName, context.jobRepository())
                .<Row, Row>chunk(chunk, context.transactionManager())
                .reader(csvReader(stepName, path))
                .writer(jdbcWriter())
                .build();
    }

    private static String required(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (value == null || value.toString().isBlank()) {
            // Thrown during job creation -> the API/GUI returns HTTP 400 with this message.
            throw new IllegalArgumentException("Property '" + key + "' is required for a csv-import step");
        }
        return value.toString();
    }
    // csvReader(...), jdbcWriter(...), toInt(...) omitted for brevity
}
```

### What each method does

- **`getType()` / `getDisplayName()` / `describeProperties()`** — same contract as `TaskletProvider`.
  The type must be unique across **both** tasklet and step providers.
- **`buildStep(stepName, properties, context)`** — build and return the step.
  - Use the supplied **`stepName`** (it is unique within the job) when naming the step and any of its
    components.
  - **`context.jobRepository()`** and **`context.transactionManager()`** are the component's batch
    infrastructure — pass them to your `StepBuilder`.
  - Throw **`IllegalArgumentException`** for bad/missing properties; the component turns it into an
    **HTTP 400** with your message instead of a 500.

### Reuse the shipped pieces

You don't have to write readers/writers from scratch — the component ships reusable parts you can
assemble in your `buildStep`:

- `GenericSqlItemReader` / `GenericSqlPagingItemReader` — stream rows of an arbitrary SQL query;
- `JsonItemProcessor` — turn rows into JSON;
- `GenericJsonItemWriter` + a `JsonDocumentSink` (`OpenSearchBulkJsonSink`, `LoggingJsonDocumentSink`).

The built-in
[`SqlExportStepProvider`](../batch-admin-starter/src/main/java/io/batchadmin/dynamic/provider/SqlExportStepProvider.java)
(`type = sql-export`) is a complete worked example that wires exactly these together — a good template
to copy.

---

## 3. Reuse a step from an existing job (no code)

You don't have to write anything for steps you **already have**. The component inspects every
registered job and turns each of its steps into a reusable building block automatically — so a new
on-the-fly job can drop in a step the application already defines, with no duplication.

- The block **type** is the step's name (e.g. `extract`), or `\<jobName\>.\<stepName\>` when the same
  step name exists in more than one job, so it stays unambiguous.
- Selecting it **reuses the existing step instance as-is** — same reader/writer/tasklet, same logic.
  It takes **no properties**.
- Jobs created *through* the admin (dynamic jobs) are excluded, so only your genuine steps are offered.

List what's available (also shown in the **Create job** screen):

```bash
curl http://localhost:8080/batch-admin/api/jobs/reusable-steps
# [ { "type": "invoiceJob.extract", "displayName": "Reuse step 'extract' from job 'invoiceJob'", ... } ]
```

Then use the type like any other block — e.g. in the GUI:

```
pull = invoiceJob.extract
load = csv-import (path=/in/data.csv)
```

Turn this off with `batch.admin.dynamic-jobs.reuse-existing-steps=false`.

> Only jobs that expose their steps (`SimpleJob` / `FlowJob`, i.e. anything implementing Spring Batch's
> `StepLocator`) contribute blocks. A step may legitimately belong to several jobs — reuse shares the
> one instance; execution state is still tracked per job execution.

---

## 4. Use your building block

Once the bean is on the context, a `csv-import` / `purge-table` step type is available everywhere.

### From the GUI

On the **Create job** screen, describe the steps one per line — `<stepName> = <type> (key=value, …)`:

```
cleanup = purge-table (table=TMP_IMPORT)
load    = csv-import (path=/in/data.csv, chunkSize=500)
```

### From the REST API

```bash
curl -X POST http://localhost:8080/batch-admin/api/jobs -H 'Content-Type: application/json' -d '{
  "jobName": "nightlyImport",
  "description": "Purge then reload",
  "steps": [
    { "name": "cleanup", "type": "purge-table", "properties": { "table": "TMP_IMPORT" } },
    { "name": "load",    "type": "csv-import",  "properties": { "path": "/in/data.csv", "chunkSize": "500" } }
  ]
}'
```

The created job is **persisted** and re-registered on every restart, then launchable and schedulable
like any other.

### Discover the available types

`TaskletProvider` building blocks are listed (with their `displayName` and `describeProperties`) at:

```bash
curl http://localhost:8080/batch-admin/api/jobs/providers       # tasklet building blocks
curl http://localhost:8080/batch-admin/api/jobs/reusable-steps  # blocks derived from existing jobs (§3)
```

---

## Property handling

- **Values arrive as a `Map<String, Object>`.** Values entered through the GUI/API are **strings**, so
  read them defensively (`String.valueOf(...)`, parse numbers yourself, default missing keys).
- **Expressions are resolved first.** Before your provider is called, every string property is run
  through the metadata/SpEL [`ValueResolver`](../batch-admin-starter/src/main/java/io/batchadmin/metadata/ValueResolver.java),
  so a property like `index = orders-#{metadata.get('region')}` reaches your code already resolved
  (e.g. `orders-EU`). You don't do anything special — just consume the final value. See
  [README §6](../README.md#6-metadata-driven-parameters-spel).
- **Validate by throwing.** For a `StepProvider`, an `IllegalArgumentException` becomes an HTTP 400
  with your message. Document required keys in `describeProperties()` so operators get it right the
  first time.

---

## Conventions & tips

- **Unique, stable `getType()`** — it is the contract referenced by persisted job definitions. Renaming
  a type orphans jobs that were built with the old name.
- **Override defaults, don't replace beans of others.** Declaring a provider with the same type as a
  built-in one effectively shadows it; prefer a distinct type unless you mean to replace it.
- **Keep steps idempotent / restartable** where you can, so the admin's **Restart** resumes cleanly.
- **Mind security.** Building jobs from the GUI lets operators run whatever your provider does (SQL,
  shell, HTTP…). Protect `/batch-admin/**` — see [migration guide §6](MIGRATION.md#6-secure-the-endpoints-important).
  The OS-`command` provider is opt-in for this reason
  (`batch.admin.dynamic-jobs.allow-command-tasklets=true`).

---

## See also

- [README — Create jobs on the fly](../README.md#2-create-jobs-on-the-fly) and
  [Reusable building blocks](../README.md#reusable-building-blocks)
- [Migration guide §12 — Optional: expose your domain as building blocks](MIGRATION.md#12-optional-expose-your-domain-as-building-blocks)
- SPIs:
  [`TaskletProvider`](../batch-admin-starter/src/main/java/io/batchadmin/dynamic/TaskletProvider.java),
  [`StepProvider`](../batch-admin-starter/src/main/java/io/batchadmin/dynamic/StepProvider.java)
