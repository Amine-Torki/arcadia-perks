package com.arcadia.prestige.elo;

import com.arcadia.lib.data.DatabaseManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache and public API for the ELO rating system.
 *
 * <p>Ratings are cached after first load. Updates are written through to the DB
 * asynchronously.</p>
 */
public final class EloManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** In-memory cache: uuid → data. */
    private static final Map<UUID, PlayerEloData> CACHE = new ConcurrentHashMap<>();

    private EloManager() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the ELO data for a player. Creates a default entry in the cache
     * (and DB on next save) if not found.
     */
    public static PlayerEloData getOrCreate(UUID uuid) {
        return CACHE.computeIfAbsent(uuid, u -> {
            if (DatabaseManager.isDebugMode()) return PlayerEloData.defaultFor(u);
            PlayerEloData loaded = EloDatabase.load(u);
            return loaded != null ? loaded : PlayerEloData.defaultFor(u);
        });
    }

    /**
     * Updates both players' ratings after a duel, then persists asynchronously.
     *
     * @param winnerUuid    winner's UUID
     * @param loserUuid     loser's UUID
     * @param winnerMobType mob type used as the winner's lead pet (for favorite tracking)
     * @param loserMobType  mob type used as the loser's lead pet
     */
    public static void updateAfterDuel(UUID winnerUuid, UUID loserUuid,
                                       String winnerMobType, String loserMobType) {
        PlayerEloData winnerData = getOrCreate(winnerUuid);
        PlayerEloData loserData  = getOrCreate(loserUuid);

        int newWinnerRating = EloCalculator.newRating(winnerData.rating(), loserData.rating(), true);
        int newLoserRating  = EloCalculator.newRating(loserData.rating(), winnerData.rating(), false);

        // Arcadia Pass bonus: winner gains 5% extra ELO if they hold the pass
        int gain = newWinnerRating - winnerData.rating();
        if (gain > 0 && hasPass(winnerUuid)) {
            newWinnerRating = winnerData.rating() + (int) Math.ceil(gain * 1.05);
        }

        PlayerEloData updatedWinner = winnerData.withWin(newWinnerRating, winnerMobType);
        PlayerEloData updatedLoser  = loserData.withLoss(newLoserRating);

        CACHE.put(winnerUuid, updatedWinner);
        CACHE.put(loserUuid, updatedLoser);

        LOGGER.info("[EloManager] Duel result: {} {} → {}  |  {} {} → {}",
                winnerUuid, winnerData.rating(), newWinnerRating,
                loserUuid,  loserData.rating(),  newLoserRating);

        if (!DatabaseManager.isDebugMode()) {
            final PlayerEloData fw = updatedWinner;
            final PlayerEloData fl = updatedLoser;
            DatabaseManager.executeAsync(() -> {
                EloDatabase.save(fw);
                EloDatabase.save(fl);
            });
        }
    }

    /**
     * Returns the top {@code limit} players by ELO, from cache if populated,
     * otherwise from the DB.
     */
    public static List<PlayerEloData> getLeaderboard(int limit) {
        if (!CACHE.isEmpty()) {
            // Sort cache — sufficient for moderate player counts
            return CACHE.values().stream()
                    .sorted(Comparator.comparingInt(PlayerEloData::rating).reversed())
                    .limit(limit)
                    .toList();
        }
        if (DatabaseManager.isDebugMode()) return List.of();
        return EloDatabase.getLeaderboard(limit);
    }

    /** Clears the in-memory cache (e.g. on server stop). */
    public static void clearCache() {
        CACHE.clear();
    }

    /** Returns true if the player is online and holds the Arcadia Pass permission. */
    private static boolean hasPass(UUID uuid) {
        try {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return false;
            ServerPlayer sp = (ServerPlayer) server.getPlayerList().getPlayer(uuid);
            return sp != null && com.arcadia.prestige.server.LuckPermsHook.hasPass(sp);
        } catch (Exception e) {
            return false;
        }
    }
}
