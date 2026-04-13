package com.arcadia.pets.duel;

/**
 * Specifies which pet(s) a duel skill action can target.
 * Drives both server-side resolution and client-side targeting UI.
 */
public enum DuelTargetType {

    /** The acting pet itself — no targeting UI needed. */
    SELF,

    /** One friendly pet (including self) — show ally selection UI. */
    ALLY_SINGLE,

    /** One living enemy pet — show enemy selection UI. */
    ENEMY_SINGLE,

    /** All living enemy pets — no targeting UI, auto-applied. */
    ALL_ENEMIES,

    /** All living friendly pets (including self) — no targeting UI. */
    ALL_ALLIES,

    /** A random living enemy pet — no targeting UI. */
    RANDOM_ENEMY
}
