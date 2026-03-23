package com.arcadia.ah.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side TOML config for the Arcadia Auction House.
 *
 * <p>Loaded from {@code config/arcadia-ah.toml}.</p>
 */
public final class AhConfig {

    public static final ModConfigSpec SPEC;

    // ── [listings] ────────────────────────────────────────────────────────────

    public static final ModConfigSpec.IntValue  LISTING_DURATION_HOURS;
    public static final ModConfigSpec.IntValue  MAX_LISTINGS_PER_PLAYER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Auction House listing settings.").push("listings");

        LISTING_DURATION_HOURS = b
                .comment("How many hours a listing stays active before expiring and returning to the seller's mailbox.")
                .defineInRange("listing_duration_hours", 48, 1, 336);

        MAX_LISTINGS_PER_PLAYER = b
                .comment("Maximum number of active listings a single player can have at once.")
                .defineInRange("max_listings_per_player", 30, 1, 500);

        b.pop();
        SPEC = b.build();
    }

    // ── Applied values ────────────────────────────────────────────────────────

    public static long LISTING_DURATION_MS       = 48L * 3_600_000L;
    public static int  MAX_LISTINGS_PER_PLAYER_V = 30;

    private AhConfig() {}

    public static void apply() {
        LISTING_DURATION_MS       = LISTING_DURATION_HOURS.get() * 3_600_000L;
        MAX_LISTINGS_PER_PLAYER_V = MAX_LISTINGS_PER_PLAYER.get();
    }
}
