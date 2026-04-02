package com.arcadia.pets.duel;

import java.util.UUID;

/**
 * Defines how one of the 28 pet skills behaves inside a duel.
 *
 * <p>Cooldown formula (in turns): {@code max(minCooldown, baseCooldown - level / 3)}.
 * This gives about 1 turn of reduction every 3 skill levels.</p>
 */
public final class DuelSkillDef {

    /** How many Spirit Points (SP) this skill costs to use. */
    public final int spCost;

    /** Base cooldown in turns at level 1. */
    public final int baseCooldown;

    /** Minimum cooldown regardless of level. */
    public final int minCooldown;

    /** Determines which pet(s) can be targeted. */
    public final DuelTargetType targetType;

    /** Server-side effect logic. */
    public final DuelEffectFn effectFn;

    public DuelSkillDef(int spCost, int baseCooldown, int minCooldown,
                        DuelTargetType targetType, DuelEffectFn effectFn) {
        this.spCost        = spCost;
        this.baseCooldown  = baseCooldown;
        this.minCooldown   = minCooldown;
        this.targetType    = targetType;
        this.effectFn      = effectFn;
    }

    /**
     * Returns the actual cooldown for a given skill level (1–10).
     * Each 3 levels shave one turn off the base, down to {@code minCooldown}.
     */
    public int getCooldown(int level) {
        return Math.max(minCooldown, baseCooldown - level / 3);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Functional interface
    // ─────────────────────────────────────────────────────────────────────────

    @FunctionalInterface
    public interface DuelEffectFn {
        /**
         * Execute the skill's duel effect.
         *
         * @param session    the active duel session (mutable)
         * @param actor      UUID of the player whose pet is acting
         * @param actorPet   0–2 index of the acting pet in the actor's roster
         * @param target     UUID of the targeted player (may equal actor for SELF / ALLY skills)
         * @param targetPet  0–2 index of the targeted pet in target's roster
         * @param level      current skill level (1–10)
         * @return a one-line combat-log message describing what happened
         */
        String apply(DuelSession session,
                     UUID actor, int actorPet,
                     UUID target, int targetPet,
                     int level);
    }
}
