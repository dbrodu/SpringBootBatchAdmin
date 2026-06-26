package io.batchadmin.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class GenericSqlPagingItemReaderTest {

    private EmbeddedDatabase db;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        jdbc.execute("CREATE TABLE PERSON (ID INT PRIMARY KEY, NAME VARCHAR(50), CITY VARCHAR(50))");
        for (int i = 1; i <= 5; i++) {
            jdbc.update("INSERT INTO PERSON (ID, NAME, CITY) VALUES (?, ?, ?)",
                    i, "Person" + i, (i % 2 == 0) ? "Lyon" : "Paris");
        }
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void readsAllRowsAcrossPages() throws Exception {
        GenericSqlPagingItemReader<Map<String, Object>> reader = GenericSqlPagingItemReaderBuilder.mapRows()
                .name("people")
                .dataSource(db)
                .select("ID, NAME")
                .from("PERSON")
                .sortAsc("ID")
                .pageSize(2) // forces multiple pages over 5 rows
                .build();

        assertThat(readAll(reader)).extracting(r -> r.get("NAME"))
                .containsExactly("Person1", "Person2", "Person3", "Person4", "Person5");
    }

    @Test
    void appliesWhereWithNamedParameter() throws Exception {
        GenericSqlPagingItemReader<Map<String, Object>> reader = GenericSqlPagingItemReaderBuilder.mapRows()
                .name("byCity")
                .dataSource(db)
                .select("ID, NAME")
                .from("PERSON")
                .where("CITY = :city")
                .parameter("city", "Paris")
                .sortAsc("ID")
                .pageSize(2)
                .build();

        assertThat(readAll(reader)).extracting(r -> r.get("NAME"))
                .containsExactly("Person1", "Person3", "Person5");
    }

    @Test
    void requiresSortKey() {
        assertThatThrownBy(() -> GenericSqlPagingItemReaderBuilder.mapRows()
                .name("x").dataSource(db).select("ID").from("PERSON").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sort key");
    }

    private <T> List<T> readAll(GenericSqlPagingItemReader<T> reader) throws Exception {
        List<T> all = new ArrayList<>();
        reader.open(new ExecutionContext());
        try {
            T item;
            while ((item = reader.read()) != null) {
                all.add(item);
            }
        } finally {
            reader.close();
        }
        return all;
    }
}
