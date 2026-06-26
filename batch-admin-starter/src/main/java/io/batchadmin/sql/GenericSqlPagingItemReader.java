package io.batchadmin.sql;

import org.springframework.batch.item.database.JdbcPagingItemReader;

/**
 * A generic, restartable Spring Batch {@link org.springframework.batch.item.ItemReader} that reads
 * an arbitrary SQL query <b>page by page</b> ({@code LIMIT}/{@code OFFSET}-style, adapted to the
 * detected database), rather than holding a single cursor open.
 *
 * <p>Paging is the recommended approach for very large result sets and for restart-critical jobs:
 * each page is a fresh, ordered query, so restart resumes cleanly at the next page. It requires an
 * unambiguous sort key. Build it with {@link GenericSqlPagingItemReaderBuilder}:</p>
 *
 * <pre>{@code
 * ItemReader<Map<String, Object>> reader = GenericSqlPagingItemReaderBuilder.mapRows()
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
 * @param <T> the row type produced by the configured {@code RowMapper}
 */
public class GenericSqlPagingItemReader<T> extends JdbcPagingItemReader<T> {

    /** Default page size; tune via {@link GenericSqlPagingItemReaderBuilder#pageSize(int)}. */
    public static final int DEFAULT_PAGE_SIZE = 1000;

    public GenericSqlPagingItemReader() {
        setName("genericSqlPagingItemReader");
        setPageSize(DEFAULT_PAGE_SIZE);
    }
}
