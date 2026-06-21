package com.asms.mt.amenity;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Swimming pool capacity control using Java Semaphore (fair mode).
 * Max capacity enforced; tryAcquire used to avoid blocking callers indefinitely.
 */
public class SwimmingPool {

    private final int capacity;
    private final Semaphore semaphore;
    private final String poolName;

    public SwimmingPool(String poolName, int capacity) {
        this.poolName = poolName;
        this.capacity = capacity;
        this.semaphore = new Semaphore(capacity, true); // fair = FIFO
    }

    public boolean enter(String userId) throws InterruptedException {
        boolean acquired = semaphore.tryAcquire(5, TimeUnit.SECONDS);
        if (acquired) {
            System.out.printf("[%s] User %s entered. Occupancy: %d/%d%n",
                    poolName, userId, currentOccupancy(), capacity);
        } else {
            System.out.printf("[%s] User %s DENIED — pool full (%d/%d)%n",
                    poolName, userId, currentOccupancy(), capacity);
        }
        return acquired;
    }

    public void exit(String userId) {
        semaphore.release();
        System.out.printf("[%s] User %s exited. Occupancy: %d/%d%n",
                poolName, userId, currentOccupancy(), capacity);
    }

    public int currentOccupancy() {
        return capacity - semaphore.availablePermits();
    }

    public int availableSlots() {
        return semaphore.availablePermits();
    }
}
