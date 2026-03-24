package com.arcadia.prestige;

/**
 * Runtime feature flags for the Arcadia Prestige module.
 *
 * <p>Hot-toggled via {@code /prestige enable} and {@code /prestige disable} (OP level 2).
 * Operators are always unaffected by the disabled state.</p>
 */
public final class PrestigeGlobalFlags {

    /** Master kill-switch for cosmetics, daily rewards, and the prestige hub. */
    public static volatile boolean PRESTIGE_ENABLED = true;

    private PrestigeGlobalFlags() {}
}
