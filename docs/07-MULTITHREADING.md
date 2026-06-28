# Multi-threading – Housing Society, Lift & Electricity
## Java 21 Virtual Threads + Classic Concurrency

---

## 1. Housing Society OOP Design

```
Society
 ├── Tower (1..N)
 │    ├── Floor (1..N)
 │    │    ├── Unit (1..N)
 │    │    └── SecurityCamera       [Thread: CameraMonitor]
 │    ├── Lift                      [Thread: LiftController — SCAN algorithm]
 │    └── ElectricityLine           [Thread: PowerMonitor — grid/backup]
 ├── SwimmingPool (1..N)            [Semaphore: capacity control]
 │    └── type: ADULT | KIDS
 ├── ClubRoom                       [ReentrantLock: single-event occupancy]
 └── TransportHub
      ├── BusScheduler              [ScheduledExecutorService]
      └── TrainMetroScheduler       [ScheduledExecutorService]
```

---

## 2. Lift Implementation (SCAN / Elevator Algorithm)

**Rules:**
- Lift does NOT stop in FIFO order of calls
- Stops in floor sequence order (ascending then descending)
- At a time, moves only UP or DOWN
- Failure recovery: reset state, alert central room

```java
public class Lift implements Runnable {

    private final int liftId;
    private volatile int currentFloor = 0;
    private volatile Direction direction = Direction.UP;
    private volatile LiftStatus status = LiftStatus.IDLE;

    // SCAN queues: sorted by floor number
    private final TreeSet<Integer> upQueue   = new TreeSet<>();   // ascending
    private final TreeSet<Integer> downQueue = new TreeSet<>(Comparator.reverseOrder()); // descending
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition hasRequest   = lock.newCondition();
    private final AtomicBoolean running  = new AtomicBoolean(true);

    public void callLift(int floor) {
        lock.lock();
        try {
            if (floor > currentFloor)  upQueue.add(floor);
            else if (floor < currentFloor) downQueue.add(floor);
            else openDoor();  // already on this floor
            hasRequest.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                lock.lock();
                try {
                    while (upQueue.isEmpty() && downQueue.isEmpty()) {
                        status = LiftStatus.IDLE;
                        hasRequest.await(5, TimeUnit.SECONDS);
                    }
                } finally {
                    lock.unlock();
                }

                // SCAN: serve UP queue first, then DOWN
                while (!upQueue.isEmpty()) {
                    int target = upQueue.pollFirst();
                    moveTo(target, Direction.UP);
                }
                direction = Direction.DOWN;

                while (!downQueue.isEmpty()) {
                    int target = downQueue.pollFirst();
                    moveTo(target, Direction.DOWN);
                }
                direction = Direction.UP;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (LiftFailureException e) {
                recoverFromFailure(e);
            }
        }
    }

    private void moveTo(int targetFloor, Direction dir) throws InterruptedException {
        status = LiftStatus.MOVING;
        System.out.printf("Lift %d moving %s: floor %d → %d%n",
            liftId, dir, currentFloor, targetFloor);

        while (currentFloor != targetFloor) {
            Thread.sleep(500);  // simulate floor transit
            currentFloor += (dir == Direction.UP) ? 1 : -1;
            System.out.printf("Lift %d at floor %d%n", liftId, currentFloor);

            if (simulateRandomFailure()) throw new LiftFailureException("Motor fault");
        }
        openDoor();
    }

    private void openDoor() throws InterruptedException {
        status = LiftStatus.DOOR_OPEN;
        System.out.printf("Lift %d: door open at floor %d%n", liftId, currentFloor);
        Thread.sleep(2000);  // passengers board/exit
        status = LiftStatus.DOOR_CLOSED;
    }

    public void recoverFromFailure(LiftFailureException e) {
        System.err.printf("ALERT: Lift %d FAILURE — %s. Initiating recovery.%n", liftId, e.getMessage());
        status = LiftStatus.MAINTENANCE;
        // Reset to ground floor
        currentFloor = 0;
        upQueue.clear();
        downQueue.clear();
        // Alert central monitoring
        CentralMonitor.getInstance().alertLiftFailure(liftId, e.getMessage());
        // Attempt restart after delay
        try {
            Thread.sleep(5000);
            status = LiftStatus.IDLE;
            System.out.printf("Lift %d: recovery complete, resuming service.%n", liftId);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() { running.set(false); }
}

// Lift demo
public class LiftDemo {
    public static void main(String[] args) throws InterruptedException {
        Lift lift = new Lift(1);
        Thread liftThread = Thread.ofVirtual().name("lift-1").start(lift);  // Java 21 virtual thread

        // Calls arrive out of order
        lift.callLift(7);
        lift.callLift(2);
        lift.callLift(9);
        lift.callLift(4);
        Thread.sleep(500);
        lift.callLift(1);
        // Expected order: 2, 4, 7, 9 (UP), then 1 (DOWN)

        Thread.sleep(20_000);
        lift.shutdown();
        liftThread.join();
    }
}
```

