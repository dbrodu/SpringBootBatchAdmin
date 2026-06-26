package io.batchadmin.service;

import io.batchadmin.autoconfigure.BatchAdminProperties;
import io.batchadmin.logs.BatchAdminLogStore;
import io.batchadmin.logs.JobLogEntry;
import io.batchadmin.logs.LogLevels;
import java.util.List;

/**
 * Reads the captured execution logs, applying a configurable minimum level filter. The execution is
 * first looked up so that unknown ids yield a 404 rather than an empty list.
 */
public class JobLogService {

    private final BatchAdminLogStore store;
    private final JobAdminService jobAdminService;
    private final BatchAdminProperties properties;

    public JobLogService(BatchAdminLogStore store,
                         JobAdminService jobAdminService,
                         BatchAdminProperties properties) {
        this.store = store;
        this.jobAdminService = jobAdminService;
        this.properties = properties;
    }

    /**
     * @param executionId the job execution
     * @param level       minimum level to return (TRACE…ERROR); {@code null}/blank uses the default
     * @param limit       maximum number of most-recent matching records (0 = no cap)
     */
    public List<JobLogEntry> read(long executionId, String level, int limit) {
        // Ensures the execution exists (throws NOT_FOUND otherwise).
        jobAdminService.getExecution(executionId);

        String effective = (level == null || level.isBlank())
                ? properties.getLogs().getDefaultReadLevel()
                : level.trim();
        if (!LogLevels.isValid(effective)) {
            throw BatchAdminException.badRequest(
                    "Unknown log level '" + level + "'. Valid levels: " + LogLevels.ORDERED);
        }
        return store.read(executionId, effective, limit);
    }

    public List<String> levels() {
        return LogLevels.ORDERED;
    }
}
