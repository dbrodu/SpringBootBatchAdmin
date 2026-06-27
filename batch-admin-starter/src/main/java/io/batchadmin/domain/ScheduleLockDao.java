package io.batchadmin.domain;

import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC dedup lock that makes scheduled launches cluster-safe: each {@code (scheduleId, fireSecond)}
 * pair can be claimed exactly once across all instances sharing the database, so only the instance
 * that wins the insert launches the job for that fire.
 */
public class ScheduleLockDao {

    private static final String TABLE = "BATCH_ADMIN_SCHEDULE_LOCK";

    private final JdbcTemplate jdbc;

    public ScheduleLockDao(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * Attempts to claim a schedule's fire. Returns {@code true} for the single instance whose insert
     * wins, {@code false} for the others (the row already exists).
     */
    public boolean tryClaim(long scheduleId, long fireSecond, String instanceId) {
        try {
            jdbc.update("INSERT INTO " + TABLE
                            + " (SCHEDULE_ID, FIRE_SECOND, INSTANCE_ID, CLAIMED_AT) VALUES (?, ?, ?, ?)",
                    scheduleId, fireSecond, instanceId, Timestamp.from(Instant.now()));
            return true;
        } catch (DataIntegrityViolationException ex) {
            // Another instance already claimed this (scheduleId, fireSecond) — includes the
            // DuplicateKeyException subclass raised on a primary-key clash.
            return false;
        }
    }

    /** Removes claim rows older than the cutoff, to keep the table bounded. */
    public int purgeOlderThan(Instant cutoff) {
        return jdbc.update("DELETE FROM " + TABLE + " WHERE CLAIMED_AT < ?", Timestamp.from(cutoff));
    }
}
