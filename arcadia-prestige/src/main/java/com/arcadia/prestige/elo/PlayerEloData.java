package com.arcadia.prestige.elo;

import java.util.UUID;

/**
 * Immutable snapshot of a player's ELO record.
 *
 * @param uuid            player UUID
 * @param rating          current ELO rating
 * @param wins            total duel wins
 * @param losses          total duel losses
 * @param favoriteMobType mob type of the pet used most often as lead in duels
 */
public record PlayerEloData(UUID uuid, int rating, int wins, int losses, String favoriteMobType) {

    /** Blank entry for a player who has never duelled. */
    public static PlayerEloData defaultFor(UUID uuid) {
        return new PlayerEloData(uuid, EloCalculator.DEFAULT_RATING, 0, 0, "");
    }

    public PlayerEloData withWin(int newRating, String mobType) {
        return new PlayerEloData(uuid, newRating, wins + 1, losses, mobType.isEmpty() ? favoriteMobType : mobType);
    }

    public PlayerEloData withLoss(int newRating) {
        return new PlayerEloData(uuid, newRating, wins, losses + 1, favoriteMobType);
    }
}
