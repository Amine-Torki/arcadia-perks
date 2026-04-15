package com.arcadia.pets.elo;

import com.arcadia.lib.data.DatabaseManager;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CRUD operations for the {@code arcadia_duel_elo} table.
 * All methods are synchronous — call from {@link DatabaseManager#executeAsync} if needed.
 */
public final class EloDatabase {

    private static final Logger LOGGER = LogUtils.getLogger();

    private EloDatabase() {}

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Returns the stored ELO data for a player, or null if not found. */
    public static PlayerEloData load(UUID uuid) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT rating, wins, losses, favorite_mob_type FROM arcadia_duel_elo WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerEloData(
                            uuid,
                            rs.getInt("rating"),
                            rs.getInt("wins"),
                            rs.getInt("losses"),
                            rs.getString("favorite_mob_type"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[EloDatabase] Failed to load ELO for {}: {}", uuid, e.getMessage());
        }
        return null;
    }

    /**
     * Returns the top {@code limit} players sorted by rating descending.
     */
    public static List<PlayerEloData> getLeaderboard(int limit) {
        List<PlayerEloData> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT uuid, rating, wins, losses, favorite_mob_type FROM arcadia_duel_elo ORDER BY rating DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PlayerEloData(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getInt("rating"),
                            rs.getInt("wins"),
                            rs.getInt("losses"),
                            rs.getString("favorite_mob_type")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[EloDatabase] Failed to load leaderboard: {}", e.getMessage());
        }
        return list;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Upserts a player's ELO record. */
    public static void save(PlayerEloData data) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO arcadia_duel_elo (uuid, rating, wins, losses, favorite_mob_type)
                     VALUES (?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE
                         rating           = VALUES(rating),
                         wins             = VALUES(wins),
                         losses           = VALUES(losses),
                         favorite_mob_type = VALUES(favorite_mob_type)
                     """)) {
            ps.setString(1, data.uuid().toString());
            ps.setInt(2, data.rating());
            ps.setInt(3, data.wins());
            ps.setInt(4, data.losses());
            ps.setString(5, data.favoriteMobType());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[EloDatabase] Failed to save ELO for {}: {}", data.uuid(), e.getMessage());
        }
    }
}
