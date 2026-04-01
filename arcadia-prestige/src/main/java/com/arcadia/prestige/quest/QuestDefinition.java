package com.arcadia.prestige.quest;

/**
 * Immutable description of a single daily quest (shared across all players
 * who roll the same quest on the same day).
 */
public record QuestDefinition(
        QuestType      type,
        QuestDifficulty difficulty,
        /** Mob/block resource location for KILL_MOB / BLOCK_BREAK, otherwise "". */
        String context,
        int targetAmount,
        int rewardCoins,
        int rewardEssence) {

    public String title() {
        return switch (type) {
            case KILL_ANY     -> "Monster Hunter";
            case KILL_MOB     -> "Bounty: " + formatContext(context);
            case AH_SELL      -> "Market Mogul";
            case PET_SUMMON   -> "Pet Parade";
            case PET_FEED     -> "Caring Owner";
            case DAILY_CLAIM  -> "Daily Devotion";
            case PET_DUEL_WIN -> "Duel Champion";
            case OPEN_PET_BAG -> "Lucky Bag";
            case BLOCK_BREAK  -> "Miner";
        };
    }

    public String description() {
        return switch (type) {
            case KILL_ANY     -> "Kill " + targetAmount + " mob(s)";
            case KILL_MOB     -> "Kill " + targetAmount + " " + formatContext(context);
            case AH_SELL      -> "List " + targetAmount + " item(s) on the Auction House";
            case PET_SUMMON   -> "Summon a pet " + targetAmount + " time(s)";
            case PET_FEED     -> "Feed a pet " + targetAmount + " time(s)";
            case DAILY_CLAIM  -> "Claim the daily login reward";
            case PET_DUEL_WIN -> "Win " + targetAmount + " pet duel(s)";
            case OPEN_PET_BAG -> "Open " + targetAmount + " pet bag(s)";
            case BLOCK_BREAK  -> "Break " + targetAmount + " " + formatContext(context);
        };
    }

    private static String formatContext(String ctx) {
        if (ctx == null || ctx.isEmpty()) return "?";
        String[] parts = ctx.split(":");
        return parts[parts.length - 1].replace('_', ' ');
    }
}
