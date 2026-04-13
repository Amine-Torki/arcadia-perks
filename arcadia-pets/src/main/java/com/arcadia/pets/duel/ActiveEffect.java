package com.arcadia.pets.duel;

/**
 * An active status effect on a single pet during a duel.
 *
 * <ul>
 *   <li>{@code remainingTurns = -1} — permanent or "consume on trigger" effects
 *       (WITHER, SHIELD, REFLECT, FOCUSED, PHASED, LUCKY_BONUS).</li>
 *   <li>{@code remainingTurns > 0} — decrements each time this pet acts;
 *       removed when it reaches 0 (POISON, BURN, STUN, FATIGUE, FORTIFY, EVA_BOOST).</li>
 * </ul>
 */
public final class ActiveEffect {

    public final DuelStatusType type;
    /** -1 for "permanent / consume-on-trigger"; otherwise ticks down each pet-turn. */
    public int remainingTurns;
    /** Semantics vary by type — see {@link DuelStatusType} javadoc. */
    public float magnitude;

    public ActiveEffect(DuelStatusType type, int remainingTurns, float magnitude) {
        this.type = type;
        this.remainingTurns = remainingTurns;
        this.magnitude = magnitude;
    }

    /** Returns a short display label for the combat log / UI. */
    public String label() {
        return switch (type) {
            case POISON      -> "☠ Poison";
            case BURN        -> "🔥 Burn";
            case WITHER      -> "💀 Wither";
            case STUN        -> "⚡ Stun";
            case SHIELD      -> "🛡 Shield";
            case REFLECT     -> "🔄 Reflect";
            case FATIGUE     -> "⬇ Fatigue";
            case FORTIFY     -> "⬆ Fortify";
            case FOCUSED     -> "🎯 Focused";
            case PHASED      -> "👻 Phased";
            case LUCKY_BONUS -> "⭐ Lucky";
            case EVA_BOOST   -> "💨 Agile";
        };
    }
}
