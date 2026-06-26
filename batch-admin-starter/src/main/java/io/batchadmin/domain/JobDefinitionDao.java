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
 * JDBC access to persisted dynamic job definitions.
 */
public class JobDefinitionDao {

    private static final String TABLE = "BATCH_ADMIN_JOB_DEFINITION";

    private static final RowMapper<JobDefinitionRecord> MAPPER = (rs, rowNum) -> new JobDefinitionRecord(
            rs.getLong("ID"),
            rs.getString("JOB_NAME"),
            rs.getString("DESCRIPTION"),
            rs.getString("STEPS_JSON"),
            toInstant(rs.getTimestamp("CREATED_AT")));

    private final JdbcTemplate jdbc;

    public JobDefinitionDao(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public JobDefinitionRecord save(String jobName, String description, String stepsJson) {
        Timestamp now = Timestamp.from(Instant.now());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + TABLE + " (JOB_NAME, DESCRIPTION, STEPS_JSON, CREATED_AT) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, jobName);
            ps.setString(2, description);
            ps.setString(3, stepsJson);
            ps.setTimestamp(4, now);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return new JobDefinitionRecord(id, jobName, description, stepsJson, now.toInstant());
    }

    public void update(String jobName, String description, String stepsJson) {
        jdbc.update("UPDATE " + TABLE + " SET DESCRIPTION = ?, STEPS_JSON = ? WHERE JOB_NAME = ?",
                description, stepsJson, jobName);
    }

    public Optional<JobDefinitionRecord> findByJobName(String jobName) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT * FROM " + TABLE + " WHERE JOB_NAME = ?", MAPPER, jobName));
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

    public List<JobDefinitionRecord> findAll() {
        return jdbc.query("SELECT * FROM " + TABLE + " ORDER BY ID", MAPPER);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Long.class);
        return count != null ? count : 0L;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
