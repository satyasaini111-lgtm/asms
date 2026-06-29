package com.asms.mt.transport;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Train track segment — only one train permitted at a time per segment.
 * Uses tryLock with timeout to prevent indefinite blocking.
 */
public class TrackSegment {

    private final String segmentId;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile String currentTrain = null;

    public TrackSegment(String segmentId) {
        this.segmentId = segmentId;
    }

    public boolean enterSegment(String trainId) throws InterruptedException {
        boolean acquired = lock.tryLock(10, TimeUnit.SECONDS);
        if (acquired) {
            currentTrain = trainId;
            System.out.printf("Train %s ENTERED segment %s%n", trainId, segmentId);
        } else {
            System.out.printf("Train %s WAITING for segment %s (occupied by %s)%n",
                    trainId, segmentId, currentTrain);
        }
        return acquired;
    }

    public void exitSegment(String trainId) {
        if (lock.isHeldByCurrentThread()) {
            currentTrain = null;
            lock.unlock();
            System.out.printf("Train %s EXITED segment %s%n", trainId, segmentId);
        } else {
            throw new IllegalStateException("Train " + trainId + " does not own segment " + segmentId);
        }
    }

    public boolean isOccupied() { return lock.isLocked(); }
    public String getCurrentTrain() { return currentTrain; }
}
