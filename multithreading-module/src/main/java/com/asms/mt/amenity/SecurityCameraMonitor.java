package com.asms.mt.amenity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One Java 21 virtual thread per security camera.
 * Demonstrates Project Loom advantage: 50 cameras = 50 virtual threads (not 50 OS threads).
 */
public class SecurityCameraMonitor {

    private final List<Thread> cameraThreads = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public void startMonitoring(List<String> cameraIds) {
        running.set(true);
        for (String cameraId : cameraIds) {
            Thread t = Thread.ofVirtual()
                    .name("camera-" + cameraId)
                    .start(() -> monitorCamera(cameraId));
            cameraThreads.add(t);
        }
        System.out.println("Started " + cameraIds.size() + " virtual camera threads.");
    }

    private void monitorCamera(String cameraId) {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Simulate frame analysis every 100ms
                Thread.sleep(100);
                // In real system: call ML model / motion detection
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void stopAll() {
        running.set(false);
        cameraThreads.forEach(Thread::interrupt);
        System.out.println("All camera threads stopped.");
    }

    public int activeCount() {
        return (int) cameraThreads.stream().filter(Thread::isAlive).count();
    }
}
