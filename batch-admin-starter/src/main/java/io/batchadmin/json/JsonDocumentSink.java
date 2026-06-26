package io.batchadmin.json;

import java.util.List;

/**
 * Destination for a batch of JSON documents. Implementations send the documents to a concrete target
 * (an OpenSearch/Elasticsearch index, a file, a queue, an HTTP endpoint, …). One {@code write} call
 * corresponds to one Spring Batch chunk, so implementations should send the whole batch at once
 * (e.g. a bulk request) when the target supports it.
 */
@FunctionalInterface
public interface JsonDocumentSink {

    /**
     * Writes a batch of JSON documents to the target.
     *
     * @param jsonDocuments the documents of the current chunk, each a JSON string
     * @throws Exception if the target rejects the batch (the step fails / rolls back)
     */
    void write(List<String> jsonDocuments) throws Exception;

    /** Releases any resource held by the sink. Called when the step closes. */
    default void close() {
    }
}
