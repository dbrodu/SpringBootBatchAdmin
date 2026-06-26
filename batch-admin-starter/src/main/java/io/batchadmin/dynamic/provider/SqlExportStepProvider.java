package io.batchadmin.dynamic.provider;

import io.batchadmin.dynamic.StepProvider;
import io.batchadmin.json.GenericJsonItemWriter;
import io.batchadmin.json.JsonDocumentSink;
import io.batchadmin.json.JsonItemProcessor;
import io.batchadmin.json.LoggingJsonDocumentSink;
import io.batchadmin.json.OpenSearchBulkJsonSink;
import io.batchadmin.sql.GenericSqlPagingItemReader;
import io.batchadmin.sql.GenericSqlPagingItemReaderBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.step.builder.StepBuilder;

/**
 * {@link StepProvider} that builds a complete chunk-oriented export step from declarative
 * properties: a paged SQL reader &rarr; JSON serialization &rarr; a target sink (OpenSearch via its
 * {@code _bulk} API, or a log dry-run). It lets operators compose a "SQL &rarr; OpenSearch export"
 * job on the fly, with no code.
 *
 * <p>Properties:</p>
 * <ul>
 *   <li>{@code select} (required) — column list, without the {@code SELECT} keyword;</li>
 *   <li>{@code from} (required) — {@code FROM} body, without the keyword;</li>
 *   <li>{@code where} (optional) — filter, without the {@code WHERE} keyword (use literals);</li>
 *   <li>{@code sort} (required) — comma-separated sort columns, prefix {@code -} for descending,
 *       e.g. {@code id} or {@code id,-created_at};</li>
 *   <li>{@code pageSize} (optional, default 500);</li>
 *   <li>{@code target} (optional, {@code opensearch} (default) or {@code log});</li>
 *   <li>{@code baseUrl}, {@code index} (required for {@code opensearch});</li>
 *   <li>{@code idField} (optional) — JSON field used as the document {@code _id};</li>
 *   <li>{@code authHeader} (optional) — value of the {@code Authorization} header.</li>
 * </ul>
 */
public class SqlExportStepProvider implements StepProvider {

    private final DataSource dataSource;

    public SqlExportStepProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getType() {
        return "sql-export";
    }

    @Override
    public String getDisplayName() {
        return "SQL → JSON export (e.g. OpenSearch)";
    }

    @Override
    public Map<String, String> describeProperties() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("select", "Columns to read, e.g. id, name, amount");
        props.put("from", "FROM body, e.g. orders");
        props.put("where", "Optional filter, e.g. status = 'NEW'");
        props.put("sort", "Sort columns, '-' for desc, e.g. id or id,-created_at");
        props.put("pageSize", "Rows per page (default 500)");
        props.put("target", "opensearch (default) or log");
        props.put("baseUrl", "OpenSearch base URL, e.g. https://opensearch:9200");
        props.put("index", "Target index name");
        props.put("idField", "Optional JSON field used as the document _id");
        props.put("authHeader", "Optional Authorization header value");
        return props;
    }

    @Override
    public Step buildStep(String stepName, Map<String, Object> properties, Context context) {
        int pageSize = toInt(properties.get("pageSize"), 500);

        GenericSqlPagingItemReader<Map<String, Object>> reader = buildReader(stepName, properties, pageSize);
        JsonItemProcessor<Map<String, Object>> processor = new JsonItemProcessor<>();
        GenericJsonItemWriter writer = new GenericJsonItemWriter(buildSink(properties));

        return new StepBuilder(stepName, context.jobRepository())
                .<Map<String, Object>, String>chunk(pageSize, context.transactionManager())
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    private GenericSqlPagingItemReader<Map<String, Object>> buildReader(String stepName,
                                                                        Map<String, Object> properties,
                                                                        int pageSize) {
        GenericSqlPagingItemReaderBuilder<Map<String, Object>> builder = GenericSqlPagingItemReaderBuilder.mapRows()
                .name(stepName + ".reader")
                .dataSource(dataSource)
                .select(required(properties, "select"))
                .from(required(properties, "from"))
                .pageSize(pageSize);
        String where = string(properties.get("where"));
        if (where != null && !where.isBlank()) {
            builder.where(where);
        }
        for (String token : required(properties, "sort").split(",")) {
            String column = token.trim();
            if (column.isEmpty()) {
                continue;
            }
            if (column.startsWith("-")) {
                builder.sortDesc(column.substring(1).trim());
            } else {
                builder.sortAsc(column);
            }
        }
        return builder.build();
    }

    private JsonDocumentSink buildSink(Map<String, Object> properties) {
        String target = string(properties.getOrDefault("target", "opensearch"));
        if ("log".equalsIgnoreCase(target)) {
            return new LoggingJsonDocumentSink();
        }
        if (!"opensearch".equalsIgnoreCase(target)) {
            throw new IllegalArgumentException("Unknown target '" + target + "' (expected 'opensearch' or 'log')");
        }
        OpenSearchBulkJsonSink.Builder sink = OpenSearchBulkJsonSink.builder()
                .baseUrl(required(properties, "baseUrl"))
                .index(required(properties, "index"));
        String idField = string(properties.get("idField"));
        if (idField != null && !idField.isBlank()) {
            sink.idField(idField);
        }
        String authHeader = string(properties.get("authHeader"));
        if (authHeader != null && !authHeader.isBlank()) {
            sink.header("Authorization", authHeader);
        }
        return sink.build();
    }

    private static String required(Map<String, Object> properties, String key) {
        String value = string(properties.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Property '" + key + "' is required for a sql-export step");
        }
        return value;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
