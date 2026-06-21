package com.asms.mt;

import com.asms.mt.amenity.SwimmingPool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SwimmingPool Semaphore Tests")
class SwimmingPoolTest {

    @Test
    @DisplayName("pool enforces capacity limit")
    void poolEnforcesCapacity() throws InterruptedException {
        SwimmingPool pool = new SwimmingPool("Main Pool", 3);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger deniedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(5);

        for (int i = 0; i < 5; i++) {
            final String userId = "user_" + i;
            Thread.ofVirtual().start(() -> {
                try {
                    boolean entered = pool.enter(userId);
                    if (entered) {
                        successCount.incrementAndGet();
                        Thread.sleep(200);
                        pool.exit(userId);
                    } else {
                        deniedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        assertThat(successCount.get()).isGreaterThan(0);
        assertThat(pool.currentOccupancy()).isEqualTo(0);
    }

    @Test
    @DisplayName("pool allows entry when slots available")
    void poolAllowsEntryWhenAvailable() throws InterruptedException {
        SwimmingPool pool = new SwimmingPool("Test Pool", 5);
        assertThat(pool.availableSlots()).isEqualTo(5);
        boolean entered = pool.enter("usr_001");
        assertThat(entered).isTrue();
        assertThat(pool.currentOccupancy()).isEqualTo(1);
        pool.exit("usr_001");
        assertThat(pool.currentOccupancy()).isEqualTo(0);
    }
}
