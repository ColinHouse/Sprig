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
    private volatile boolean closed;

    public HostChannel(long capacity) {
        if (capacity < 1 || capacity > 10_000_000) throw new SprigError("channel capacity must be 1..10000000, got " + capacity);
        this.queue = new LinkedBlockingQueue<>((int) capacity);
    }

    /** Blocks while the channel is full. Sending to a closed channel is an Error. */
    public void send(T value) {
        if (value == null) throw new SprigError("a channel carries values, not null");
        sendLock.lock();
        try {
            if (closed) throw new SprigError("channel is closed");
            queue.put(value);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SprigError("sending on the channel was interrupted", e);
        } finally {
            sendLock.unlock();
        }
    }

    /** Offers without blocking: false when the channel is full or closed. */
    public boolean trySend(T value) {
        if (value == null) throw new SprigError("a channel carries values, not null");
        sendLock.lock();
        try {
            return !closed && queue.offer(value);
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
            // The marker takes the last slot; a full channel waits for a receiver.
            queue.put(CLOSED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SprigError("closing the channel was interrupted", e);
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
