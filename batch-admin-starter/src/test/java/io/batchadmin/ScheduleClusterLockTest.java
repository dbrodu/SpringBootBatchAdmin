package io.batchadmin;

import static org.assertj.core.api.Assertions.assertThat;

import io.batchadmin.domain.ScheduleLockDao;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The cluster-safe scheduling lock: two {@link ScheduleLockDao}s on the same database stand in for two
 * application instances. Each {@code (scheduleId, fireSecond)} can be claimed exactly once, so only one
 * instance would launch a given scheduled fire.
 */
@SpringBootTest(classes = TestBatchApplication.class)
class ScheduleClusterLockTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void onlyOneInstanceClaimsAGivenFire() {
        ScheduleLockDao instanceA = new ScheduleLockDao(dataSource);
        ScheduleLockDao instanceB = new ScheduleLockDao(dataSource);

        assertThat(instanceA.tryClaim(1, 1000, "A")).isTrue();    // A wins the fire
        assertThat(instanceB.tryClaim(1, 1000, "B")).isFalse();   // B loses the same fire
        assertThat(instanceA.tryClaim(1, 1000, "A")).isFalse();   // even a re-attempt loses
        assertThat(instanceB.tryClaim(1, 1001, "B")).isTrue();    // the next fire is claimable
        assertThat(instanceA.tryClaim(2, 1000, "A")).isTrue();    // a different schedule is independent
    }

    @Test
    void concurrentClaimsYieldExactlyOneWinner() throws Exception {
        int contenders = 8;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            ScheduleLockDao dao = new ScheduleLockDao(dataSource);
            String id = "instance-" + i;
            results.add(pool.submit(() -> {
                start.await();
                return dao.tryClaim(42, 5000, id);
            }));
        }
        start.countDown();
        long winners = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                winners++;
            }
        }
        pool.shutdownNow();
        assertThat(winners).isEqualTo(1);
    }

    @Test
    void purgeRemovesClaims() {
        ScheduleLockDao dao = new ScheduleLockDao(dataSource);
        dao.tryClaim(7, 100, "x");
        // A cutoff in the future removes every existing claim row.
        assertThat(dao.purgeOlderThan(Instant.now().plusSeconds(60))).isGreaterThanOrEqualTo(1);
    }
}
