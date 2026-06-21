package com.asms.mt.lift;

import java.util.concurrent.TimeUnit;

public class LiftDemo {

    public static void main(String[] args) throws InterruptedException {
        Lift lift1 = new Lift("LIFT-A", 0, 20, 0);
        Lift lift2 = new Lift("LIFT-B", 0, 20, 10);

        lift1.addArrivalListener(System.out::println);
        lift2.addArrivalListener(System.out::println);

        // Java 21 virtual threads — lightweight, one per lift
        Thread t1 = Thread.ofVirtual().name("lift-A").start(lift1);
        Thread t2 = Thread.ofVirtual().name("lift-B").start(lift2);

        // Simulate concurrent floor requests
        lift1.callLift(5);
        lift1.callLift(12);
        lift1.callLift(3);
        lift2.callLift(18);
        lift2.callLift(7);

        TimeUnit.SECONDS.sleep(20);

        lift1.stop();
        lift2.stop();
        t1.join();
        t2.join();
        System.out.println("Both lifts stopped.");
    }
}
