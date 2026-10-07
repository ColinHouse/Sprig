package sprig.runtime.concurrent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import sprig.runtime.Fn0;
import sprig.runtime.NonNull;
import sprig.runtime.SprigError;

/** A fixed number of daemon worker threads that run tasks in submission order. */
public final class HostPool {
    private final ExecutorService executor;
    private final long threads;
    /** Every task the pool has accepted and not yet run to its end, so shutdownNow() can cancel it. */
    private final Set<HostTask<?>> live = ConcurrentHashMap.newKeySet();

    public HostPool(long threads) {
        if (threads < 1 || threads > 10_000) throw new SprigError("pool threads must be 1..10000, got " + threads);
        this.threads = threads;
        this.executor = Executors.newFixedThreadPool((int) threads, HostTask.factory());
    }

    @NonNull
    public <T> HostTask<T> submit(Fn0<T> work) {
        HostTask<T> task = HostTask.prepare(work, null);
        start(task);
        return task;
    }

    /** Queues a prepared task; the pool keeps it in {@code live} until its run has ended. */
    void start(HostTask<?> task) {
        live.add(task);
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    live.remove(task);
                }
            });
        } catch (RejectedExecutionException e) {
            live.remove(task);
            throw new SprigError("pool is shut down; it accepts no more tasks", e);
        }
    }

    public long threads() {
        return threads;
    }

    /** Stops accepting tasks; running and queued tasks still finish. */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Stops accepting tasks and cancels every task the pool still has, as
     * HostTask.cancel() does: a running task's thread is interrupted, and a
     * queued task never runs, so its scope does not wait for it. A cancelled
     * task is not a failure of its scope.
     */
    public void shutdownNow() {
        executor.shutdown(); // from here on no task can join live
        for (HostTask<?> task : live) {
            task.cancel(); // cancelled before its thread sees the interrupt
        }
        executor.shutdownNow(); // drops the queue, which holds only cancelled tasks now
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
