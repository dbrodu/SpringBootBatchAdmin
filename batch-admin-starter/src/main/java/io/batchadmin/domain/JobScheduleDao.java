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
 * JDBC access to persisted cron schedules.
 */
public class JobScheduleDao {

    private static final String TABLE = "BATCH_ADMIN_JOB_SCHEDULE";

    private static final RowMapper<JobScheduleRecord> MAPPER = (rs, rowNum) -> new JobScheduleRecord(
            rs.getLong("ID"),
            rs.getString("JOB_NAME"),
            rs.getString("CRON_EXPRESSION"),
            rs.getString("PARAMETERS_JSON"),
            rs.getBoolean("ENABLED"),
            rs.getString("DESCRIPTION"),
            toInstant(rs.getTimestamp("CREATED_AT")));

    private final JdbcTemplate jdbc;

    public JobScheduleDao(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public JobScheduleRecord insert(String jobName, String cron, String parametersJson,
                                    boolean enabled, String description) {
        Timestamp now = Timestamp.from(Instant.now());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + TABLE
                            + " (JOB_NAME, CRON_EXPRESSION, PARAMETERS_JSON, ENABLED, DESCRIPTION, CREATED_AT)"
                            + " VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, jobName);
            ps.setString(2, cron);
            ps.setString(3, parametersJson);
            ps.setBoolean(4, enabled);
            ps.setString(5, description);
            ps.setTimestamp(6, now);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return new JobScheduleRecord(id, jobName, cron, parametersJson, enabled, description, now.toInstant());
    }

    public void update(long id, String jobName, String cron, String parametersJson,
                       boolean enabled, String description) {
        jdbc.update("UPDATE " + TABLE
                        + " SET JOB_NAME = ?, CRON_EXPRESSION = ?, PARAMETERS_JSON = ?, ENABLED = ?, DESCRIPTION = ?"
                        + " WHERE ID = ?",
                jobName, cron, parametersJson, enabled, description, id);
    }

    public void setEnabled(long id, boolean enabled) {
        jdbc.update("UPDATE " + TABLE + " SET ENABLED = ? WHERE ID = ?", enabled, id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM " + TABLE + " WHERE ID = ?", id);
    }

    public Optional<JobScheduleRecord> findById(long id) {
        try {
            return Optional.of(jdbc.queryForObject("SELECT * FROM " + TABLE + " WHERE ID = ?", MAPPER, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<JobScheduleRecord> findAll() {
        return jdbc.query("SELECT * FROM " + TABLE + " ORDER BY ID", MAPPER);
    }

    public List<JobScheduleRecord> findEnabled() {
        return jdbc.query("SELECT * FROM " + TABLE + " WHERE ENABLED = TRUE ORDER BY ID", MAPPER);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
