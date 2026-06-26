package io.batchadmin.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.Assert;

/**
 * {@link JsonDocumentSink} that indexes JSON documents into an OpenSearch (or Elasticsearch) index
 * using the {@code _bulk} REST API, with the JDK HTTP client only (no OpenSearch SDK dependency).
 *
 * <p>Each chunk becomes one NDJSON bulk request, so a whole chunk is indexed in a single round-trip.
 * Authentication and TLS are configured by supplying request headers (e.g. {@code Authorization})
 * and/or your own pre-configured {@link HttpClient}.</p>
 *
 * <pre>{@code
 * JsonDocumentSink sink = OpenSearchBulkJsonSink.builder()
 *         .baseUrl("https://opensearch:9200")
 *         .index("orders")
 *         .header("Authorization", "Basic " + base64("user:pass"))
 *         .idField("id")            // optional: use this JSON field as the document _id
 *         .build();
 * }</pre>
 */
public class OpenSearchBulkJsonSink implements JsonDocumentSink {

    private final String bulkUrl;
    private final String index;
    private final String idField;
    private final Map<String, String> headers;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private OpenSearchBulkJsonSink(Builder builder) {
        Assert.hasText(builder.baseUrl, "baseUrl must be provided");
        Assert.hasText(builder.index, "index must be provided");
        String base = builder.baseUrl.endsWith("/") ? builder.baseUrl.substring(0, builder.baseUrl.length() - 1)
                : builder.baseUrl;
        this.bulkUrl = base + "/_bulk";
        this.index = builder.index;
        this.idField = builder.idField;
        this.headers = new LinkedHashMap<>(builder.headers);
        this.requestTimeout = builder.requestTimeout;
        this.httpClient = builder.httpClient != null ? builder.httpClient
                : HttpClient.newBuilder().connectTimeout(builder.requestTimeout).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void write(List<String> jsonDocuments) throws Exception {
        String body = buildBulkBody(jsonDocuments);

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(bulkUrl))
                .timeout(requestTimeout)
                .header("Content-Type", "application/x-ndjson")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(request::header);

        HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("OpenSearch _bulk returned HTTP " + response.statusCode() + ": "
                    + truncate(response.body()));
        }
        assertNoItemErrors(response.body());
    }

    private String buildBulkBody(List<String> jsonDocuments) throws Exception {
        StringBuilder body = new StringBuilder();
        for (String document : jsonDocuments) {
            body.append(actionMetadata(document)).append('\n');
            body.append(document).append('\n');
        }
        return body.toString();
    }

    private String actionMetadata(String document) throws Exception {
        Map<String, Object> index = new LinkedHashMap<>();
        index.put("_index", this.index);
        if (idField != null && !idField.isBlank()) {
            JsonNode node = objectMapper.readTree(document).get(idField);
            if (node != null && !node.isNull()) {
                index.put("_id", node.asText());
            }
        }
        return objectMapper.writeValueAsString(Map.of("index", index));
    }

    private void assertNoItemErrors(String responseBody) throws Exception {
        if (responseBody == null || responseBody.isBlank()) {
            return;
        }
        JsonNode root = objectMapper.readTree(responseBody);
        if (root.path("errors").asBoolean(false)) {
            String firstError = "unknown error";
            for (JsonNode item : root.path("items")) {
                JsonNode error = item.path("index").path("error");
                if (!error.isMissingNode()) {
                    firstError = error.toString();
                    break;
                }
            }
            throw new IllegalStateException("OpenSearch _bulk reported item errors: " + truncate(firstError));
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }

    public static final class Builder {
        private String baseUrl;
        private String index;
        private String idField;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private HttpClient httpClient;
        private Duration requestTimeout = Duration.ofSeconds(30);

        /** Base URL of the cluster, e.g. {@code https://opensearch:9200}. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** Target index name. */
        public Builder index(String index) {
            this.index = index;
            return this;
        }

        /** Optional JSON field whose value becomes the document {@code _id}. */
        public Builder idField(String idField) {
            this.idField = idField;
            return this;
        }

        /** Adds a request header (e.g. {@code Authorization}) sent with every bulk request. */
        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        /** Supplies a pre-configured client (for TLS, proxies, auth, timeouts). */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public OpenSearchBulkJsonSink build() {
            return new OpenSearchBulkJsonSink(this);
        }
    }
}
