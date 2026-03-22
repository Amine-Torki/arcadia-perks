package com.arcadia.pets.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The six permanent "gene" stats that every pet possesses (1–5★).
 * These are the immutable building blocks; actual combat values
 * are derived from them via {@link DerivedPetStats}.
 *
 * <pre>
 *  POWER      → ATK damage
 *  ENDURANCE  → DEF%, max HP
 *  AGILITY    → movement SPD, CRIT%, EVA%
 *  WIT        → EVA% (shared), DEFEND-mode dodge
 *  CHARISMA   → death cooldown reduction
 *  LUCK       → CRIT% (shared), daily reward bonus
 * </pre>
 */
public enum PetStat {

    POWER     ("Power",     "POW"),
    ENDURANCE ("Endurance", "END"),
    AGILITY   ("Agility",   "AGI"),
    WIT       ("Wit",       "WIT"),
    CHARISMA  ("Charisma",  "CHM"),
    LUCK      ("Luck",      "LCK");

    private final String displayName;
    private final String abbrev;

    PetStat(String displayName, String abbrev) {
        this.displayName = displayName;
        this.abbrev = abbrev;
    }

    /** Raw English name — use {@link #getTranslatableName()} for player-facing text. */
    public String getDisplayName() { return displayName; }

    /** Translation key, e.g. {@code arcadia_prestige.stat.power}. */
    public String getTranslationKey() {
        return "arcadia_prestige.stat." + name().toLowerCase();
    }

    /** Translatable {@link Component} for this stat's full name. */
    public Component getTranslatableName() {
        return Component.translatable(getTranslationKey());
    }

    /** Short 3-letter abbreviation used as a prefix in stat labels. */
    public String getIcon() { return abbrev; }

    /**
     * Returns a star display Component like "★★★☆☆" where filled stars are gold
     * and empty stars are gray, for a max of 5 stars.
     */
    public static Component getStarDisplay(int stars) {
        MutableComponent component = Component.empty();
        for (int i = 1; i <= 5; i++) {
            if (i <= stars) {
                component.append(Component.literal("\u2605").withStyle(ChatFormatting.GOLD));
            } else {
                component.append(Component.literal("\u2606").withStyle(ChatFormatting.GRAY));
            }
        }
        return component;
    }
}
