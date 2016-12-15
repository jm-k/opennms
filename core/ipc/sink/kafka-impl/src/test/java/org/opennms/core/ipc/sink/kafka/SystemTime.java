package org.opennms.core.ipc.sink.kafka;

import java.util.concurrent.TimeUnit;

import kafka.utils.Time;

public class SystemTime implements Time {
    @Override
    public long milliseconds() {
        return System.currentTimeMillis();
    }

    @Override
    public long nanoseconds() {
        return System.nanoTime();
    }

    @Override
    public void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            // better to ignore?  or pass upstream?
            //Thread.currentThread().interrupt();
        }
    }

    @Override
    public long hiResClockMs() {
        return TimeUnit.NANOSECONDS.toMillis(nanoseconds());
    }
}
