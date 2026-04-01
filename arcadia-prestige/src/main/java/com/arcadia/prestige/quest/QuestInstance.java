package com.arcadia.prestige.quest;

import java.util.UUID;

/**
 * Mutable runtime state of one quest for one player on one day.
 * Immutable via wither-style factory methods to keep the cache safe.
 */
public record QuestInstance(
        UUID            playerUuid,
        String          dateKey,     // "2026-04-01"
        int             questIndex,  // 0, 1, 2
        QuestDefinition def,
        int             progress,
        boolean         claimed) {

    public boolean isCompleted() {
        return progress >= def.targetAmount();
    }

    public QuestInstance withProgress(int newProgress) {
        return new QuestInstance(playerUuid, dateKey, questIndex, def,
                Math.min(newProgress, def.targetAmount()), claimed);
    }

    public QuestInstance markClaimed() {
        return new QuestInstance(playerUuid, dateKey, questIndex, def, progress, true);
    }
}
