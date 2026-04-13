package com.arcadia.lib.economy;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side TOML config for economy settings.
 * Loaded from {@code config/arcadia/lib/economy.toml}.
 *
 * <p>Supported providers:
 * <ul>
 *   <li>{@code "numismatics"} — Create: Numismatics (spurs/copper/silver/gold)</li>
 *   <li>{@code "items"} — Vanilla item-based currency (configurable item)</li>
 *   <li>{@code "none"} — No economy, everything is free</li>
 * </ul>
 */
public final class EconomyConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> PROVIDER;
    public static final ModConfigSpec.ConfigValue<String> CURRENCY_NAME;
    public static final ModConfigSpec.ConfigValue<String> CURRENCY_SYMBOL;
    public static final ModConfigSpec.ConfigValue<String> ITEM_CURRENCY_ID;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Economy settings for the Arcadia Auction House and future mods.",
                  "Change the provider to switch between currency systems.")
         .push("economy");

        PROVIDER = b.comment(
                "Economy provider to use.",
                "Options: 'numismatics' (Create: Numismatics), 'items' (vanilla item currency), 'none' (free)")
                .define("provider", "numismatics");

        CURRENCY_NAME = b.comment("Display name for the currency (used in messages)")
                .define("currency_name", "Coins");

        CURRENCY_SYMBOL = b.comment("Short symbol for the currency (used in compact displays)")
                .define("currency_symbol", "\u00A4"); // ¤

        ITEM_CURRENCY_ID = b.comment(
                "When provider is 'items', this is the registry ID of the item used as currency.",
                "Examples: 'minecraft:gold_ingot', 'minecraft:diamond', 'minecraft:emerald'")
                .define("item_currency_id", "minecraft:emerald");

        b.pop();
        SPEC = b.build();
    }

    // Applied values
    public static String ACTIVE_PROVIDER   = "numismatics";
    public static String DISPLAY_NAME      = "Coins";
    public static String DISPLAY_SYMBOL    = "\u00A4";
    public static String ITEM_ID           = "minecraft:emerald";

    private EconomyConfig() {}

    public static void apply() {
        ACTIVE_PROVIDER = PROVIDER.get();
        DISPLAY_NAME    = CURRENCY_NAME.get();
        DISPLAY_SYMBOL  = CURRENCY_SYMBOL.get();
        ITEM_ID         = ITEM_CURRENCY_ID.get();
    }
}
