package sprig.runtime.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import sprig.runtime.Fn0;
import sprig.runtime.NonNull;
import sprig.runtime.SprigError;

/** A fixed number of daemon worker threads that run tasks in submission order. */
public final class HostPool {
    private final ExecutorService executor;
    private final long threads;

    public HostPool(long threads) {
        if (threads < 1 || threads > 10_000) throw new SprigError("pool threads must be 1..10000, got " + threads);
        this.threads = threads;
        this.executor = Executors.newFixedThreadPool((int) threads, HostTask.factory());
    }

    @NonNull
    public <T> HostTask<T> submit(Fn0<T> work) {
        return HostTask.startOn(executor, work);
    }

    public long threads() {
        return threads;
    }

    /** Stops accepting tasks; running and queued tasks still finish. */
    public void shutdown() {
        executor.shutdown();
    }

    /** Stops accepting tasks and interrupts the running ones. */
    public void shutdownNow() {
        executor.shutdownNow();
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    /** After shutdown: waits until every task finished, or the time ran out. */
    public boolean awaitTermination(long millis) {
        if (millis < 0) throw new SprigError("timeout millis must not be negative: " + millis);
        try {
            return executor.awaitTermination(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SprigError("waiting for the pool was interrupted", e);
        }
    }
}
