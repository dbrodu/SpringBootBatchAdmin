package io.batchadmin.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Presentation helpers exposed to the Thymeleaf templates as the {@code batchAdminFormat} bean,
 * e.g. {@code ${@batchAdminFormat.statusClass(e.status)}}.
 */
public class BatchAdminFormat {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public String statusClass(String status) {
        String s = status == null ? "" : status.toUpperCase();
        return switch (s) {
            case "COMPLETED" -> "badge badge-ok";
            case "STARTED", "STARTING", "STOPPING" -> "badge badge-run";
            case "FAILED", "ABANDONED" -> "badge badge-fail";
            case "STOPPED" -> "badge badge-warn";
            default -> "badge badge-idle";
        };
    }

    public String date(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant);
    }

    public String duration(Long ms) {
        if (ms == null) {
            return "—";
        }
        if (ms < 1000) {
            return ms + " ms";
        }
        double seconds = ms / 1000.0;
        if (seconds < 60) {
            return String.format("%.1f s", seconds);
        }
        long minutes = (long) (seconds / 60);
        long rest = Math.round(seconds % 60);
        return minutes + "m " + rest + "s";
    }

    public String params(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(k).append('=').append(v);
        });
        return sb.toString();
    }
}
