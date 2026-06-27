package io.batchadmin.domain;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
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
 * JDBC access to persisted alert rules.
 */
public class AlertRuleDao {

    private static final String TABLE = "BATCH_ADMIN_ALERT_RULE";

    private static final RowMapper<AlertRuleRecord> MAPPER = (rs, rowNum) -> new AlertRuleRecord(
            rs.getLong("ID"),
            rs.getString("JOB_NAME"),
            rs.getString("RULE_TYPE"),
            (Long) rs.getObject("THRESHOLD_MILLIS"),
            rs.getString("CHANNEL"),
            rs.getString("TARGET"),
            rs.getBoolean("ENABLED"),
            rs.getString("DESCRIPTION"),
            toInstant(rs.getTimestamp("CREATED_AT")));

    private final JdbcTemplate jdbc;

    public AlertRuleDao(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public AlertRuleRecord insert(String jobName, String ruleType, Long thresholdMillis, String channel,
                                  String target, boolean enabled, String description) {
        Timestamp now = Timestamp.from(Instant.now());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + TABLE
                            + " (JOB_NAME, RULE_TYPE, THRESHOLD_MILLIS, CHANNEL, TARGET, ENABLED,"
                            + " DESCRIPTION, CREATED_AT) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, jobName);
            ps.setString(2, ruleType);
            if (thresholdMillis != null) {
                ps.setLong(3, thresholdMillis);
            } else {
                ps.setNull(3, Types.BIGINT);
            }
            ps.setString(4, channel);
            ps.setString(5, target);
            ps.setBoolean(6, enabled);
            ps.setString(7, description);
            ps.setTimestamp(8, now);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return new AlertRuleRecord(id, jobName, ruleType, thresholdMillis, channel, target, enabled,
                description, now.toInstant());
    }

    public void setEnabled(long id, boolean enabled) {
        jdbc.update("UPDATE " + TABLE + " SET ENABLED = ? WHERE ID = ?", enabled, id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM " + TABLE + " WHERE ID = ?", id);
    }

    public Optional<AlertRuleRecord> findById(long id) {
        try {
            return Optional.of(jdbc.queryForObject("SELECT * FROM " + TABLE + " WHERE ID = ?", MAPPER, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<AlertRuleRecord> findAll() {
        return jdbc.query("SELECT * FROM " + TABLE + " ORDER BY ID", MAPPER);
    }

    /** Enabled rules matching the given job: either rules scoped to it or wildcard ({@code *}) rules. */
    public List<AlertRuleRecord> findEnabledForJob(String jobName) {
        return jdbc.query("SELECT * FROM " + TABLE
                        + " WHERE ENABLED = TRUE AND (JOB_NAME = ? OR JOB_NAME = '*') ORDER BY ID",
                MAPPER, jobName);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
