package sprig.runtime.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import sprig.runtime.Fn0;
import sprig.runtime.NonNull;
import sprig.runtime.SprigError;

/**
 * A structured scope: every task started in it ends before the scope does.
 * Tasks run on virtual threads (JDK 21), so ten thousand blocking tasks cost
 * what ten cost. The first failure cancels the sibling tasks and is rethrown
 * when the scope is joined; cancellation interrupts the task, which takes
 * effect at its blocking points. Sprig owns the API ({@code @std/concurrent.spr});
 * this class owns the executor mechanics.
 */
public final class HostScope {
    /** A task and the signal its body gives when it has really ended (a cancelled Future returns early). */
    private record Owned(HostTask<?> task, CountDownLatch done) {
    }

    private final ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentLinkedQueue<Owned> tasks = new ConcurrentLinkedQueue<>();
    private final AtomicLong taskCount = new AtomicLong();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private volatile boolean closed;

    /** Starts work on a virtual thread owned by this scope. */
    @NonNull
    public <T> HostTask<T> spawn(Fn0<T> work) {
        requireOpen(work);
        return start(null, work);
    }

    /**
     * Starts work that only has an effect. Sprig's Unit is not a value, so the
     * task completes with true once work has run.
     */
    @NonNull
    public HostTask<Boolean> run(Runnable work) {
        if (work == null) throw new SprigError("task work must not be null");
        Fn0<Boolean> effect = () -> {
            work.run();
            return Boolean.TRUE;
        };
        requireOpen(effect);
        return start(null, effect);
    }

    /** Starts work on the pool's platform threads; the task still belongs to this scope. */
    @NonNull
    public <T> HostTask<T> spawnOn(HostPool pool, Fn0<T> work) {
        requireOpen(work);
        if (pool == null) throw new SprigError("pool must not be null");
        return start(pool, work);
    }

    private void requireOpen(Fn0<?> work) {
        if (work == null) throw new SprigError("task work must not be null");
        if (closed) throw new SprigError("the scope has ended; spawn inside the scope body");
    }

    /**
     * Exactly one party marks a task as ended: its body, which claims the task
     * when it starts and counts the latch down in its finally, or, when the
     * task completed without running its body (cancelled while queued, or
     * dropped by a pool's shutdownNow), the task's completion. A task cancelled
     * while its body runs is still waited for until the body returns.
     */
    private <T> HostTask<T> start(HostPool pool, Fn0<T> work) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean claimed = new AtomicBoolean();
        HostTask<?>[] self = new HostTask<?>[1];
        Fn0<T> guarded = guarded(work, done, claimed, self);
        Runnable endedUnstarted = () -> {
            if (claimed.compareAndSet(false, true)) {
                done.countDown();
            }
        };
        HostTask<T> task = pool == null ? HostTask.startOn(virtual, guarded, endedUnstarted)
                : pool.submit(guarded, endedUnstarted);
        self[0] = task;
        tasks.add(new Owned(task, done));
        taskCount.incrementAndGet();
        return task;
    }

    /**
     * A failure that was not asked for by cancel() is the scope's failure: the
     * first one cancels every sibling, and the failing task still reports its
     * own failure through await(), so it is not cancelled itself. The latch
     * tells the scope when the body has ended.
     */
    private <T> Fn0<T> guarded(Fn0<T> work, CountDownLatch done, AtomicBoolean claimed, HostTask<?>[] self) {
        return () -> {
            if (!claimed.compareAndSet(false, true)) {
                throw new CancellationException("cancelled before it started");
            }
            try {
                return work.apply();
            } catch (RuntimeException | Error e) {
                HostTask<?> me = self[0];
                boolean requested = me != null && me.isCancelled();
                if (!requested && failure.compareAndSet(null, e)) {
                    cancelAllExcept(me);
                }
                throw e;
            } finally {
                done.countDown();
            }
        };
    }

    /** Asks every task to stop; each thread is interrupted. A cancelled task is not a failure of the scope. */
    public void cancelAll() {
        cancelAllExcept(null);
    }

    private void cancelAllExcept(HostTask<?> keep) {
        for (Owned owned : tasks) {
            if (owned.task() != keep) {
                owned.task().cancel();
            }
        }
    }

    /**
     * After the body returned: waits for every task, then rethrows the first
     * failure as an Error. Leaving the scope never loses a task.
     */
    public void join() {
        closed = true;
        waitAll();
        virtual.shutdown();
        Throwable first = failure.get();
        if (first != null) {
            throw HostTask.failure(first);
        }
    }

    /** After the body failed: cancels every task and waits for them; nothing is rethrown. */
    public void abandon() {
        closed = true;
        cancelAll();
        waitAll();
        virtual.shutdown();
    }

    /**
     * After the body failed with {@code bodyError}: abandons the scope and
     * returns the error to report. A body that failed only because it awaited
     * a task that a sibling's failure cancelled reports that first failure,
     * not the cancellation (#205); any other error of the body is its own.
     */
    @NonNull
    public SprigError failedWith(SprigError bodyError) {
        abandon();
        Throwable first = failure.get();
        if (first != null && bodyError.getCause() instanceof CancellationException) {
            return HostTask.failure(first);
        }
        return bodyError;
    }

    public boolean isOpen() {
        return !closed;
    }

    /** True once some task failed; the failure is reported by join(). */
    public boolean hasFailed() {
        return failure.get() != null;
    }

    public long taskCount() {
        return taskCount.get();
    }

    private void waitAll() {
        boolean interrupted = false;
        for (Owned owned : tasks) {
            while (true) {
                try {
                    owned.done().await(); // the body has ended, whatever its outcome or cancellation
                    break;
                } catch (InterruptedException e) {
                    interrupted = true; // keep waiting: the scope's tasks must end first
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