---

## 3. Electricity Supply Implementation

```java
public enum PowerSource { GRID, BACKUP, FAILED }

public class ElectricityController implements Runnable {

    private final String towerId;
    private volatile PowerSource currentSource = PowerSource.GRID;
    private final AtomicBoolean gridAvailable = new AtomicBoolean(true);
    private final AtomicBoolean backupAvailable = new AtomicBoolean(true);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public void setGridAvailable(boolean available) {
        gridAvailable.set(available);
    }

    @Override
    public void run() {
        System.out.printf("Tower %s: Power monitor started. Source: %s%n", towerId, currentSource);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                checkAndSwitch();
                Thread.sleep(500);  // poll every 500ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private synchronized void checkAndSwitch() {
        switch (currentSource) {
            case GRID -> {
                if (!gridAvailable.get()) {
                    if (backupAvailable.get()) {
                        switchTo(PowerSource.BACKUP, "Grid failure detected");
                    } else {
                        switchTo(PowerSource.FAILED, "Both grid and backup failed!");
                        CentralMonitor.getInstance().alertPowerFailure(towerId);
                    }
                }
            }
            case BACKUP -> {
                if (gridAvailable.get()) {
                    switchTo(PowerSource.GRID, "Grid restored — switching back");
                } else if (!backupAvailable.get()) {
                    switchTo(PowerSource.FAILED, "Backup exhausted!");
                    CentralMonitor.getInstance().alertPowerFailure(towerId);
                }
            }
            case FAILED -> {
                if (gridAvailable.get()) {
                    switchTo(PowerSource.GRID, "Emergency grid restored");
                } else if (backupAvailable.get()) {
                    switchTo(PowerSource.BACKUP, "Emergency backup activated");
                }
            }
        }
    }

    private void switchTo(PowerSource target, String reason) {
        System.out.printf("Tower %s: %-6s → %-6s | Reason: %s%n",
            towerId, currentSource, target, reason);
        currentSource = target;
    }
}
```

---

## 4. Swimming Pool (Semaphore – Capacity Control)

```java
public class SwimmingPool {

    public enum PoolType { ADULT, KIDS }

    private final String poolId;
    private final PoolType type;
    private final Semaphore capacityGuard;

    public SwimmingPool(String poolId, PoolType type, int capacity) {
        this.poolId = poolId;
        this.type = type;
        this.capacityGuard = new Semaphore(capacity, true);  // fair semaphore
    }

    public void enter(String residentId) throws InterruptedException {
        System.out.printf("%s pool %s: Resident %s waiting...%n", type, poolId, residentId);
        capacityGuard.acquire();
        System.out.printf("%s pool %s: Resident %s entered. Available slots: %d%n",
            type, poolId, residentId, capacityGuard.availablePermits());
    }

    public void exit(String residentId) {
        capacityGuard.release();
        System.out.printf("%s pool %s: Resident %s exited. Available slots: %d%n",
            type, poolId, residentId, capacityGuard.availablePermits());
    }
}
```

---

## 5. Club Room (Mutex – Single Event at a Time)

```java
public class ClubRoom {

    private final ReentrantLock eventLock = new ReentrantLock(true);  // fair lock
    private volatile String currentEvent = null;

    public boolean bookForEvent(String eventName, Duration duration) {
        if (eventLock.tryLock()) {
            try {
                currentEvent = eventName;
                System.out.printf("ClubRoom: '%s' started.%n", eventName);
                Thread.sleep(duration.toMillis());
                System.out.printf("ClubRoom: '%s' ended.%n", eventName);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } finally {
                currentEvent = null;
                eventLock.unlock();
            }
        } else {
            System.out.printf("ClubRoom: Busy with '%s'. Cannot book '%s'.%n", currentEvent, eventName);
            return false;
        }
    }
}
```

