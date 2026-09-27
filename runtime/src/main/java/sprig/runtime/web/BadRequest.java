package sprig.runtime.web;

import sprig.runtime.SprigError;

/** Explicit malformed client input; web routing stays in Sprig. */
public final class BadRequest extends SprigError {
    public BadRequest(String message) { super(message); }
}
