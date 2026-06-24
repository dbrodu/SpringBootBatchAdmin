package io.batchadmin.logs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchAdminLogStoreTest {

    private JobLogEntry entry(String level, String message) {
        return new JobLogEntry(Instant.now(), level, "io.test.Logger", "main", message);
    }

    @Test
    void filtersByMinimumLevel() {
        BatchAdminLogStore store = new BatchAdminLogStore(100, 10);
        store.append(1L, entry("DEBUG", "d"));
        store.append(1L, entry("INFO", "i"));
        store.append(1L, entry("WARN", "w"));
        store.append(1L, entry("ERROR", "e"));

        assertThat(store.read(1L, "DEBUG", 0)).extracting(JobLogEntry::message)
                .containsExactly("d", "i", "w", "e");
        assertThat(store.read(1L, "INFO", 0)).extracting(JobLogEntry::message)
                .containsExactly("i", "w", "e");
        assertThat(store.read(1L, "WARN", 0)).extracting(JobLogEntry::message)
                .containsExactly("w", "e");
        assertThat(store.read(1L, "ERROR", 0)).extracting(JobLogEntry::message)
                .containsExactly("e");
    }

    @Test
    void keepsOnlyMostRecentRecordsPerExecution() {
        BatchAdminLogStore store = new BatchAdminLogStore(3, 10);
        for (int i = 1; i <= 5; i++) {
            store.append(7L, entry("INFO", "m" + i));
        }
        assertThat(store.read(7L, "INFO", 0)).extracting(JobLogEntry::message)
                .containsExactly("m3", "m4", "m5");
    }

    @Test
    void limitReturnsMostRecentMatches() {
        BatchAdminLogStore store = new BatchAdminLogStore(100, 10);
        for (int i = 1; i <= 5; i++) {
            store.append(2L, entry("INFO", "m" + i));
        }
        assertThat(store.read(2L, "INFO", 2)).extracting(JobLogEntry::message)
                .containsExactly("m4", "m5");
    }

    @Test
    void evictsOldestExecutionsBeyondCapacity() {
        BatchAdminLogStore store = new BatchAdminLogStore(100, 2);
        store.append(1L, entry("INFO", "a"));
        store.append(2L, entry("INFO", "b"));
        store.append(3L, entry("INFO", "c")); // evicts execution 1

        assertThat(store.hasLogs(1L)).isFalse();
        assertThat(store.hasLogs(2L)).isTrue();
        assertThat(store.hasLogs(3L)).isTrue();
    }

    @Test
    void unknownExecutionReturnsEmpty() {
        BatchAdminLogStore store = new BatchAdminLogStore(100, 10);
        assertThat(store.read(99L, "INFO", 0)).isEmpty();
        assertThat(store.hasLogs(99L)).isFalse();
    }
}
