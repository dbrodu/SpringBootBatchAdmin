package io.batchadmin.metadata;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default {@link MetadataService} backed by a static map, populated from the
 * {@code batch.admin.metadata.*} properties. Handy out of the box and for tests; replace it with a
 * bean of your own to plug a real metadata source (it backs off via {@code @ConditionalOnMissingBean}).
 */
public class PropertiesMetadataService implements MetadataService {

    private final Map<String, Object> values;

    public PropertiesMetadataService(Map<String, String> values) {
        this.values = new LinkedHashMap<>();
        if (values != null) {
            this.values.putAll(values);
        }
    }

    @Override
    public Object get(String key) {
        return values.get(key);
    }

    @Override
    public Map<String, Object> all() {
        return Map.copyOf(values);
    }
}
