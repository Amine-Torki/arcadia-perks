package com.arcadia.pets.client;

/** Client-side aftershock cooldown tracker, fed by {@link com.arcadia.pets.network.S2CAftershockCooldown}. */
public final class ClientAftershockState {

    private static long cooldownEndMs   = 0L;
    private static long totalCooldownMs = 0L;

    private ClientAftershockState() {}

    public static void start(int durationMs) {
        totalCooldownMs = durationMs;
        cooldownEndMs   = System.currentTimeMillis() + durationMs;
    }

    public static boolean isOnCooldown() {
        return System.currentTimeMillis() < cooldownEndMs;
    }

    /** 1.0 = just fired (full bar), 0.0 = ready (empty). */
    public static float getCooldownFraction() {
        if (totalCooldownMs <= 0) return 0f;
        long remaining = cooldownEndMs - System.currentTimeMillis();
        if (remaining <= 0) return 0f;
        return Math.min(1f, (float) remaining / totalCooldownMs);
    }

    public static float getRemainingSeconds() {
        long remaining = cooldownEndMs - System.currentTimeMillis();
        return remaining <= 0 ? 0f : remaining / 1000f;
    }
}
