package sprig.compiler.diag;

/** Compiler phases, matching the agent tool protocol. */
public enum Phase {
    CLI, LEX, SYNTAX, NAME, TYPE, FLOW, JVM, RUNTIME
}
