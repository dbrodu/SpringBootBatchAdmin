package io.batchadmin.web;

import io.batchadmin.service.BatchAdminException;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates {@link BatchAdminException} (and unexpected errors) raised by the admin controllers
 * into structured HTTP responses. Scoped to this component's controllers so it never interferes
 * with the host application's own error handling.
 */
@RestControllerAdvice(basePackages = "io.batchadmin.web")
public class BatchAdminExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BatchAdminExceptionHandler.class);

    @ExceptionHandler(BatchAdminException.class)
    public ResponseEntity<Map<String, Object>> handle(BatchAdminException ex) {
        HttpStatus status = switch (ex.getKind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        if (status.is5xxServerError()) {
            log.error("[batch-admin] {}", ex.getMessage(), ex);
        }
        return ResponseEntity.status(status).body(body(status, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("[batch-admin] Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage()));
    }

    private Map<String, Object> body(HttpStatus status, String message) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message != null ? message : "");
    }
}
