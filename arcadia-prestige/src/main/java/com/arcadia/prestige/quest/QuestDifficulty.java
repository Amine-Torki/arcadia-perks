package com.arcadia.prestige.quest;

/** Difficulty tier for a daily quest, controlling target range and rewards. */
public enum QuestDifficulty {
    EASY  ( 3, 10, 15, 1),
    MEDIUM(10, 25, 30, 2),
    HARD  (25, 60, 60, 5);

    public final int minTarget;
    public final int maxTarget;
    /** Numismatics coin reward. */
    public final int rewardCoins;
    /** Star Essence reward. */
    public final int rewardEssence;

    QuestDifficulty(int minTarget, int maxTarget, int rewardCoins, int rewardEssence) {
        this.minTarget     = minTarget;
        this.maxTarget     = maxTarget;
        this.rewardCoins   = rewardCoins;
        this.rewardEssence = rewardEssence;
    }

    public String displayName() {
        return switch (this) {
            case EASY   -> "§aEasy";
            case MEDIUM -> "§eNormal";
            case HARD   -> "§cHard";
        };
    }
}
