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

    /** Whether the browser GUI (Angular app) is served from the application port. */
    private boolean uiEnabled = true;

    /** Scheduling (cron based) sub-configuration. */
    private final Scheduling scheduling = new Scheduling();

    /** Dynamic, on-the-fly job creation sub-configuration. */
    private final DynamicJobs dynamicJobs = new DynamicJobs();

    /** Observability sub-configuration. */
    private final Observability observability = new Observability();

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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAllowCommandTasklets() {
            return allowCommandTasklets;
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
}
