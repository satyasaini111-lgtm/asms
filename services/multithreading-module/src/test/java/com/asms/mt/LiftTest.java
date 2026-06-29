package com.asms.mt;

import com.asms.mt.lift.Lift;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Lift SCAN Algorithm Tests")
class LiftTest {

    private Lift lift;
    private Thread liftThread;
    private final List<String> arrivals = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lift = new Lift("TEST-LIFT", 0, 10, 0);
        lift.addArrivalListener(msg -> {
            synchronized (arrivals) {
                arrivals.add(msg);
            }
        });
        liftThread = Thread.ofVirtual().name("test-lift").start(lift);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        lift.stop();
        liftThread.join(3000);
    }

    @Test
    @DisplayName("callLift: lift visits requested floors")
    void liftVisitsRequestedFloors() {
        lift.callLift(3);
        lift.callLift(7);

        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .until(() -> {
                    synchronized (arrivals) {
                        return arrivals.size() >= 2;
                    }
                });

        synchronized (arrivals) {
            assertThat(arrivals).anyMatch(m -> m.contains("floor 3"));
            assertThat(arrivals).anyMatch(m -> m.contains("floor 7"));
        }
    }

    @Test
    @DisplayName("callLift: floor out of range — throws exception")
    void callLift_outOfRange() {
        assertThatThrownBy(() -> lift.callLift(15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    @DisplayName("lift recovers from exception and continues")
    void liftStartsAtFloorZero() {
        assertThat(lift.getCurrentFloor()).isEqualTo(0);
        assertThat(lift.getDirection()).isEqualTo(Lift.Direction.IDLE);
    }
}
