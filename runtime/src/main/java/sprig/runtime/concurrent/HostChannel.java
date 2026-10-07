package sprig.runtime.concurrent;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import sprig.runtime.SprigError;

/**
 * A bounded queue between threads that can be closed. Senders block while the
 * channel is full; receivers block while it is empty and open. After close,
 * the remaining values are still received, then {@code receive} returns null.
 */
public final class HostChannel<T> {
    private static final Object CLOSED = new Object();
    private final LinkedBlockingQueue<Object> queue;
    private final ReentrantLock sendLock = new ReentrantLock();
    /** Free value slots: senders take one, receivers give it back. */
    private final java.util.concurrent.Semaphore permits;
    private volatile boolean closed;

    public HostChannel(long capacity) {
        if (capacity < 1 || capacity > 10_000_000) throw new SprigError("channel capacity must be 1..10000000, got " + capacity);
        // One slot beyond the capacity is reserved for the close marker, so
        // close() never waits for a receiver; the permits keep senders to the
        // declared capacity.
        this.queue = new LinkedBlockingQueue<>((int) capacity + 1);
        this.permits = new java.util.concurrent.Semaphore((int) capacity);
    }

    /** Blocks while the channel is full. Sending to a closed channel is an Error. */
    public void send(T value) {
        if (value == null) throw new SprigError("a channel carries values, not null");
        if (closed) throw new SprigError("channel is closed");
        try {
            // Wait for a slot outside the lock, so a close() is never queued
            // behind a sender that waits for a receiver.
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SprigError("sending on the channel was interrupted", e);
        }
        sendLock.lock();
        try {
            if (closed) {
                permits.release();
                throw new SprigError("channel is closed");
            }
            if (!queue.offer(value)) throw new IllegalStateException("channel slot accounting failed");
        } finally {
            sendLock.unlock();
        }
    }

    /** Offers without blocking: false when the channel is full or closed. */
    public boolean trySend(T value) {
        if (value == null) throw new SprigError("a channel carries values, not null");
        if (closed || !permits.tryAcquire()) return false;
        sendLock.lock();
        try {
            if (closed) {
                permits.release();
                return false;
            }
            if (!queue.offer(value)) throw new IllegalStateException("channel slot accounting failed");
            return true;
        } finally {
            sendLock.unlock();
        }
    }

    /** Blocks until a value arrives; null once the channel is closed and drained. */
    @SuppressWarnings("unchecked")
    public T receive() {
        try {
            Object item = queue.take();
            if (item == CLOSED) {
                queue.offer(CLOSED); // keep the marker for the other receivers
                return null;
            }
            permits.release();
            return (T) item;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SprigError("receiving from the channel was interrupted", e);
        }
    }

    /** Waits at most the given milliseconds; null when nothing arrived or the channel is closed and drained. */
    @SuppressWarnings("unchecked")
    public T receiveWithin(long millis) {
        if (millis < 0) throw new SprigError("timeout millis must not be negative: " + millis);
        try {
            Object item = queue.poll(millis, TimeUnit.MILLISECONDS);
            if (item == CLOSED) {
                queue.offer(CLOSED);
                return null;
            }
            if (item != null) permits.release();
            return (T) item;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SprigError("receiving from the channel was interrupted", e);
        }
    }

    /** No more values can be sent; receivers drain what is left and then see null. Idempotent. */
    public void close() {
        sendLock.lock();
        try {
            if (closed) return;
            closed = true;
            // The marker has its own reserved slot, so a full channel closes at once.
            if (!queue.offer(CLOSED)) throw new IllegalStateException("channel slot accounting failed");
            // Senders waiting for a slot wake up and see the channel closed.
            permits.release(Integer.MAX_VALUE / 2);
        } finally {
            sendLock.unlock();
        }
    }

    public boolean isClosed() {
        return closed;
    }

    /** Values waiting to be received. */
    public long size() {
        int size = queue.size();
        return closed && queue.peek() == CLOSED ? 0 : closed && size > 0 ? size - 1 : size;
    }
}
