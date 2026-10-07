package sprig.runtime.concurrent;

import java.util.concurrent.locks.ReentrantLock;
import sprig.runtime.Fn0;
import sprig.runtime.NonNull;
import sprig.runtime.SprigError;

/** A mutual-exclusion lock that runs a function while held; reentrant on one thread. */
public final class HostLock {
    private final ReentrantLock lock = new ReentrantLock();

    /** Runs the work while holding the lock and returns its result. */
    @NonNull
    public <T> T withLock(Fn0<T> work) {
        if (work == null) throw new SprigError("lock work must not be null");
        lock.lock();
        try {
            T value = work.apply();
            if (value == null) throw new SprigError("locked work produced null");
            return value;
        } finally {
            lock.unlock();
        }
    }

    /** Runs the action while holding the lock. */
    public void run(Fn0<Void> action) {
        if (action == null) throw new SprigError("lock action must not be null");
        lock.lock();
        try {
            action.apply();
        } finally {
            lock.unlock();
        }
    }

    public boolean isHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }
}
