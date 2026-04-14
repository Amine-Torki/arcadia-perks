package com.arcadia.prestige.quest;

import com.arcadia.lib.data.DatabaseManager;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * CRUD for the {@code arcadia_prestige_daily_quests} table.
 *
 * <p>All methods are blocking — call from {@link DatabaseManager#executeAsync} or
 * {@link DatabaseManager#supplyAsync}.</p>
 */
public final class QuestDatabase {

    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestDatabase() {}

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Loads the three quest instances for a player on a given day.
     * Returns {@code null} if no row exists yet (quests not yet generated).
     */
    public static QuestInstance[] loadInstances(UUID playerUuid, String dateKey) {
        String sql = "SELECT quest_index, quest_type, difficulty, context, "
                + "target_amount, reward_coins, reward_essence, progress, claimed "
                + "FROM arcadia_prestige_daily_quests "
                + "WHERE uuid = ? AND date_key = ? ORDER BY quest_index ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, dateKey);
            ResultSet rs = ps.executeQuery();

            QuestInstance[] instances = new QuestInstance[3];
            int count = 0;
            while (rs.next()) {
                int idx          = rs.getInt("quest_index");
                QuestType type   = QuestType.valueOf(rs.getString("quest_type"));
                QuestDifficulty diff = QuestDifficulty.valueOf(rs.getString("difficulty"));
                String ctx       = rs.getString("context");
                int target       = rs.getInt("target_amount");
                int coins        = rs.getInt("reward_coins");
                int essence      = rs.getInt("reward_essence");
                int progress     = rs.getInt("progress");
                boolean claimed  = rs.getInt("claimed") == 1;

                QuestDefinition def = new QuestDefinition(type, diff, ctx, target, coins, essence);
                if (idx >= 0 && idx < 3) {
                    instances[idx] = new QuestInstance(playerUuid, dateKey, idx, def, progress, claimed);
                    count++;
                }
            }
            return count == 3 ? instances : null;
        } catch (SQLException e) {
            LOGGER.error("[QuestDatabase] Failed to load quests for {} on {}", playerUuid, dateKey, e);
            return null;
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Inserts the three generated quest rows for a player on a given day. */
    public static void insertInstances(UUID playerUuid, String dateKey, QuestInstance[] instances) {
        String sql = "INSERT IGNORE INTO arcadia_prestige_daily_quests "
                + "(uuid, date_key, quest_index, quest_type, difficulty, context, "
                + "target_amount, reward_coins, reward_essence, progress, claimed) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (QuestInstance qi : instances) {
                QuestDefinition def = qi.def();
                ps.setString(1, playerUuid.toString());
                ps.setString(2, dateKey);
                ps.setInt   (3, qi.questIndex());
                ps.setString(4, def.type().name());
                ps.setString(5, def.difficulty().name());
                ps.setString(6, def.context());
                ps.setInt   (7, def.targetAmount());
                ps.setInt   (8, def.rewardCoins());
                ps.setInt   (9, def.rewardEssence());
                ps.setInt   (10, qi.progress());
                ps.setInt   (11, qi.claimed() ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("[QuestDatabase] Failed to insert quests for {} on {}", playerUuid, dateKey, e);
        }
    }

    /** Updates the progress counter for a single quest row. */
    public static void updateProgress(UUID playerUuid, String dateKey, int questIndex, int progress) {
        String sql = "UPDATE arcadia_prestige_daily_quests SET progress = ? "
                + "WHERE uuid = ? AND date_key = ? AND quest_index = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt   (1, progress);
            ps.setString(2, playerUuid.toString());
            ps.setString(3, dateKey);
            ps.setInt   (4, questIndex);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[QuestDatabase] Failed to update progress for {} quest {}", playerUuid, questIndex, e);
        }
    }

    /** Marks a quest as claimed. */
    public static void markClaimed(UUID playerUuid, String dateKey, int questIndex) {
        String sql = "UPDATE arcadia_prestige_daily_quests SET claimed = 1 "
                + "WHERE uuid = ? AND date_key = ? AND quest_index = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, dateKey);
            ps.setInt   (3, questIndex);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[QuestDatabase] Failed to mark quest claimed for {} index {}", playerUuid, questIndex, e);
        }
    }
}
