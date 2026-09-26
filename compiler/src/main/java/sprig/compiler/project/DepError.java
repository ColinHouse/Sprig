package sprig.compiler.project;

import java.util.LinkedHashMap;
import java.util.Map;

/** Structured dependency failure: stable code, message, optional JSON data. */
public final class DepError extends RuntimeException {
    public final String code;
    public final Map<String, Object> data;

    public DepError(String code, String message, String detail) {
        super(message);
        this.code = code;
        this.data = new LinkedHashMap<>();
        if (detail != null) {
            data.put("detail", detail);
        }
    }

    public DepError with(String key, Object value) {
        data.put(key, value);
        return this;
    }
}
