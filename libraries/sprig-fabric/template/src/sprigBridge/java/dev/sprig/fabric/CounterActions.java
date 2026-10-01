package dev.sprig.fabric;

/** Narrow Java host boundary implemented by the Sprig counter domain object. */
public interface CounterActions {
    void tick();

    long count();
}
