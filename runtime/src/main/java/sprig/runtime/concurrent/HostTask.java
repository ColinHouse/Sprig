package sprig.runtime.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import sprig.runtime.Fn0;
import sprig.runtime.NonNull;
import sprig.runtime.SprigError;

/**
 * A computation running on another thread, with a typed result. Sprig owns the
 * API ({@code @std/concurrent.spr}); this class owns the JDK future mechanics.
 * Every task belongs to a {@link HostScope}, which starts it on a virtual
 * thread (or on a {@link HostPool}) and waits for it before the scope ends.
 */
public final class HostTask<T> {
    private static final AtomicLong COUNTER = new AtomicLong();
    private static final ThreadFactory FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "sprig-pool-" + COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    };

    private final FutureTask<T> future;

    private HostTask(FutureTask<T> future) {
        this.future = future;
    }

    static ThreadFactory factory() {
        return FACTORY;
    }

    /**
     * A task that has not started: {@link #startOn} or a pool starts it, so
     * its owner can keep the task before its body can run. {@code whenComplete}
     * runs once the task is complete in any way, including a cancellation
     * before its body ever ran (a cancelled FutureTask never calls its body, so
     * a body's own finally cannot report that).
     */
    static <T> HostTask<T> prepare(Fn0<T> work, Runnable whenComplete) {
        if (work == null) throw new SprigError("task work must not be null");
        FutureTask<T> future = new FutureTask<>(work::apply) {
            @Override
            protected void done() {
                if (whenComplete != null) whenComplete.run();
            }
        };
        return new HostTask<>(future);
    }

    /**
     * Starts a prepared task. Executors.newVirtualThreadPerTaskExecutor runs
     * the FutureTask given to execute(), so completion and cancellation are
     * this task's own.
     */
    void startOn(Executor executor) {
        try {
            executor.execute(future);
        } catch (RejectedExecutionException e) {
            throw new SprigError("pool is shut down; it accepts no more tasks", e);
        }
    }

    /** Runs a prepared task on the calling thread; a pool's worker calls this. */
    void run() {
        future.run();
    }

    /** Waits for the result. A failure inside the task is rethrown here as an Error. */
    @NonNull
    public T join() {
        try {
            return unwrap(future.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SprigError("waiting for the task was interrupted", e);
        } catch (ExecutionException e) {
            throw failure(e.getCause());
        } catch (CancellationException e) {
            throw new SprigError("task was cancelled", e);
        }
    }

    /** Waits at most the given milliseconds; a timeout is an Error, the task keeps running. */
    @NonNull
    public T joinWithin(long millis) {
        if (millis < 0) throw new SprigError("timeout millis must not be negative: " + millis);
        try {
            return unwrap(future.get(millis, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SprigError("waiting for the task was interrupted", e);
        } catch (ExecutionException e) {
            throw failure(e.getCause());
        } catch (TimeoutException e) {
            throw new SprigError("task did not finish within " + millis + " ms", e);
        } catch (CancellationException e) {
            throw new SprigError("task was cancelled", e);
        }
    }

    private T unwrap(T value) {
        if (value == null) throw new SprigError("task produced null");
        return value;
    }

    static SprigError failure(Throwable cause) {
        if (cause instanceof SprigError error) return error;
        return new SprigError("task failed: " + cause, cause);
    }

    /**
     * True once the task completed or was cancelled. As with any
     * java.util.concurrent Future, a cancelled task is done at once, even while
     * its body still runs; its scope waits for the body all the same.
     */
    public boolean isDone() {
        return future.isDone();
    }

    /**
     * Pauses the current thread. Inside a task, an interruption (the task was
     * cancelled) ends the task with an Error instead of being swallowed, so
     * cancel() stops a sleeping task; await() on it reports the cancellation.
     */
    public static void sleep(long millis) {
        if (millis < 0) throw new SprigError("sleep millis must not be negative: " + millis);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SprigError("sleep was interrupted", e);
        }
    }

    /** The result, or the fallback when the task failed, was cancelled or produced nothing. */
    @NonNull
    public T joinOr(T fallback) {
        if (fallback == null) throw new SprigError("fallback must not be null");
        try {
            T value = future.get();
            return value == null ? fallback : value;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (ExecutionException | CancellationException e) {
            return fallback;
        }
    }

    /** Asks the task to stop; true when it had not finished yet. Its thread is interrupted. */
    public boolean cancel() {
        return future.cancel(true);
    }

    public boolean isCancelled() {
        return future.isCancelled();
    }
}
