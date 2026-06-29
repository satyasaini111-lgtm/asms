package com.asms.mt.transport;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Bus/Train schedule management using ScheduledExecutorService.
 * Supports fixed-rate dispatch and one-time departures.
 */
public class TransportScheduler {

    private final ScheduledExecutorService scheduler;
    private final String societyId;

    public TransportScheduler(String societyId, int poolSize) {
        this.societyId = societyId;
        this.scheduler = Executors.newScheduledThreadPool(poolSize,
                Thread.ofVirtual().name("transport-", 0).factory());
    }

    public ScheduledFuture<?> scheduleBus(String routeId, long initialDelaySeconds, long periodSeconds) {
        return scheduler.scheduleAtFixedRate(
                () -> System.out.printf("[%s] Bus %s departed at %s%n",
                        societyId, routeId, java.time.LocalTime.now()),
                initialDelaySeconds, periodSeconds, TimeUnit.SECONDS
        );
    }

    public ScheduledFuture<?> scheduleOneTimePickup(String vehicleId, long delaySeconds) {
        return scheduler.schedule(
                () -> System.out.printf("[%s] Vehicle %s pickup at %s%n",
                        societyId, vehicleId, java.time.LocalTime.now()),
                delaySeconds, TimeUnit.SECONDS
        );
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
