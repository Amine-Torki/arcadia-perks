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
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> BLACKLISTED_ITEMS;

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

        b.comment("Item blacklist — items that cannot be listed on the AH.",
                  "Use registry IDs (e.g. 'minecraft:bedrock', 'arcadia_pets:pet_item').")
         .push("blacklist");

        BLACKLISTED_ITEMS = b
                .comment("List of item registry IDs that are blocked from being sold.")
                .defineListAllowEmpty(
                        java.util.List.of("items"),
                        () -> java.util.List.of("minecraft:bedrock", "minecraft:command_block", "minecraft:barrier"),
                        obj -> obj instanceof String s && s.contains(":"));

        b.pop();
        SPEC = b.build();
    }

    // ── Applied values ────────────────────────────────────────────────────────

    public static long LISTING_DURATION_MS       = 48L * 3_600_000L;
    public static int  MAX_LISTINGS_PER_PLAYER_V = 30;
    public static java.util.Set<String> BLACKLIST = java.util.Set.of("minecraft:bedrock", "minecraft:command_block", "minecraft:barrier");

    private AhConfig() {}

    public static void apply() {
        LISTING_DURATION_MS       = LISTING_DURATION_HOURS.get() * 3_600_000L;
        MAX_LISTINGS_PER_PLAYER_V = MAX_LISTINGS_PER_PLAYER.get();
        BLACKLIST = new java.util.HashSet<>(BLACKLISTED_ITEMS.get());
    }

    /** Returns true if the given item registry ID is blacklisted. */
    public static boolean isBlacklisted(String itemId) {
        return BLACKLIST.contains(itemId);
    }
}
