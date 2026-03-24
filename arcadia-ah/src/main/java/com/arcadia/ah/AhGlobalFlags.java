package com.arcadia.ah;

/**
 * Runtime feature flags for the Arcadia AH module.
 *
 * <p>Hot-toggled via {@code /ah enable} and {@code /ah disable} (OP level 2).
 * Operators are always unaffected by the disabled state.</p>
 */
public final class AhGlobalFlags {

    /** Master kill-switch for all player-facing auction house functionality. */
    public static volatile boolean AH_ENABLED = true;

    private AhGlobalFlags() {}
}
