package io.batchadmin.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.batch.item.ItemProcessor;

/**
 * Generic Spring Batch {@link ItemProcessor} that serializes each item to a JSON string with Jackson.
 *
 * <p>It pairs naturally with the SQL readers (which produce {@code Map<String, Object>} rows) but
 * works with any item type, so a typical chunk step reads rows, turns them into JSON here, and hands
 * the JSON to a {@link GenericJsonItemWriter}:</p>
 *
 * <pre>{@code
 * .processor(new JsonItemProcessor<Map<String, Object>>())
 * }</pre>
 *
 * @param <I> the input item type
 */
public class JsonItemProcessor<I> implements ItemProcessor<I, String> {

    private final ObjectMapper objectMapper;

    /** Uses a sensible default {@link ObjectMapper} (registered modules, e.g. java.time support). */
    public JsonItemProcessor() {
        this(defaultObjectMapper());
    }

    public JsonItemProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String process(I item) throws Exception {
        return item == null ? null : objectMapper.writeValueAsString(item);
    }

    private static ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
