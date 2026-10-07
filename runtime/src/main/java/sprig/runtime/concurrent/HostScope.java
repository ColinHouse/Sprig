package sprig.runtime.concurrent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final List<Owned> tasks = new CopyOnWriteArrayList<>();
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

    private <T> HostTask<T> start(HostPool pool, Fn0<T> work) {
        CountDownLatch done = new CountDownLatch(1);
        HostTask<?>[] self = new HostTask<?>[1];
        Fn0<T> guarded = guarded(work, done, self);
        HostTask<T> task = pool == null ? HostTask.startOn(virtual, guarded) : pool.submit(guarded);
        self[0] = task;
        tasks.add(new Owned(task, done));
        return task;
    }

    /**
     * A failure that was not asked for by cancel() is the scope's failure: the
     * first one cancels every sibling, and the failing task still reports it
     * through await(). The latch tells the scope when the body has ended.
     */
    private <T> Fn0<T> guarded(Fn0<T> work, CountDownLatch done, HostTask<?>[] self) {
        return () -> {
            try {
                return work.apply();
            } catch (RuntimeException | Error e) {
                HostTask<?> me = self[0];
                boolean requested = me != null && me.isCancelled();
                if (!requested && failure.compareAndSet(null, e)) {
                    cancelAll();
                }
                throw e;
            } finally {
                done.countDown();
            }
        };
    }

    /** Asks every task to stop; each thread is interrupted. A cancelled task is not a failure of the scope. */
    public void cancelAll() {
        for (Owned owned : tasks) {
            owned.task().cancel();
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

    public boolean isOpen() {
        return !closed;
    }

    /** True once some task failed; the failure is reported by join(). */
    public boolean hasFailed() {
        return failure.get() != null;
    }

    public long taskCount() {
        return tasks.size();
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
