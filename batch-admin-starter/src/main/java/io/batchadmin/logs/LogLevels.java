package io.batchadmin.logs;

import java.util.List;
import java.util.Locale;

/**
 * Backend-agnostic ranking of the standard log levels, used to filter captured logs by a
 * configurable minimum level without depending on any specific logging framework.
 */
public final class LogLevels {

    /** Levels from least to most severe, as shown in the GUI level selector. */
    public static final List<String> ORDERED = List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");

    private LogLevels() {
    }

    /** Numeric rank of a level name; unknown names rank as INFO. */
    public static int rank(String level) {
        if (level == null) {
            return 20;
        }
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "TRACE" -> 0;
            case "DEBUG" -> 10;
            case "WARN", "WARNING" -> 30;
            case "ERROR", "SEVERE" -> 40;
            default -> 20; // INFO
        };
    }

    public static boolean isValid(String level) {
        return level != null && ORDERED.contains(level.toUpperCase(Locale.ROOT));
    }
}
