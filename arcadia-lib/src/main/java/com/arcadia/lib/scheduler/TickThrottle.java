package com.arcadia.lib.scheduler;

public final class TickThrottle {
    private long nextRunAtMs;
    private final long intervalMs;

    public TickThrottle(long intervalMs) {
        if (intervalMs < 0) {
            throw new IllegalArgumentException("intervalMs must be >= 0");
        }
        this.intervalMs = intervalMs;
    }

    public boolean shouldRun(long nowMs) {
        if (nowMs < nextRunAtMs) return false;
        nextRunAtMs = nowMs + intervalMs;
        return true;
    }

    public void reset() {
        nextRunAtMs = 0L;
    }

    public long nextRunAtMs() {
        return nextRunAtMs;
    }
}
