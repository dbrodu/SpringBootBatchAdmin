package io.batchadmin.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonItemProcessorTest {

    @Test
    void serializesMapRowsToJson() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("name", "Alice");
        row.put("city", "Paris");

        String json = new JsonItemProcessor<Map<String, Object>>().process(row);

        assertThat(json).isEqualTo("{\"id\":1,\"name\":\"Alice\",\"city\":\"Paris\"}");
    }

    @Test
    void serializesTypedItems() throws Exception {
        record Person(int id, String name) {
        }
        String json = new JsonItemProcessor<Person>().process(new Person(7, "Bob"));
        assertThat(json).contains("\"id\":7").contains("\"name\":\"Bob\"");
    }

    @Test
    void passesNullThrough() throws Exception {
        assertThat(new JsonItemProcessor<Object>().process(null)).isNull();
    }
}
