package sprig.runtime.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import sprig.runtime.SprigError;

/** A count that threads lower; waiters continue when it reaches zero. */
public final class HostLatch {
    private final CountDownLatch latch;

    public HostLatch(long count) {
        if (count < 0) throw new SprigError("latch count must not be negative: " + count);
        this.latch = new CountDownLatch((int) Math.min(count, Integer.MAX_VALUE));
    }

    public void countDown() {
        latch.countDown();
    }

    public long remaining() {
        return latch.getCount();
    }

    public void await() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SprigError("waiting for the latch was interrupted", e);
        }
    }

    /** True when the count reached zero within the time. */
    public boolean awaitWithin(long millis) {
        if (millis < 0) throw new SprigError("timeout millis must not be negative: " + millis);
        try {
            return latch.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SprigError("waiting for the latch was interrupted", e);
        }
    }
}
