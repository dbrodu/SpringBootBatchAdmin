package io.batchadmin.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the Spring Batch Admin component.
 *
 * <p>All properties are prefixed with {@code batch.admin}. The component is enabled by default
 * and self-initializes as soon as Spring Batch is on the classpath.</p>
 */
@ConfigurationProperties(prefix = "batch.admin")
public class BatchAdminProperties {

    /** Master switch for the whole administration component. */
    private boolean enabled = true;

    /**
     * Base path under which both the REST API and the GUI are exposed, relative to the
     * Spring Boot server port. The GUI is served at this path and the REST API at
     * {@code <basePath>/api}.
     */
    private String basePath = "/batch-admin";

    /** Whether the server-rendered Thymeleaf GUI is served from the application port. */
    private boolean uiEnabled = true;

    /** Scheduling (cron based) sub-configuration. */
    private final Scheduling scheduling = new Scheduling();

    /** Dynamic, on-the-fly job creation sub-configuration. */
    private final DynamicJobs dynamicJobs = new DynamicJobs();

    /** Observability sub-configuration. */
    private final Observability observability = new Observability();

    /** Per-execution log capture sub-configuration. */
    private final Logs logs = new Logs();

    /** SpEL expression resolution sub-configuration. */
    private final Expressions expressions = new Expressions();

    /**
     * Static metadata exposed to expressions as {@code #{metadata.get('key')}} via the default
     * {@link io.batchadmin.metadata.PropertiesMetadataService}. Replace that bean to plug a real
     * metadata source.
     */
    private final java.util.Map<String, String> metadata = new java.util.LinkedHashMap<>();

    /** Optional OAuth2/OIDC security sub-configuration. */
    private final Security security = new Security();

    /** Pub/sub job-lifecycle events sub-configuration. */
    private final Events events = new Events();

    /** Event-driven job chaining (run a job when another finishes). */
    private final Triggers triggers = new Triggers();

