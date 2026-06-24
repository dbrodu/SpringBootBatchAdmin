package io.batchadmin.service;

/**
 * Runtime failure raised by the admin services and translated to an HTTP response by
 * {@link io.batchadmin.web.BatchAdminExceptionHandler}.
 */
public class BatchAdminException extends RuntimeException {

    public enum Kind {
        NOT_FOUND,
        CONFLICT,
        BAD_REQUEST,
        INTERNAL
    }

    private final Kind kind;

    public BatchAdminException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public BatchAdminException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public static BatchAdminException notFound(String message) {
        return new BatchAdminException(Kind.NOT_FOUND, message);
    }

    public static BatchAdminException conflict(String message) {
        return new BatchAdminException(Kind.CONFLICT, message);
    }

    public static BatchAdminException badRequest(String message) {
        return new BatchAdminException(Kind.BAD_REQUEST, message);
    }
}
