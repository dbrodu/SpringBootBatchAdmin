package io.batchadmin.sql;

import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.Assert;

/**
 * Fluent builder for a {@link GenericSqlItemReader}.
 *
 * <p>Two entry points are provided:</p>
 * <ul>
 *   <li>{@link #mapRows()} — each row is read as a case-insensitive, insertion-ordered
 *       {@code Map<String, Object>} (column label &rarr; value), requiring no mapping code;</li>
 *   <li>{@link #mapWith(RowMapper)} — map each row to your own type.</li>
 * </ul>
 *
 * <pre>{@code
 * // Generic rows as maps:
 * GenericSqlItemReader<Map<String, Object>> reader = GenericSqlItemReaderBuilder.mapRows()
 *         .name("ordersReader")
 *         .dataSource(dataSource)
 *         .sql("SELECT id, customer, amount FROM orders WHERE status = ?")
 *         .parameters("NEW")
 *         .fetchSize(500)
 *         .build();
 *
 * // Typed rows:
 * GenericSqlItemReader<String> names = GenericSqlItemReaderBuilder
 *         .mapWith((rs, rowNum) -> rs.getString("name"))
 *         .name("names").dataSource(dataSource).sql("SELECT name FROM person ORDER BY id")
 *         .build();
 * }</pre>
 *
 * @param <T> the row type produced by the reader
 */
public final class GenericSqlItemReaderBuilder<T> {

    private String name = "genericSqlItemReader";
    private DataSource dataSource;
    private String sql;
    private RowMapper<T> rowMapper;
    private PreparedStatementSetter preparedStatementSetter;
    private int fetchSize = GenericSqlItemReader.DEFAULT_FETCH_SIZE;
    private boolean saveState = true;
    private Integer queryTimeoutSeconds;
    private Integer maxRows;

    private GenericSqlItemReaderBuilder() {
    }

    /** Reads each row as a {@code Map<String, Object>} (column label to value). */
    public static GenericSqlItemReaderBuilder<Map<String, Object>> mapRows() {
        GenericSqlItemReaderBuilder<Map<String, Object>> builder = new GenericSqlItemReaderBuilder<>();
        builder.rowMapper = new ColumnMapRowMapper();
        return builder;
    }

    /** Reads each row with the supplied {@link RowMapper}. */
    public static <R> GenericSqlItemReaderBuilder<R> mapWith(RowMapper<R> rowMapper) {
        Assert.notNull(rowMapper, "rowMapper must not be null");
        GenericSqlItemReaderBuilder<R> builder = new GenericSqlItemReaderBuilder<>();
        builder.rowMapper = rowMapper;
        return builder;
    }

    /** Logical name of the reader; required (used as the key for restart state). */
    public GenericSqlItemReaderBuilder<T> name(String name) {
        this.name = name;
        return this;
    }

    public GenericSqlItemReaderBuilder<T> dataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        return this;
    }

    public GenericSqlItemReaderBuilder<T> sql(String sql) {
        this.sql = sql;
        return this;
    }

    /** Overrides the row mapper set by {@link #mapRows()} / {@link #mapWith(RowMapper)}. */
    public GenericSqlItemReaderBuilder<T> rowMapper(RowMapper<T> rowMapper) {
        this.rowMapper = rowMapper;
        return this;
    }

    /** Binds positional ({@code ?}) query parameters, in order. */
    public GenericSqlItemReaderBuilder<T> parameters(Object... parameters) {
        this.preparedStatementSetter = (parameters == null || parameters.length == 0)
                ? null
                : new ArgumentPreparedStatementSetter(parameters);
        return this;
    }

    /** Sets a custom {@link PreparedStatementSetter} (mutually exclusive with {@link #parameters}). */
    public GenericSqlItemReaderBuilder<T> preparedStatementSetter(PreparedStatementSetter setter) {
        this.preparedStatementSetter = setter;
        return this;
    }

    public GenericSqlItemReaderBuilder<T> fetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
        return this;
    }

    /** Whether the reader persists its position so the step can restart where it left off. */
    public GenericSqlItemReaderBuilder<T> saveState(boolean saveState) {
        this.saveState = saveState;
        return this;
    }

    public GenericSqlItemReaderBuilder<T> queryTimeout(int seconds) {
        this.queryTimeoutSeconds = seconds;
        return this;
    }

    public GenericSqlItemReaderBuilder<T> maxRows(int maxRows) {
        this.maxRows = maxRows;
        return this;
    }

    public GenericSqlItemReader<T> build() {
        Assert.hasText(name, "name must be provided");
        Assert.notNull(dataSource, "dataSource must be provided");
        Assert.hasText(sql, "sql must be provided");
        Assert.notNull(rowMapper, "a rowMapper must be provided (use mapRows() or mapWith())");

        GenericSqlItemReader<T> reader = new GenericSqlItemReader<>();
        reader.setName(name);
        reader.setDataSource(dataSource);
        reader.setSql(sql);
        reader.setRowMapper(rowMapper);
        reader.setFetchSize(fetchSize);
        reader.setSaveState(saveState);
        if (preparedStatementSetter != null) {
            reader.setPreparedStatementSetter(preparedStatementSetter);
        }
        if (queryTimeoutSeconds != null) {
            reader.setQueryTimeout(queryTimeoutSeconds);
        }
        if (maxRows != null) {
            reader.setMaxRows(maxRows);
        }
        try {
            reader.afterPropertiesSet();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not initialize the SQL item reader: " + ex.getMessage(), ex);
        }
        return reader;
    }
}
