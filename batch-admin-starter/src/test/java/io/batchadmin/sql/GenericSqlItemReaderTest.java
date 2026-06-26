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

class GenericSqlItemReaderTest {

    private EmbeddedDatabase db;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        jdbc.execute("CREATE TABLE PERSON (ID INT PRIMARY KEY, NAME VARCHAR(50), CITY VARCHAR(50))");
        jdbc.update("INSERT INTO PERSON (ID, NAME, CITY) VALUES (?, ?, ?)", 1, "Alice", "Paris");
        jdbc.update("INSERT INTO PERSON (ID, NAME, CITY) VALUES (?, ?, ?)", 2, "Bob", "Lyon");
        jdbc.update("INSERT INTO PERSON (ID, NAME, CITY) VALUES (?, ?, ?)", 3, "Carol", "Paris");
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void readsAllRowsAsMaps() throws Exception {
        GenericSqlItemReader<Map<String, Object>> reader = GenericSqlItemReaderBuilder.mapRows()
                .name("personReader")
                .dataSource(db)
                .sql("SELECT ID, NAME, CITY FROM PERSON ORDER BY ID")
                .build();

        List<Map<String, Object>> rows = readAll(reader);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("NAME", "Alice").containsEntry("CITY", "Paris");
        // ColumnMapRowMapper keys are case-insensitive.
        assertThat(rows.get(1).get("name")).isEqualTo("Bob");
        assertThat(rows).extracting(r -> r.get("NAME")).containsExactly("Alice", "Bob", "Carol");
    }

    @Test
    void bindsPositionalParameters() throws Exception {
        GenericSqlItemReader<Map<String, Object>> reader = GenericSqlItemReaderBuilder.mapRows()
                .name("byCity")
                .dataSource(db)
                .sql("SELECT NAME FROM PERSON WHERE CITY = ? ORDER BY ID")
                .parameters("Paris")
                .build();

        assertThat(readAll(reader)).extracting(r -> r.get("NAME")).containsExactly("Alice", "Carol");
    }

    @Test
    void mapsRowsWithACustomMapper() throws Exception {
        GenericSqlItemReader<String> reader = GenericSqlItemReaderBuilder
                .mapWith((rs, rowNum) -> rs.getString("NAME"))
                .name("names")
                .dataSource(db)
                .sql("SELECT NAME FROM PERSON ORDER BY NAME")
                .build();

        assertThat(readAll(reader)).containsExactly("Alice", "Bob", "Carol");
    }

    @Test
    void honoursMaxRows() throws Exception {
        GenericSqlItemReader<Map<String, Object>> reader = GenericSqlItemReaderBuilder.mapRows()
                .name("capped")
                .dataSource(db)
                .sql("SELECT ID FROM PERSON ORDER BY ID")
                .maxRows(2)
                .build();

        assertThat(readAll(reader)).hasSize(2);
    }

    @Test
    void rejectsMissingSqlOrDataSource() {
        assertThatThrownBy(() -> GenericSqlItemReaderBuilder.mapRows().name("x").dataSource(db).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sql");
        assertThatThrownBy(() -> GenericSqlItemReaderBuilder.mapRows().name("x").sql("SELECT 1").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataSource");
    }

    private <T> List<T> readAll(GenericSqlItemReader<T> reader) throws Exception {
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
