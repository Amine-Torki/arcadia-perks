package com.arcadia.pets;

/**
 * Runtime feature flags for the Arcadia Pets module.
 *
 * <p>Set at launch via JVM property so different servers can disable pets
 * without modifying world data or needing separate jars:</p>
 * <pre>  -Darcadia.pets.enabled=false</pre>
 *
 * <p>Can also be hot-toggled in-game via {@code /pets disable} and
 * {@code /pets enable} (requires OP level 2). Operators are always
 * unaffected by the disabled state.</p>
 */
public final class PetsGlobalFlags {

    /**
     * Master kill-switch for all player-facing pets functionality.
     * When {@code false}, non-operators cannot open pet bags, use pet commands,
     * or access any pets-related UI. Operators (permission level ≥ 2) bypass this.
     */
    public static volatile boolean PETS_ENABLED =
            System.getProperty("arcadia.pets.enabled", "true").equalsIgnoreCase("true");

    private PetsGlobalFlags() {}
}
