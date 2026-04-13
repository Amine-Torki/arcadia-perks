package com.arcadia.pets.duel;

/**
 * All status-effect types that can be applied to a pet during a duel.
 * The magnitude/duration fields in {@link ActiveEffect} carry the numeric details.
 */
public enum DuelStatusType {

    /**
     * Deals {@code magnitude} damage per turn for {@code remainingTurns} turns.
     */
    POISON,

    /**
     * Deals {@code magnitude} damage per turn for {@code remainingTurns} turns.
     */
    BURN,

    /**
     * Permanently reduces the pet's max HP by {@code magnitude}.
     * Applied instantly; {@code remainingTurns} = -1 (lasts the whole duel).
     */
    WITHER,

    /**
     * The pet skips its next turn.
     * {@code remainingTurns} = 1, consumed when the turn would be taken.
     */
    STUN,

    /**
     * Reduces the next incoming hit by {@code magnitude}% (0–1 scale).
     * Consumed on first incoming hit; {@code remainingTurns} = -1.
     */
    SHIELD,

    /**
     * Reflects {@code magnitude}% (0–1 scale) of the next incoming hit back to the attacker.
     * Consumed on first incoming hit; {@code remainingTurns} = -1.
     */
    REFLECT,

    /**
     * Multiplies the pet's attack output by {@code (1.0 - magnitude)} for {@code remainingTurns} turns.
     * e.g. magnitude=0.5 → ATK halved.
     */
    FATIGUE,

    /**
     * Multiplies incoming damage by {@code (1.0 - magnitude)} for {@code remainingTurns} turns.
     * e.g. magnitude=0.25 → 25% damage reduction.
     */
    FORTIFY,

    /**
     * Multiplies the pet's next attack by {@code magnitude}.
     * Consumed on the next attack action; {@code remainingTurns} = -1.
     */
    FOCUSED,

    /**
     * The pet is immune to the very next incoming hit.
     * Consumed on the next hit; {@code remainingTurns} = -1.
     */
    PHASED,

    /**
     * Adds {@code magnitude}% (integer) to the pet's effective CRIT chance for its next attack.
     * Consumed after that attack; {@code remainingTurns} = -1.
     */
    LUCKY_BONUS,

    /**
     * Adds {@code magnitude}% (integer) to the pet's effective EVA chance for {@code remainingTurns} turns.
     */
    EVA_BOOST
}
