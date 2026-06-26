package io.batchadmin.service;

/**
 * How a version snapshot of a dynamic job's definition came to be — captured as audit metadata on
 * every recorded version.
 */
public enum VersionChangeType {

    /** The job was first created. */
    CREATE,
    /** An existing dynamic job's definition was edited in place. */
    EDIT,
    /** The definition was restored from an earlier version (recorded as a new version). */
    ROLLBACK,
    /** The definition was brought in from an exported JSON document. */
    IMPORT,
    /** A baseline snapshot backfilled for a job persisted before version history existed. */
    BASELINE
}
