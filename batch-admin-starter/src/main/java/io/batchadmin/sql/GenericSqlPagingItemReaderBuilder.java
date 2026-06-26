package io.batchadmin.sql;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.Assert;

/**
 * Fluent builder for a {@link GenericSqlPagingItemReader}.
 *
 * <p>The query is described in parts ({@code select} / {@code from} / optional {@code where}) plus a
 * mandatory sort key; the right paging SQL for the underlying database is generated automatically.
 * Use {@link #mapRows()} to read rows as {@code Map<String, Object>} or {@link #mapWith(RowMapper)}
 * to map to your own type.</p>
 *
 * <pre>{@code
 * GenericSqlPagingItemReader<Map<String, Object>> reader = GenericSqlPagingItemReaderBuilder.mapRows()
 *         .name("ordersReader")
 *         .dataSource(dataSource)
 *         .select("id, customer, amount")
 *         .from("orders")
 *         .where("status = :status")
 *         .parameter("status", "NEW")
 *         .sortAsc("id")
 *         .pageSize(500)
 *         .build();
 * }</pre>
 *
 * @param <T> the row type produced by the reader
 */
public final class GenericSqlPagingItemReaderBuilder<T> {

    private String name = "genericSqlPagingItemReader";
    private DataSource dataSource;
    private String selectClause;
    private String fromClause;
    private String whereClause;
    private final Map<String, Order> sortKeys = new LinkedHashMap<>();
    private final Map<String, Object> parameterValues = new LinkedHashMap<>();
    private RowMapper<T> rowMapper;
    private int pageSize = GenericSqlPagingItemReader.DEFAULT_PAGE_SIZE;
    private boolean saveState = true;

    private GenericSqlPagingItemReaderBuilder() {
    }

    /** Reads each row as a {@code Map<String, Object>} (column label to value). */
    public static GenericSqlPagingItemReaderBuilder<Map<String, Object>> mapRows() {
        GenericSqlPagingItemReaderBuilder<Map<String, Object>> builder = new GenericSqlPagingItemReaderBuilder<>();
        builder.rowMapper = new ColumnMapRowMapper();
        return builder;
    }

    /** Reads each row with the supplied {@link RowMapper}. */
    public static <R> GenericSqlPagingItemReaderBuilder<R> mapWith(RowMapper<R> rowMapper) {
        Assert.notNull(rowMapper, "rowMapper must not be null");
        GenericSqlPagingItemReaderBuilder<R> builder = new GenericSqlPagingItemReaderBuilder<>();
        builder.rowMapper = rowMapper;
        return builder;
    }

    public GenericSqlPagingItemReaderBuilder<T> name(String name) {
        this.name = name;
        return this;
    }

    public GenericSqlPagingItemReaderBuilder<T> dataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        return this;
    }

    /** The column list, without the {@code SELECT} keyword, e.g. {@code "id, name, amount"}. */
    public GenericSqlPagingItemReaderBuilder<T> select(String selectClause) {
        this.selectClause = selectClause;
        return this;
    }

    /** The {@code FROM} body, without the keyword, e.g. {@code "orders o JOIN customer c ON ..."}. */
    public GenericSqlPagingItemReaderBuilder<T> from(String fromClause) {
        this.fromClause = fromClause;
        return this;
    }

    /** Optional filter, without the {@code WHERE} keyword; may use named parameters ({@code :name}). */
    public GenericSqlPagingItemReaderBuilder<T> where(String whereClause) {
        this.whereClause = whereClause;
        return this;
    }

    public GenericSqlPagingItemReaderBuilder<T> rowMapper(RowMapper<T> rowMapper) {
        this.rowMapper = rowMapper;
        return this;
    }

    /** Binds a named parameter used in the {@code where} clause. */
    public GenericSqlPagingItemReaderBuilder<T> parameter(String name, Object value) {
        this.parameterValues.put(name, value);
        return this;
    }

    public GenericSqlPagingItemReaderBuilder<T> parameters(Map<String, Object> values) {
        if (values != null) {
            this.parameterValues.putAll(values);
        }
        return this;
    }

    /** Adds ascending sort keys (insertion order is preserved). At least one sort key is required. */
    public GenericSqlPagingItemReaderBuilder<T> sortAsc(String... columns) {
        for (String column : columns) {
            this.sortKeys.put(column, Order.ASCENDING);
        }
        return this;
    }

    /** Adds descending sort keys. */
    public GenericSqlPagingItemReaderBuilder<T> sortDesc(String... columns) {
        for (String column : columns) {
            this.sortKeys.put(column, Order.DESCENDING);
        }
        return this;
    }

    public GenericSqlPagingItemReaderBuilder<T> sortKeys(Map<String, Order> keys) {
        if (keys != null) {
            this.sortKeys.putAll(keys);
        }
        return this;
    }

    public GenericSqlPagingItemReaderBuilder<T> pageSize(int pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public GenericSqlPagingItemReaderBuilder<T> saveState(boolean saveState) {
        this.saveState = saveState;
        return this;
    }

    public GenericSqlPagingItemReader<T> build() {
        Assert.hasText(name, "name must be provided");
        Assert.notNull(dataSource, "dataSource must be provided");
        Assert.hasText(selectClause, "select clause must be provided");
        Assert.hasText(fromClause, "from clause must be provided");
        Assert.notNull(rowMapper, "a rowMapper must be provided (use mapRows() or mapWith())");
        Assert.notEmpty(sortKeys, "at least one sort key is required for paging (use sortAsc()/sortDesc())");

        PagingQueryProvider queryProvider = buildQueryProvider();

        GenericSqlPagingItemReader<T> reader = new GenericSqlPagingItemReader<>();
        reader.setName(name);
        reader.setDataSource(dataSource);
        reader.setQueryProvider(queryProvider);
        reader.setRowMapper(rowMapper);
        reader.setPageSize(pageSize);
        reader.setSaveState(saveState);
        if (!parameterValues.isEmpty()) {
            reader.setParameterValues(new LinkedHashMap<>(parameterValues));
        }
        try {
            reader.afterPropertiesSet();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not initialize the paging SQL item reader: " + ex.getMessage(), ex);
        }
        return reader;
    }

    private PagingQueryProvider buildQueryProvider() {
        SqlPagingQueryProviderFactoryBean factory = new SqlPagingQueryProviderFactoryBean();
        factory.setDataSource(dataSource);
        factory.setSelectClause(selectClause);
        factory.setFromClause(fromClause);
        if (whereClause != null && !whereClause.isBlank()) {
            factory.setWhereClause(whereClause);
        }
        factory.setSortKeys(new LinkedHashMap<>(sortKeys));
        try {
            return factory.getObject();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build the paging query provider: " + ex.getMessage(), ex);
        }
    }
}
