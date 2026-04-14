package com.arcadia.pets.duel;

/** Difficulty tier for a bot duel opponent. Controls pet rarity and stat scaling. */
public enum BotDifficulty {
    EASY,    // 3 Common pets — for tutorials
    MEDIUM,  // 3 Uncommon/Rare pets
    HARD     // 3 Epic pets — for op stress-testing
}
