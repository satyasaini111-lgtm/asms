package com.asms.mt.electricity;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Power failover state machine: GRID → BACKUP → FAILED.
 * Polling interval 500ms as specified in design doc.
 */
public class ElectricityController {

    public enum PowerSource { GRID, BACKUP, FAILED }

    private volatile PowerSource currentSource = PowerSource.GRID;
    private final AtomicBoolean gridAvailable = new AtomicBoolean(true);
    private final AtomicBoolean backupAvailable = new AtomicBoolean(true);

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    Thread.ofVirtual().name("power-monitor-", 0).factory());

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkAndSwitch, 0, 500, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdown();
    }

    private synchronized void checkAndSwitch() {
        PowerSource previous = currentSource;
        currentSource = switch (currentSource) {
            case GRID -> gridAvailable.get() ? PowerSource.GRID : PowerSource.BACKUP;
            case BACKUP -> backupAvailable.get() ? PowerSource.BACKUP : PowerSource.FAILED;
            case FAILED -> PowerSource.FAILED;
        };
        if (currentSource != previous) {
            System.out.printf("Power source changed: %s → %s%n", previous, currentSource);
        }
    }

    public void simulateGridFailure() {
        System.out.println("Grid power FAILED!");
        gridAvailable.set(false);
    }

    public void simulateGridRestored() {
        gridAvailable.set(true);
        currentSource = PowerSource.GRID;
        System.out.println("Grid power RESTORED.");
    }

    public void simulateBackupFailure() {
        System.out.println("Backup power FAILED!");
        backupAvailable.set(false);
    }

    public PowerSource getCurrentSource() { return currentSource; }
}
