package sprig.runtime.sqlite;

import sprig.runtime.SprigError;
import java.sql.SQLException;

/** Recoverable adapter failure; the SQL cause is retained for local debugging. */
public final class SqliteError extends SprigError {
    private static final long serialVersionUID = 1L;
    public SqliteError(String message) { super(message); }
    public SqliteError(String message, Throwable cause) { super(message, cause); }
    public boolean constraint() {
        return getCause() instanceof SQLException failure && (failure.getErrorCode() & 255) == 19;
    }
}
