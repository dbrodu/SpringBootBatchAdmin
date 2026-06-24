package io.batchadmin.logs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded, in-memory store of captured log events keyed by job execution id.
 *
 * <p>Each execution keeps at most {@code maxRecordsPerExecution} of its most recent events, and at
 * most {@code maxExecutions} executions are retained (the oldest are evicted). The store is
 * thread-safe and free of any logging-framework dependency.</p>
 */
public class BatchAdminLogStore {

    private final int maxRecordsPerExecution;
    private final Map<Long, Deque<JobLogEntry>> byExecution;

    public BatchAdminLogStore(int maxRecordsPerExecution, int maxExecutions) {
        this.maxRecordsPerExecution = Math.max(1, maxRecordsPerExecution);
        int cap = Math.max(1, maxExecutions);
        this.byExecution = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Deque<JobLogEntry>> eldest) {
                return size() > cap;
            }
        });
    }

    /** Appends an event for the given execution, evicting the oldest record(s) if over capacity. */
    public void append(long executionId, JobLogEntry entry) {
        synchronized (byExecution) {
            Deque<JobLogEntry> records = byExecution.computeIfAbsent(executionId, k -> new ArrayDeque<>());
            records.addLast(entry);
            while (records.size() > maxRecordsPerExecution) {
                records.removeFirst();
            }
        }
    }

    /**
     * Returns the captured events for an execution, oldest first, keeping only those at or above
     * {@code minLevel} and capping to the {@code limit} most recent matches.
     */
    public List<JobLogEntry> read(long executionId, String minLevel, int limit) {
        int minRank = minLevel == null ? Integer.MIN_VALUE : LogLevels.rank(minLevel);
        List<JobLogEntry> snapshot;
        synchronized (byExecution) {
            Deque<JobLogEntry> records = byExecution.get(executionId);
            snapshot = records == null ? List.of() : new ArrayList<>(records);
        }
        List<JobLogEntry> filtered = new ArrayList<>();
        for (JobLogEntry entry : snapshot) {
            if (LogLevels.rank(entry.level()) >= minRank) {
                filtered.add(entry);
            }
        }
        if (limit > 0 && filtered.size() > limit) {
            return new ArrayList<>(filtered.subList(filtered.size() - limit, filtered.size()));
        }
        return filtered;
    }

    /** Whether any log has been captured for the given execution. */
    public boolean hasLogs(long executionId) {
        synchronized (byExecution) {
            Deque<JobLogEntry> records = byExecution.get(executionId);
            return records != null && !records.isEmpty();
        }
    }

    public void clear(long executionId) {
        byExecution.remove(executionId);
    }
}
