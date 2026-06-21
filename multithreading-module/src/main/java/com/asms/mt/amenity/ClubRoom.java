
package com.asms.mt.amenity;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Club room — single event at a time. tryLock immediately returns false
 * if another event is in progress. Avoids blocking/deadlock.
 */
public class ClubRoom {

    private final String roomName;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile String currentEventOwner = null;

    public ClubRoom(String roomName) {
        this.roomName = roomName;
    }

    public boolean bookForEvent(String organizer, String eventName) {
        if (lock.tryLock()) {
            currentEventOwner = organizer;
            System.out.printf("[%s] BOOKED by %s for event: %s%n", roomName, organizer, eventName);
            return true;
        } else {
            System.out.printf("[%s] BOOKING DENIED for %s — room in use by %s%n",
                    roomName, organizer, currentEventOwner);
            return false;
        }
    }

    public void releaseAfterEvent(String organizer) {
        if (lock.isHeldByCurrentThread()) {
            currentEventOwner = null;
            lock.unlock();
            System.out.printf("[%s] Released by %s%n", roomName, organizer);
        } else {
            throw new IllegalStateException(organizer + " does not hold the club room lock");
        }
    }

    public boolean isAvailable() {
        return !lock.isLocked();
    }

    public String getCurrentEventOwner() {
        return currentEventOwner;
    }
}
