package com.llmgateway.model.enums;

/**
 * Request priority carried via the {@code X-Request-Priority} header.
 * LOW-priority (batch) requests are rejected first when a team nears capacity,
 * preserving headroom for HIGH-priority (user-facing) traffic.
 */
public enum RequestPriority {
    HIGH,
    LOW;

    public static RequestPriority fromHeader(String raw) {
        if (raw == null) {
            return HIGH; // default: protect interactive latency
        }
        return "low".equalsIgnoreCase(raw.trim()) ? LOW : HIGH;
    }
}
