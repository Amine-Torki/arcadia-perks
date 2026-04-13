package com.arcadia.prestige.quest;

/**
 * All supported daily-quest activity types.
 *
 * <p>{@link #usesContext()} is true for types that need a specific
 * target identifier (mob type, block type) stored in the definition.</p>
 */
public enum QuestType {
    KILL_ANY,
    KILL_MOB,
    AH_SELL,
    PET_SUMMON,
    PET_FEED,
    DAILY_CLAIM,
    PET_DUEL_WIN,
    OPEN_PET_BAG,
    BLOCK_BREAK;

    public boolean usesContext() {
        return this == KILL_MOB || this == BLOCK_BREAK;
    }
}
