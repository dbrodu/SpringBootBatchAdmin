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
 * JDBC access to the append-only version history of dynamic job definitions.
 */
public class JobDefinitionVersionDao {

    private static final String TABLE = "BATCH_ADMIN_JOB_DEFINITION_VERSION";

    private static final RowMapper<JobDefinitionVersionRecord> MAPPER = (rs, rowNum) ->
            new JobDefinitionVersionRecord(
                    rs.getLong("ID"),
                    rs.getString("JOB_NAME"),
                    rs.getInt("VERSION_NUMBER"),
                    rs.getString("DESCRIPTION"),
                    rs.getString("STEPS_JSON"),
                    rs.getString("AUTHOR"),
                    rs.getString("CHANGE_TYPE"),
                    rs.getString("CHANGE_NOTE"),
                    toInstant(rs.getTimestamp("CREATED_AT")));

    private final JdbcTemplate jdbc;

    public JobDefinitionVersionDao(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** Next version number for a job (1-based; the highest existing version plus one). */
    public int nextVersion(String jobName) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(VERSION_NUMBER), 0) FROM " + TABLE + " WHERE JOB_NAME = ?",
                Integer.class, jobName);
        return (max == null ? 0 : max) + 1;
    }

    /** Appends a snapshot (with its audit metadata) and returns it. */
    public JobDefinitionVersionRecord save(String jobName, int version, String description, String stepsJson,
                                           String author, String changeType, String changeNote) {
        Timestamp now = Timestamp.from(Instant.now());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + TABLE
                            + " (JOB_NAME, VERSION_NUMBER, DESCRIPTION, STEPS_JSON, AUTHOR, CHANGE_TYPE,"
                            + " CHANGE_NOTE, CREATED_AT) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, jobName);
            ps.setInt(2, version);
            ps.setString(3, description);
            ps.setString(4, stepsJson);
            ps.setString(5, author);
            ps.setString(6, changeType);
            ps.setString(7, changeNote);
            ps.setTimestamp(8, now);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return new JobDefinitionVersionRecord(id, jobName, version, description, stepsJson,
                author, changeType, changeNote, now.toInstant());
    }

    /** Every snapshot for a job, newest first. */
    public List<JobDefinitionVersionRecord> findByJobName(String jobName) {
        return jdbc.query("SELECT * FROM " + TABLE + " WHERE JOB_NAME = ? ORDER BY VERSION_NUMBER DESC",
                MAPPER, jobName);
    }

    /** A specific snapshot. */
    public Optional<JobDefinitionVersionRecord> find(String jobName, int version) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT * FROM " + TABLE + " WHERE JOB_NAME = ? AND VERSION_NUMBER = ?",
                    MAPPER, jobName, version));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean existsByJobName(String jobName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE JOB_NAME = ?", Integer.class, jobName);
        return count != null && count > 0;
    }

    public void deleteByJobName(String jobName) {
        jdbc.update("DELETE FROM " + TABLE + " WHERE JOB_NAME = ?", jobName);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
