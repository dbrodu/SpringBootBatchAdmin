package io.batchadmin.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValueResolverTest {

    private ValueResolver resolver(boolean enabled, Map<String, String> metadata) {
        return new ValueResolver(new PropertiesMetadataService(metadata), enabled);
    }

    @Test
    void resolvesMetadataExpressions() {
        ValueResolver resolver = resolver(true, Map.of("region", "EU", "tenant", "acme"));
        assertThat(resolver.resolve("#{metadata.get('region')}")).isEqualTo("EU");
        assertThat(resolver.resolve("orders-#{metadata.get('tenant')}")).isEqualTo("orders-acme");
    }

    @Test
    void resolvesBuiltInVariables() {
        ValueResolver resolver = resolver(true, Map.of());
        assertThat(resolver.resolve("#{today}")).isEqualTo(LocalDate.now().toString());
    }

    @Test
    void passesPlainAndNullThrough() {
        ValueResolver resolver = resolver(true, Map.of());
        assertThat(resolver.resolve("just text")).isEqualTo("just text");
        assertThat(resolver.resolve(null)).isNull();
    }

    @Test
    void disabledLeavesExpressionsUntouched() {
        ValueResolver resolver = resolver(false, Map.of("region", "EU"));
        assertThat(resolver.resolve("#{metadata.get('region')}")).isEqualTo("#{metadata.get('region')}");
    }

    @Test
    void resolvesPropertyMapValues() {
        ValueResolver resolver = resolver(true, Map.of("region", "EU"));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("index", "orders-#{metadata.get('region')}");
        props.put("pageSize", 500);
        Map<String, Object> resolved = resolver.resolveProperties(props);
        assertThat(resolved).containsEntry("index", "orders-EU").containsEntry("pageSize", 500);
    }

    @Test
    void blocksUnsafeTypeReferences() {
        ValueResolver resolver = resolver(true, Map.of());
        assertThatThrownBy(() -> resolver.resolve("#{T(java.lang.System).exit(0)}"))
                .isInstanceOf(org.springframework.expression.spel.SpelEvaluationException.class);
    }
}
