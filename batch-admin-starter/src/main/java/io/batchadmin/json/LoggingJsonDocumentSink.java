package io.batchadmin.json;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link JsonDocumentSink} that logs each JSON document. Useful as a dry-run target while wiring a
 * pipeline, or for smoke tests, before pointing the writer at a real destination.
 */
public class LoggingJsonDocumentSink implements JsonDocumentSink {

    private static final Logger log = LoggerFactory.getLogger(LoggingJsonDocumentSink.class);

    @Override
    public void write(List<String> jsonDocuments) {
        for (String document : jsonDocuments) {
            log.info("[batch-admin] json> {}", document);
        }
    }
}
