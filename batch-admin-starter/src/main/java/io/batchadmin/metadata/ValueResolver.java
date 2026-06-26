package io.batchadmin.metadata;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * Resolves values that may contain <b>SpEL</b> template expressions, so job parameters and
 * dynamic-job step properties can be wired from a metadata-driven architecture.
 *
 * <p>Anything between <code>#{ }</code> is evaluated against a root object; surrounding text is kept.
 * The root exposes:</p>
 * <ul>
 *   <li><code>metadata</code> — the {@link MetadataService}, e.g. <code>#{metadata.get('region')}</code>;</li>
 *   <li><code>today</code> ({@link LocalDate}), <code>now</code> ({@link Instant}),
 *       <code>timestamp</code> ({@link LocalDateTime}).</li>
 * </ul>
 *
 * <p>Examples: <code>region=#{metadata.get('region')}</code>, <code>#{today}</code>,
 * <code>orders-#{metadata.get('tenant')}</code>.</p>
 *
 * <p>A safe {@link SimpleEvaluationContext} is used (property reads + instance method calls only, no
 * type references or constructors). Expression support can be disabled entirely.</p>
 */
public class ValueResolver {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParserContext TEMPLATE = new TemplateParserContext();

    private final MetadataService metadataService;
    private final boolean enabled;

    public ValueResolver(MetadataService metadataService, boolean enabled) {
        this.metadataService = metadataService;
        this.enabled = enabled;
    }

    /** Resolves a single string, evaluating any {@code #{...}} expression it contains. */
    public String resolve(String value) {
        if (!enabled || value == null || !value.contains("#{")) {
            return value;
        }
        EvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().withInstanceMethods().build();
        Root root = new Root(metadataService, LocalDate.now(), Instant.now(), LocalDateTime.now());
        // Evaluate to the raw object (avoids needing a ConversionService for e.g. LocalDate),
        // then stringify — a pure '#{today}' yields a LocalDate, a templated value yields a String.
        Object result = PARSER.parseExpression(value, TEMPLATE).getValue(context, root);
        return result == null ? null : String.valueOf(result);
    }

    /** Resolves every value of a string map. */
    public Map<String, String> resolveAll(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return values;
        }
        Map<String, String> resolved = new LinkedHashMap<>(values.size());
        values.forEach((key, value) -> resolved.put(key, resolve(value)));
        return resolved;
    }

    /** Resolves the string values of a property map (non-string values are kept as-is). */
    public Map<String, Object> resolveProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return properties;
        }
        Map<String, Object> resolved = new LinkedHashMap<>(properties.size());
        properties.forEach((key, value) ->
                resolved.put(key, value instanceof String s ? resolve(s) : value));
        return resolved;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Root object exposed to expressions (JavaBean getters so property access works safely). */
    public static final class Root {
        private final MetadataService metadata;
        private final LocalDate today;
        private final Instant now;
        private final LocalDateTime timestamp;

        Root(MetadataService metadata, LocalDate today, Instant now, LocalDateTime timestamp) {
            this.metadata = metadata;
            this.today = today;
            this.now = now;
            this.timestamp = timestamp;
        }

        public MetadataService getMetadata() {
            return metadata;
        }

        public LocalDate getToday() {
            return today;
        }

        public Instant getNow() {
            return now;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}
