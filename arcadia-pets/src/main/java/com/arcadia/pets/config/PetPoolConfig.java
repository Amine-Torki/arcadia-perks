package com.arcadia.pets.config;

import com.arcadia.pets.item.PetRarity;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side TOML config that controls all pet balancing values.
 *
 * <p>Loaded from {@code config/arcadia-pets.toml}. Edit that file to tune
 * the pet meta without recompiling — changes take effect on server restart.</p>
 *
 * <h3>Sections</h3>
 * <ul>
 *   <li>{@code [rarities]} — drop weights and minimum stat floors per rarity</li>
 *   <li>{@code [stats]}    — base star-weight table for stat rolling</li>
 *   <li>{@code [pools]}    — which mobs belong to each rarity pool</li>
 * </ul>
 */
public final class PetPoolConfig {

    public static final ModConfigSpec SPEC;

    // ── Rarity weights & stat floors ─────────────────────────────────────────

    /** Roll weights for each rarity in enum order: COMMON … MYTHIC. */
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> RARITY_WEIGHTS;
    /** Minimum stat star floor per rarity in enum order: COMMON … MYTHIC. */
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> STAT_FLOORS;

    // ── Star weights ──────────────────────────────────────────────────────────

    /** Base probability weights for star rolling: index 0 = 1★, index 4 = 5★. */
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> STAR_WEIGHTS;

    /** Live int[] cache updated by {@link #applyToRarities()}. Read by {@link com.arcadia.pets.item.PetRoller}. */
    public static int[] CACHED_STAR_WEIGHTS = {40, 30, 18, 9, 3};

    // ── Mob pools ─────────────────────────────────────────────────────────────

    private static final ModConfigSpec.ConfigValue<List<? extends String>> COMMON;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> UNCOMMON;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> RARE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> EPIC;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> LEGENDARY;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> MYTHIC;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        // ── [rarities] ────────────────────────────────────────────────────────
        b.comment(
                "Rarity balancing — weights and stat floors.",
                "Order: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC"
        ).push("rarities");

        RARITY_WEIGHTS = b
                .comment(
                        "Roll weight for each rarity. Higher = more common.",
                        "The weights are relative — e.g. [4500,3000,1500,700,250,50]",
                        "means COMMON is 4500/(sum) likely when rolling from COMMON floor."
                )
                .defineList("weights",
                        List.of(4500, 3000, 1500, 700, 250, 50),
                        o -> o instanceof Integer i && i >= 0);

        STAT_FLOORS = b
                .comment(
                        "Minimum stat star per rarity (1–5). Pets cannot roll below this floor.",
                        "Order: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC"
                )
                .defineList("stat_floors",
                        List.of(1, 1, 2, 2, 3, 4),
                        o -> o instanceof Integer i && i >= 1 && i <= 5);

        b.pop();

        // ── [stats] ───────────────────────────────────────────────────────────
        b.comment(
                "Stat rolling — base weights for each star level.",
                "Index 0 = 1★, index 4 = 5★. Redistributed upward for rarities with a floor > 1."
        ).push("stats");

        STAR_WEIGHTS = b
                .comment("Base probability weights for 1★ through 5★.")
                .defineList("star_weights",
                        List.of(40, 30, 18, 9, 3),
                        o -> o instanceof Integer i && i >= 0);

        b.pop();

        // ── [pools] ───────────────────────────────────────────────────────────
        b.comment(
                "Mob pools — which entity types can appear at each rarity.",
                "Use namespaced IDs, e.g. \"minecraft:fox\" or \"mymod:custom_mob\"."
        ).push("pools");

        // Tier rebalance: staff members occupy Mythic; everything else shifts down one tier.
        // Old common pets stay Common (can't go lower); old uncommon pets merge into Common.

        COMMON = b
                .comment("Mobs rollable at COMMON rarity")
                .defineListAllowEmpty("common", List.of(
                        // Original common
                        "minecraft:chicken", "minecraft:pig", "minecraft:cow",
                        "minecraft:sheep", "minecraft:rabbit",
                        // Shifted down from old Uncommon
                        "minecraft:cat", "minecraft:fox",
                        "minecraft:bee"
                ), o -> o instanceof String s && s.contains(":"));

