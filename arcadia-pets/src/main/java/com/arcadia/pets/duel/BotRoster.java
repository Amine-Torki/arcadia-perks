package com.arcadia.pets.duel;

import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetRarity;
import com.arcadia.pets.item.PetRoller;
import com.arcadia.pets.item.PetStat;

import java.util.EnumMap;
import java.util.UUID;

/**
 * Generates preset bot pet rosters for practice/debug duels.
 * Bot pets are created in-memory — they don't exist in any collection.
 */
public final class BotRoster {

    private BotRoster() {}

    // Fixed mob types per difficulty so the player knows what to expect
    private static final String[][] MOBS = {
        { "minecraft:chicken", "minecraft:pig",    "minecraft:cow"    }, // EASY
        { "minecraft:wolf",    "minecraft:fox",    "minecraft:bee"    }, // MEDIUM
        { "minecraft:blaze",   "minecraft:warden", "minecraft:ravager"}, // HARD
    };

    /**
     * Creates 3 bot PetData objects for the given difficulty.
     * Stats are fixed mid-range values scaled per tier.
     */
    public static PetData[] generate(BotDifficulty difficulty) {
        int tier = difficulty.ordinal(); // 0, 1, 2
        String[] mobs = MOBS[tier];
        PetRarity rarity = switch (difficulty) {
            case EASY   -> PetRarity.COMMON;
            case MEDIUM -> PetRarity.RARE;
            case HARD   -> PetRarity.EPIC;
        };
        PetData[] roster = new PetData[3];
        for (int i = 0; i < 3; i++) {
            roster[i] = buildPet(mobs[i], rarity, tier);
        }
        return roster;
    }

    private static PetData buildPet(String mobType, PetRarity rarity, int tier) {
        EnumMap<PetStat, Integer> stats = new EnumMap<>(PetStat.class);
        int base = 4 + tier * 3; // 4 / 7 / 10
        for (PetStat s : PetStat.values()) stats.put(s, base);

        return new PetData(
                UUID.randomUUID(),
                mobType,
                rarity,
                stats,
                false,
                "Bot " + capitalize(mobType.replace("minecraft:", "")),
                100,
                100,
                PetRoller.rollSkills(mobType, rarity)
        );
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
