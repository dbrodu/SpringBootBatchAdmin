package io.batchadmin.sql;

import org.springframework.batch.item.database.JdbcCursorItemReader;

/**
 * A generic, restartable Spring Batch {@link org.springframework.batch.item.ItemReader} that streams
 * the rows of an arbitrary SQL query from a {@code DataSource}.
 *
 * <p>It is a thin {@link JdbcCursorItemReader} specialization with sensible defaults (a name and a
 * fetch size) so it can be used as a reusable building block by host jobs. Build it with
 * {@link GenericSqlItemReaderBuilder} rather than wiring it by hand, e.g.:</p>
 *
 * <pre>{@code
 * ItemReader<Map<String, Object>> reader = GenericSqlItemReaderBuilder.mapRows()
 *         .name("ordersReader")
 *         .dataSource(dataSource)
 *         .sql("SELECT id, customer, amount FROM orders WHERE status = ?")
 *         .parameters("NEW")
 *         .build();
 * }</pre>
 *
 * <p>The cursor implementation streams rows (it does not load the whole result set into memory).
 * Restart re-executes the query and skips the already-processed rows, so the query must impose a
 * stable {@code ORDER BY} for restarts to be correct; for very large or restart-critical jobs a
 * paging reader is usually preferable.</p>
 *
 * @param <T> the row type produced by the configured {@code RowMapper}
 */
public class GenericSqlItemReader<T> extends JdbcCursorItemReader<T> {

    /** Default JDBC fetch size; tune via {@link GenericSqlItemReaderBuilder#fetchSize(int)}. */
    public static final int DEFAULT_FETCH_SIZE = 1000;

    public GenericSqlItemReader() {
        setName("genericSqlItemReader");
        setFetchSize(DEFAULT_FETCH_SIZE);
    }
}