        UNCOMMON = b
                .comment("Mobs rollable at UNCOMMON rarity")
                .defineListAllowEmpty("uncommon", List.of(
                        "minecraft:wolf",
                        "minecraft:axolotl", "minecraft:panda", "minecraft:frog",
                        "minecraft:turtle"
                ), o -> o instanceof String s && s.contains(":"));

        RARE = b
                .comment("Mobs rollable at RARE rarity")
                .defineListAllowEmpty("rare", List.of(
                        // Shifted down from old Epic
                        "minecraft:allay", "minecraft:sniffer", "minecraft:strider"
                ), o -> o instanceof String s && s.contains(":"));

        EPIC = b
                .comment("Mobs rollable at EPIC rarity")
                .defineListAllowEmpty("epic", List.of(
                        // Shifted down from old Legendary
                        "minecraft:iron_golem", "minecraft:enderman",
                        "minecraft:blaze", "minecraft:wither_skeleton"
                ), o -> o instanceof String s && s.contains(":"));

        LEGENDARY = b
                .comment("Mobs rollable at LEGENDARY rarity")
                .defineListAllowEmpty("legendary", List.of(
                        // Shifted down from old Mythic
                        "minecraft:warden",
                        "minecraft:ender_dragon",
                        "minecraft:wither",
                        "minecraft:elder_guardian",
                        "minecraft:ravager",
                        "minecraft:breeze",
                        "minecraft:shulker"
                ), o -> o instanceof String s && s.contains(":"));

        MYTHIC = b
                .comment("Mobs rollable at MYTHIC rarity — Staff member pets (player-skin entities, to be implemented)")
                .defineListAllowEmpty("mythic", List.of(
                        // Staff pets — arcadia_prestige:staff_* entities (implementation pending)
                        "arcadia_prestige:staff_vyrriox",
                        "arcadia_prestige:staff_siriust",
                        "arcadia_prestige:staff_tyvrax",
                        "arcadia_prestige:staff_jlpopeye",
                        "arcadia_prestige:staff_gaspich"
                ), o -> o instanceof String s && s.contains(":"));

        b.pop();
        SPEC = b.build();
    }

    private PetPoolConfig() {}

    // ── Public accessors ──────────────────────────────────────────────────────

    /** Returns the mob pool for a given rarity, falling back to pig if empty. */
    @SuppressWarnings("unchecked")
    public static List<String> getPool(PetRarity rarity) {
        List<? extends String> raw = switch (rarity) {
            case COMMON    -> COMMON.get();
            case UNCOMMON  -> UNCOMMON.get();
            case RARE      -> RARE.get();
            case EPIC      -> EPIC.get();
            case LEGENDARY -> LEGENDARY.get();
            case MYTHIC    -> MYTHIC.get();
        };
        return raw.isEmpty() ? List.of("minecraft:pig") : (List<String>) raw;
    }

    /** All mobs from all pools combined — used for roulette filler cards. */
    public static List<String> getAllMobs() {
        List<String> all = new ArrayList<>();
        for (PetRarity r : PetRarity.values()) all.addAll(getPool(r));
        return all;
    }

    /**
     * Pushes config values into {@link PetRarity} (weights + stat floors)
     * and {@link ArcadiaConfig} (star weights). Called once when the config loads.
     */
    public static void applyToRarities() {
        List<? extends Integer> weights = RARITY_WEIGHTS.get();
        List<? extends Integer> floors  = STAT_FLOORS.get();
        List<? extends Integer> stars   = STAR_WEIGHTS.get();

        PetRarity.applyConfig(toIntArray(weights), toIntArray(floors));

        int[] starArr = toIntArray(stars);
        if (starArr.length == CACHED_STAR_WEIGHTS.length) {
            System.arraycopy(starArr, 0, CACHED_STAR_WEIGHTS, 0, starArr.length);
        }
    }

    private static int[] toIntArray(List<? extends Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }
}
