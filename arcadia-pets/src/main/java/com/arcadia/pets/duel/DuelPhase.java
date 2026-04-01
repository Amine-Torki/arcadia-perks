package com.arcadia.pets.duel;

/** Lifecycle phases of a duel session. */
public enum DuelPhase {
    /** Both players are picking their roster of 3 pets. */
    ROSTER_PICK,
    /** Combat is in progress. */
    ACTIVE,
    /** Duel has ended — winner is set. */
    FINISHED
}