    /** Failure / SLA alerting (notify when a job fails or overruns). */
    private final Alerts alerts = new Alerts();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = normalize(basePath);
    }

    public boolean isUiEnabled() {
        return uiEnabled;
    }

    public void setUiEnabled(boolean uiEnabled) {
        this.uiEnabled = uiEnabled;
    }

    public Scheduling getScheduling() {
        return scheduling;
    }

    public DynamicJobs getDynamicJobs() {
        return dynamicJobs;
    }

    public Observability getObservability() {
        return observability;
    }

    public Logs getLogs() {
        return logs;
    }

    public Expressions getExpressions() {
        return expressions;
    }

    public java.util.Map<String, String> getMetadata() {
        return metadata;
    }

    public Security getSecurity() {
        return security;
    }

    public Events getEvents() {
        return events;
    }

    public Triggers getTriggers() {
        return triggers;
    }

    public Alerts getAlerts() {
        return alerts;
    }

    /** The API path is always the base path suffixed with {@code /api}. */
    public String getApiPath() {
        return basePath + "/api";
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/batch-admin";
        }
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    public static class Alerts {
        /** Whether failure / SLA alerting is enabled. */
        private boolean enabled = true;
        /** How many recently fired alerts to keep in memory for the GUI/API. */
        private int recentBufferSize = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRecentBufferSize() {
            return recentBufferSize;
        }

        public void setRecentBufferSize(int recentBufferSize) {
            this.recentBufferSize = recentBufferSize;
        }
    }

    public static class Triggers {
        /** Whether event-driven job chaining (run a job when another finishes) is enabled. */
        private boolean enabled = true;
        /**
         * Safety cap on how deep a trigger chain may go (A→B→C→…), to bound run-away or cyclic
         * chains. A launch beyond this depth is skipped and logged.
         */
        private int maxChainDepth = 25;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxChainDepth() {
            return maxChainDepth;
        }

        public void setMaxChainDepth(int maxChainDepth) {
            this.maxChainDepth = maxChainDepth;
        }
    }

    public static class Scheduling {
        /** Whether cron scheduling of jobs is enabled. */
        private boolean enabled = true;
        /** Size of the thread pool backing the scheduler. */
        private int poolSize = 4;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }
    }

    public static class DynamicJobs {
        /** Whether jobs can be created on the fly through the API/GUI. */
        private boolean enabled = true;
        /**
         * Whether step definitions backed by a system command tasklet are allowed.
         * Disabled by default because it executes arbitrary OS commands.
         */
        private boolean allowCommandTasklets = false;

        /**
         * Whether the steps of the host's existing jobs are exposed as reusable building blocks, so a
         * new on-the-fly job can drop in a step the application already defines. Enabled by default.
         */
        private boolean reuseExistingSteps = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAllowCommandTasklets() {
            return allowCommandTasklets;
        }

        public boolean isReuseExistingSteps() {
            return reuseExistingSteps;
        }

        public void setReuseExistingSteps(boolean reuseExistingSteps) {
            this.reuseExistingSteps = reuseExistingSteps;
        }

        public void setAllowCommandTasklets(boolean allowCommandTasklets) {
            this.allowCommandTasklets = allowCommandTasklets;
        }
    }

    public static class Observability {
        /** Maximum number of recent executions returned by observability endpoints. */
        private int recentExecutionsLimit = 100;
        /** Whether Micrometer counters/timers are published for job lifecycle events. */
        private boolean metricsEnabled = true;

        public int getRecentExecutionsLimit() {
            return recentExecutionsLimit;
        }

        public void setRecentExecutionsLimit(int recentExecutionsLimit) {
            this.recentExecutionsLimit = recentExecutionsLimit;
        }

        public boolean isMetricsEnabled() {
            return metricsEnabled;
        }

        public void setMetricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
        }
    }

    public static class Expressions {
        /** Whether SpEL expression resolution of parameters and step properties is enabled. */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Logs {
        /** Whether per-execution log capture is enabled (requires Logback). */
        private boolean enabled = true;

        /**
         * Minimum level a log event must have to be captured and stored against an execution
         * (TRACE, DEBUG, INFO, WARN, ERROR). Note that events below the application's effective
         * logger level are never emitted, so they cannot be captured regardless of this value.
         */
        private String captureLevel = "INFO";

        /** Default minimum level returned when reading logs, if the caller does not specify one. */
        private String defaultReadLevel = "INFO";

        /** Maximum number of log records retained per execution (oldest are dropped). */
        private int maxRecordsPerExecution = 2000;

        /** Maximum number of executions kept in the in-memory log buffer (oldest are evicted). */
        private int maxExecutions = 200;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCaptureLevel() {
            return captureLevel;
        }

        public void setCaptureLevel(String captureLevel) {
            this.captureLevel = captureLevel;
        }

        public String getDefaultReadLevel() {
            return defaultReadLevel;
        }

        public void setDefaultReadLevel(String defaultReadLevel) {
            this.defaultReadLevel = defaultReadLevel;
        }

        public int getMaxRecordsPerExecution() {
            return maxRecordsPerExecution;
        }

        public void setMaxRecordsPerExecution(int maxRecordsPerExecution) {
            this.maxRecordsPerExecution = maxRecordsPerExecution;
        }

        public int getMaxExecutions() {
            return maxExecutions;
        }

        public void setMaxExecutions(int maxExecutions) {
            this.maxExecutions = maxExecutions;
        }
    }

    /**
     * Optional OAuth2/OIDC protection of the component. Disabled by default so the component stays
     * non-intrusive; host applications already secured by their own filter chain keep that behaviour.
     *
     * <p>When {@code enabled} is {@code true} the component installs two dedicated filter chains
     * scoped to its own paths:</p>
     * <ul>
     *   <li>the <b>REST API</b> ({@code <basePath>/api/**}) becomes a stateless OAuth2
     *       <i>resource server</i> validating bearer JWTs — configured through the standard
     *       {@code spring.security.oauth2.resourceserver.jwt.*} properties;</li>
     *   <li>the <b>GUI</b> ({@code <basePath>/**}) uses interactive OAuth2/OIDC
     *       <i>login</i> — configured through the standard
     *       {@code spring.security.oauth2.client.registration.*} properties.</li>
     * </ul>
     * Each chain activates only when its underlying support is configured, so an API-only or a
     * GUI-only deployment is fine.
     */
    public static class Security {
        /** Master switch for the component's own OAuth2/OIDC filter chains. */
        private boolean enabled = false;

        /**
         * Authority required to call the REST API (e.g. {@code SCOPE_batch.admin}). When blank any
         * authenticated bearer token is accepted.
         */
        private String apiAuthority;

        /**
         * Authority required to use the GUI (e.g. {@code ROLE_BATCH_ADMIN} or {@code SCOPE_openid}).
         * When blank any authenticated OIDC user is accepted.
         */
        private String uiAuthority;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiAuthority() {
            return apiAuthority;
        }

        public void setApiAuthority(String apiAuthority) {
            this.apiAuthority = apiAuthority;
        }

        public String getUiAuthority() {
            return uiAuthority;
        }

        public void setUiAuthority(String uiAuthority) {
            this.uiAuthority = uiAuthority;
        }
    }

    /**
     * Pub/sub of job-lifecycle events. Enabled by default with the in-process
     * {@link Broker#APPLICATION} publisher (Spring application events + log), so no infrastructure is
     * required. Switch {@code broker} to {@link Broker#RABBIT} (with Spring AMQP on the classpath) to
     * fan events out to a RabbitMQ topic exchange. Host applications may also register their own
     * {@code BatchEventPublisher} bean to integrate any other transport.
     */
    public static class Events {

        /** Transport used to publish lifecycle events. */
        public enum Broker {
            /** In-process Spring {@code ApplicationEvent} + log (default, no infrastructure). */
            APPLICATION,
            /** RabbitMQ topic exchange (requires Spring AMQP and a {@code RabbitTemplate}). */
            RABBIT
        }

        /** Whether lifecycle events are published at all. */
        private boolean enabled = true;

        /** Which publisher to activate. */
        private Broker broker = Broker.APPLICATION;

        /** Target exchange name when {@code broker=rabbit}. */
        private String exchange = "batch.admin.events";

        /**
         * Routing-key prefix when {@code broker=rabbit}; the full key is
         * {@code <prefix>.<jobName>.<eventType>}.
         */
        private String routingKeyPrefix = "batch.admin";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Broker getBroker() {
            return broker;
        }

        public void setBroker(Broker broker) {
            this.broker = broker;
        }

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getRoutingKeyPrefix() {
            return routingKeyPrefix;
        }

        public void setRoutingKeyPrefix(String routingKeyPrefix) {
            this.routingKeyPrefix = routingKeyPrefix;
        }
    }
}
