package io.batchadmin.domain;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * JDBC access to persisted job triggers (event-driven chaining rules).
 */
public class JobTriggerDao {

    private static final String TABLE = "BATCH_ADMIN_JOB_TRIGGER";

    private static final RowMapper<JobTriggerRecord> MAPPER = (rs, rowNum) -> new JobTriggerRecord(
            rs.getLong("ID"),
            rs.getString("SOURCE_JOB"),
            rs.getString("TARGET_JOB"),
            rs.getString("CONDITION_TYPE"),
            rs.getBoolean("ENABLED"),
            rs.getString("DESCRIPTION"),
            toInstant(rs.getTimestamp("CREATED_AT")));

    private final JdbcTemplate jdbc;

    public JobTriggerDao(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public JobTriggerRecord insert(String sourceJob, String targetJob, String condition,
                                   boolean enabled, String description) {
        Timestamp now = Timestamp.from(Instant.now());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + TABLE
                            + " (SOURCE_JOB, TARGET_JOB, CONDITION_TYPE, ENABLED, DESCRIPTION, CREATED_AT)"
                            + " VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sourceJob);
            ps.setString(2, targetJob);
            ps.setString(3, condition);
            ps.setBoolean(4, enabled);
            ps.setString(5, description);
            ps.setTimestamp(6, now);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return new JobTriggerRecord(id, sourceJob, targetJob, condition, enabled, description, now.toInstant());
    }

    public void setEnabled(long id, boolean enabled) {
        jdbc.update("UPDATE " + TABLE + " SET ENABLED = ? WHERE ID = ?", enabled, id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM " + TABLE + " WHERE ID = ?", id);
    }

    public Optional<JobTriggerRecord> findById(long id) {
        try {
            return Optional.of(jdbc.queryForObject("SELECT * FROM " + TABLE + " WHERE ID = ?", MAPPER, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<JobTriggerRecord> findAll() {
        return jdbc.query("SELECT * FROM " + TABLE + " ORDER BY ID", MAPPER);
    }

    /** Enabled triggers whose source is the given job (evaluated when that job finishes). */
    public List<JobTriggerRecord> findEnabledBySource(String sourceJob) {
        return jdbc.query("SELECT * FROM " + TABLE + " WHERE ENABLED = TRUE AND SOURCE_JOB = ? ORDER BY ID",
                MAPPER, sourceJob);
    }

    /** Removes every trigger referencing the job as source or target (used when a job is deleted). */
    public void deleteByJob(String jobName) {
        jdbc.update("DELETE FROM " + TABLE + " WHERE SOURCE_JOB = ? OR TARGET_JOB = ?", jobName, jobName);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