---

## 6. Bus Feeder to Train/Metro (ScheduledExecutorService)

```java
public class TransportScheduler {

    private final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(4);

    public void startBusFeeder(String societyId, String stationId, int intervalMinutes) {
        scheduler.scheduleAtFixedRate(
            () -> dispatchBus(societyId, stationId),
            0, intervalMinutes, TimeUnit.MINUTES
        );
        System.out.printf("Bus feeder started: %s → %s every %d min%n",
            societyId, stationId, intervalMinutes);
    }

    public void startMetroFeeder(String societyId, int intervalMinutes) {
        scheduler.scheduleAtFixedRate(
            () -> dispatchMetroFeeder(societyId),
            0, intervalMinutes, TimeUnit.MINUTES
        );
    }

    private void dispatchBus(String from, String to) {
        System.out.printf("[%s] Bus dispatched: %s → %s%n",
            LocalTime.now(), from, to);
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
```

---

## 7. Security Camera Monitor (Virtual Threads – Java 21)

```java
public class SecurityCameraMonitor {

    // Java 21: virtual thread per camera — no thread pool size limit needed
    public void startMonitoringAll(List<SecurityCamera> cameras) {
        cameras.forEach(camera ->
            Thread.ofVirtual()
                .name("camera-monitor-" + camera.getId())
                .start(() -> monitorCamera(camera))
        );
    }

    private void monitorCamera(SecurityCamera camera) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                CameraFrame frame = camera.captureFrame();
                if (motionDetected(frame)) {
                    alertSecurity(camera.getFloorId(), frame);
                }
                Thread.sleep(100);  // 10 FPS
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

---

## 8. Track/Road – Single-Direction Mutex

```java
// Only ONE train can travel in a single-track segment at a time
public class TrackSegment {

    private final String segmentId;
    private final ReentrantLock trackLock = new ReentrantLock();
    private volatile String currentTrainId = null;

    public boolean requestEntry(String trainId) throws InterruptedException {
        if (trackLock.tryLock(10, TimeUnit.SECONDS)) {
            currentTrainId = trainId;
            System.out.printf("Track %s: Train %s ENTERED.%n", segmentId, trainId);
            return true;
        }
        System.out.printf("Track %s: Train %s BLOCKED — occupied by %s.%n",
            segmentId, trainId, currentTrainId);
        return false;
    }

    public void exit(String trainId) {
        if (trainId.equals(currentTrainId)) {
            currentTrainId = null;
            trackLock.unlock();
            System.out.printf("Track %s: Train %s EXITED.%n", segmentId, trainId);
        }
    }
}
```

---

## 9. JVM Configuration (Java 21 Production Flags)

```bash
# Startup script / K8s container args
JAVA_OPTS="\
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:GCTimeRatio=4 \
  -Xss128k \
  -Xlog:gc*:file=/logs/gc.log:time,uptime:filecount=5,filesize=20m \
  -Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.port=9090 \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.ssl=false"

# Optional: ZGC for ultra-low pause (< 1ms)
# -XX:+UseZGC

# Optional: GraalVM native image for 50ms startup
# mvn -Pnative native:compile
```

**Spring Boot virtual threads** (`application.yml`):
```yaml
spring:
  threads:
    virtual:
      enabled: true   # Tomcat uses virtual threads for request handling
```

**JConsole monitoring:** `jconsole <pid>` — observe heap, thread count, GC activity  
**JProfiler:** Attach to JVM on port 9090 — bytecode-level profiling, allocation hotspots

---

## 10. Central Monitoring (Singleton)

```java
public class CentralMonitor {
    private static final CentralMonitor INSTANCE = new CentralMonitor();
    private final List<AlertListener> listeners = new CopyOnWriteArrayList<>();

    private CentralMonitor() {}
    public static CentralMonitor getInstance() { return INSTANCE; }

    public void addListener(AlertListener listener) { listeners.add(listener); }

    public void alertLiftFailure(int liftId, String reason) {
        Alert alert = new Alert(AlertType.LIFT_FAILURE, "Lift " + liftId + ": " + reason);
        listeners.forEach(l -> l.onAlert(alert));
    }

    public void alertPowerFailure(String towerId) {
        Alert alert = new Alert(AlertType.POWER_FAILURE, "Tower " + towerId + ": power failed");
        listeners.forEach(l -> l.onAlert(alert));
    }
}
```
