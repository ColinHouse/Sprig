package sprig.compiler.diag;

/** Compiler phases, matching the agent tool protocol. */
public enum Phase {
    LEX, SYNTAX, NAME, TYPE, FLOW, JVM, RUNTIME
}
