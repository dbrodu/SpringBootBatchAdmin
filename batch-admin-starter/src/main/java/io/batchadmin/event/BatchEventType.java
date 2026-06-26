package io.batchadmin.event;

/** Lifecycle moment a {@link BatchEvent} reports. */
public enum BatchEventType {

    /** A job execution has started. */
    JOB_STARTED,

    /** A job execution finished with status {@code COMPLETED}. */
    JOB_COMPLETED,

    /** A job execution finished with status {@code STOPPED}. */
    JOB_STOPPED,

    /** A job execution finished in a failure status ({@code FAILED}, {@code ABANDONED}, …). */
    JOB_FAILED
}
