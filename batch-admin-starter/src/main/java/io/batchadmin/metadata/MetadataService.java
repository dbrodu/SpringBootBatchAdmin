package io.batchadmin.metadata;

import java.util.Map;

/**
 * Extension point that lets the admin component integrate into a <b>metadata-driven</b> architecture:
 * job parameters and dynamic-job step properties can reference values resolved from the host's
 * metadata source (a registry, a configuration service, a database, …) through SpEL expressions such
 * as {@code #{metadata.get('region')}}.
 *
 * <p>Host applications contribute their own implementation as a Spring bean; the component ships a
 * simple property-backed default ({@link PropertiesMetadataService}, populated from
 * {@code batch.admin.metadata.*}).</p>
 */
public interface MetadataService {

    /**
     * Resolves a single metadata value.
     *
     * @param key metadata key
     * @return the value, or {@code null} if unknown
     */
    Object get(String key);

    /** Convenience: the value as text, or {@code null}. */
    default String getString(String key) {
        Object value = get(key);
        return value == null ? null : String.valueOf(value);
    }

    /** Whether a key is known. */
    default boolean contains(String key) {
        return get(key) != null;
    }

    /** Optional snapshot of all known metadata (used for tooling/diagnostics). */
    default Map<String, Object> all() {
        return Map.of();
    }
}
