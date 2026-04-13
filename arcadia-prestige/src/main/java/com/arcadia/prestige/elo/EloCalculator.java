package com.arcadia.prestige.elo;

/**
 * Standard ELO rating calculation.
 *
 * <ul>
 *   <li>K-factor: 32 for ratings below 1200, 24 below 1600, 16 above.</li>
 *   <li>Rating floor: 800 (cannot drop below).</li>
 *   <li>Default starting rating: 1000.</li>
 * </ul>
 */
public final class EloCalculator {

    public static final int DEFAULT_RATING = 1000;
    public static final int RATING_FLOOR   = 800;

    private EloCalculator() {}

    /**
     * Computes the new rating for a player after a match.
     *
     * @param rating        current rating
     * @param opponentRating opponent's current rating
     * @param won           true if this player won
     * @return updated rating (at least RATING_FLOOR)
     */
    public static int newRating(int rating, int opponentRating, boolean won) {
        double expected = 1.0 / (1.0 + Math.pow(10.0, (opponentRating - rating) / 400.0));
        double score    = won ? 1.0 : 0.0;
        int k = kFactor(rating);
        int updated = (int) Math.round(rating + k * (score - expected));
        return Math.max(RATING_FLOOR, updated);
    }

    private static int kFactor(int rating) {
        if (rating < 1200) return 32;
        if (rating < 1600) return 24;
        return 16;
    }
}
