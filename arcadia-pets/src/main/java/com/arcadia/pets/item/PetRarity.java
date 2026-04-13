package com.arcadia.pets.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.concurrent.ThreadLocalRandom;

public enum PetRarity {

    COMMON("Common", ChatFormatting.WHITE, 7500, 1, false),
    UNCOMMON("Uncommon", ChatFormatting.GREEN, 1400, 1, false),
    RARE("Rare", ChatFormatting.BLUE, 700, 2, false),
    EPIC("Epic", ChatFormatting.DARK_PURPLE, 300, 2, true),
    LEGENDARY("Legendary", ChatFormatting.GOLD, 100, 3, true),
    MYTHIC("Mythic", ChatFormatting.RED, 50, 4, true);

    private final String displayName;
    private final ChatFormatting color;
    private int weight;       // mutable — overridden by PetPoolConfig on load
    private int statFloor;    // mutable — overridden by PetPoolConfig on load
    private final boolean hasGlint;

    PetRarity(String displayName, ChatFormatting color, int weight, int statFloor, boolean hasGlint) {
        this.displayName = displayName;
        this.color = color;
        this.weight = weight;
        this.statFloor = statFloor;
        this.hasGlint = hasGlint;
    }

    /** Raw English name — use {@link #getTranslatableName()} for player-facing text. */
    public String getDisplayName() {
        return displayName;
    }

    /** Translation key for this rarity's display name, e.g. {@code arcadia_pets.rarity.common}. */
    public String getTranslationKey() {
        return "arcadia_pets.rarity." + name().toLowerCase();
    }

    /** Translatable {@link Component} with the rarity's colour applied. */
    public Component getTranslatableName() {
        return Component.translatable(getTranslationKey()).withStyle(Style.EMPTY.withColor(color));
    }

    public ChatFormatting getColor() {
        return color;
    }

    public int getWeight() {
        return weight;
    }

    public int getStatFloor() {
        return statFloor;
    }

    public boolean hasGlint() {
        return hasGlint;
    }

    /**
     * Returns a styled Component with the rarity's color applied.
     */
    public Component getStyledName() {
        return Component.literal(displayName).withStyle(Style.EMPTY.withColor(color));
    }

    /**
     * Returns the PetRarity for the given ordinal, or COMMON if out of bounds.
     */
    public static PetRarity fromId(int ordinal) {
        PetRarity[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return COMMON;
        }
        return values[ordinal];
    }

    /**
     * Rolls a random rarity for bag/reveal purposes.
     * MYTHIC is always excluded — staff pets are granted directly, never rolled.
     * Weights are recalculated among eligible rarities (minimum ≤ r ≤ LEGENDARY).
     */
    public static PetRarity rollRarity(PetRarity minimum) {
        PetRarity[] values = values();
        int totalWeight = 0;
        for (PetRarity r : values) {
            if (r.ordinal() >= minimum.ordinal() && r != MYTHIC) {
                totalWeight += r.weight;
            }
        }

        if (totalWeight <= 0) {
            return minimum == MYTHIC ? LEGENDARY : minimum;
        }

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (PetRarity r : values) {
            if (r.ordinal() >= minimum.ordinal() && r != MYTHIC) {
                cumulative += r.weight;
                if (roll < cumulative) {
                    return r;
                }
            }
        }

        return minimum == MYTHIC ? LEGENDARY : minimum;
    }

    /**
     * Returns the next rarity tier for fusion output, or empty if this rarity cannot be fused
     * (Legendary and Mythic are the ceiling — Mythic is staff-only, so Legendary cannot be fused up).
     */
    public java.util.Optional<PetRarity> next() {
        if (ordinal() >= LEGENDARY.ordinal()) return java.util.Optional.empty();
        return java.util.Optional.of(values()[ordinal() + 1]);
    }

    /**
     * Applies config-driven weights and stat floors to all rarity values.
     * Called once when {@code arcadia-pets.toml} is loaded.
     * Arrays are indexed in enum declaration order (COMMON=0 … MYTHIC=5).
     */
    public static void applyConfig(int[] weights, int[] statFloors) {
        PetRarity[] values = values();
        for (int i = 0; i < values.length; i++) {
            if (i < weights.length   && weights[i]    >= 0) values[i].weight    = weights[i];
            if (i < statFloors.length && statFloors[i] >= 1) values[i].statFloor = statFloors[i];
        }
    }
}
