package io.batchadmin.json;

import java.util.ArrayList;
import java.util.List;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.util.Assert;

/**
 * Generic Spring Batch {@link org.springframework.batch.item.ItemWriter} for JSON documents: it
 * delegates each chunk to a pluggable {@link JsonDocumentSink}, which writes the documents to the
 * actual target (for example an OpenSearch index via {@link OpenSearchBulkJsonSink}).
 *
 * <pre>{@code
 * ItemWriter<String> writer = new GenericJsonItemWriter(
 *         OpenSearchBulkJsonSink.builder()
 *                 .baseUrl("https://opensearch:9200")
 *                 .index("orders")
 *                 .build());
 * }</pre>
 */
public class GenericJsonItemWriter implements ItemStreamWriter<String> {

    private final JsonDocumentSink sink;

    public GenericJsonItemWriter(JsonDocumentSink sink) {
        Assert.notNull(sink, "sink must not be null");
        this.sink = sink;
    }

    @Override
    public void write(Chunk<? extends String> chunk) throws Exception {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        List<String> documents = new ArrayList<>(chunk.size());
        for (String document : chunk.getItems()) {
            if (document != null) {
                documents.add(document);
            }
        }
        if (!documents.isEmpty()) {
            sink.write(documents);
        }
    }

    @Override
    public void close() throws ItemStreamException {
        try {
            sink.close();
        } catch (Exception ex) {
            throw new ItemStreamException("Failed to close the JSON document sink", ex);
        }
    }
}
