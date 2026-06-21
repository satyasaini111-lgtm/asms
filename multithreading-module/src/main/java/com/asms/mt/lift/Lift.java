package com.asms.mt.lift;

import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * SCAN (elevator) algorithm: sweep up through upQueue, then down through downQueue.
 * Uses Java 21 virtual threads — one virtual thread per lift instance.
 */
public class Lift implements Runnable {

    public enum Direction { UP, DOWN, IDLE }

    private final String liftId;
    private final int minFloor;
    private final int maxFloor;

    private volatile int currentFloor;
    private volatile Direction direction = Direction.IDLE;

    private final ReentrantLock lock = new ReentrantLock();
    private final TreeSet<Integer> upQueue = new TreeSet<>();
    private final TreeSet<Integer> downQueue = new TreeSet<>(java.util.Comparator.reverseOrder());

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final CopyOnWriteArrayList<Consumer<String>> arrivalListeners = new CopyOnWriteArrayList<>();

    private static final long FLOOR_TRANSIT_MS = 500;
    private static final long DOOR_OPEN_MS = 1000;

    public Lift(String liftId, int minFloor, int maxFloor, int startFloor) {
        this.liftId = liftId;
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.currentFloor = startFloor;
    }

    public void callLift(int floor) {
        if (floor < minFloor || floor > maxFloor) {
            throw new IllegalArgumentException("Floor " + floor + " out of range");
        }
        lock.lock();
        try {
            if (floor >= currentFloor) {
                upQueue.add(floor);
            } else {
                downQueue.add(floor);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                Integer nextFloor = getNextFloor();
                if (nextFloor != null) {
                    moveTo(nextFloor);
                    openDoor();
                } else {
                    direction = Direction.IDLE;
                    Thread.sleep(200);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                recoverFromFailure();
            }
        }
    }

    private Integer getNextFloor() {
        lock.lock();
        try {
            if (direction == Direction.UP || direction == Direction.IDLE) {
                if (!upQueue.isEmpty()) {
                    direction = Direction.UP;
                    return upQueue.first();
                }
                if (!downQueue.isEmpty()) {
                    direction = Direction.DOWN;
                    return downQueue.first();
                }
            } else {
                if (!downQueue.isEmpty()) {
                    direction = Direction.DOWN;
                    return downQueue.first();
                }
                if (!upQueue.isEmpty()) {
                    direction = Direction.UP;
                    return upQueue.first();
                }
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    private void moveTo(int targetFloor) throws InterruptedException {
        while (currentFloor != targetFloor) {
            Thread.sleep(FLOOR_TRANSIT_MS);
            currentFloor += (targetFloor > currentFloor) ? 1 : -1;
        }
        lock.lock();
        try {
            upQueue.remove(targetFloor);
            downQueue.remove(targetFloor);
        } finally {
            lock.unlock();
        }
    }

    private void openDoor() throws InterruptedException {
        String msg = liftId + " doors open at floor " + currentFloor;
        arrivalListeners.forEach(l -> l.accept(msg));
        Thread.sleep(DOOR_OPEN_MS);
    }

    private void recoverFromFailure() {
        try {
            direction = Direction.IDLE;
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void stop() { running.set(false); }
    public void addArrivalListener(Consumer<String> listener) { arrivalListeners.add(listener); }
    public int getCurrentFloor() { return currentFloor; }
    public Direction getDirection() { return direction; }
    public String getLiftId() { return liftId; }
}
