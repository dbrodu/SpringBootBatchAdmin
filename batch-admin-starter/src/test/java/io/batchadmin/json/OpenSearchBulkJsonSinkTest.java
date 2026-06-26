package io.batchadmin.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenSearchBulkJsonSinkTest {

    private HttpServer server;
    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private final AtomicReference<String> capturedContentType = new AtomicReference<>();
    private volatile String responseBody = "{\"took\":1,\"errors\":false,\"items\":[]}";
    private volatile int responseStatus = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Test
    void postsNdjsonBulkRequest() throws Exception {
        OpenSearchBulkJsonSink sink = OpenSearchBulkJsonSink.builder()
                .baseUrl(baseUrl()).index("people").build();

        sink.write(List.of("{\"id\":1,\"name\":\"Alice\"}", "{\"id\":2,\"name\":\"Bob\"}"));

        assertThat(capturedPath.get()).isEqualTo("/_bulk");
        assertThat(capturedContentType.get()).isEqualTo("application/x-ndjson");
        assertThat(capturedBody.get()).isEqualTo(
                "{\"index\":{\"_index\":\"people\"}}\n"
                        + "{\"id\":1,\"name\":\"Alice\"}\n"
                        + "{\"index\":{\"_index\":\"people\"}}\n"
                        + "{\"id\":2,\"name\":\"Bob\"}\n");
    }

    @Test
    void usesIdFieldAsDocumentId() throws Exception {
        OpenSearchBulkJsonSink sink = OpenSearchBulkJsonSink.builder()
                .baseUrl(baseUrl()).index("people").idField("id").build();

        sink.write(List.of("{\"id\":42,\"name\":\"Alice\"}"));

        assertThat(capturedBody.get()).startsWith("{\"index\":{\"_index\":\"people\",\"_id\":\"42\"}}\n");
    }

    @Test
    void throwsWhenClusterReportsItemErrors() {
        responseBody = "{\"errors\":true,\"items\":[{\"index\":{\"error\":{\"type\":\"mapper_parsing_exception\"}}}]}";
        OpenSearchBulkJsonSink sink = OpenSearchBulkJsonSink.builder()
                .baseUrl(baseUrl()).index("people").build();

        assertThatThrownBy(() -> sink.write(List.of("{\"id\":1}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("item errors");
    }

    @Test
    void throwsOnHttpError() {
        responseStatus = 503;
        responseBody = "service unavailable";
        OpenSearchBulkJsonSink sink = OpenSearchBulkJsonSink.builder()
                .baseUrl(baseUrl()).index("people").build();

        assertThatThrownBy(() -> sink.write(List.of("{\"id\":1}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 503");
    }
}
