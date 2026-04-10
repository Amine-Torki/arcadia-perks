package com.arcadia.lib.data;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDataHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ConcurrentHashMap<UUID, PlayerRecord> cache = new ConcurrentHashMap<>();

    private static final long CLAIM_COOLDOWN_MS = 24L * 60 * 60 * 1000;
    private static final long STREAK_RESET_MS = 48L * 60 * 60 * 1000;

    /** Server reference for SavedData access in singleplayer mode. */
    private static net.minecraft.server.MinecraftServer serverRef;

    /** Called from ArcadiaDashboard.onServerAboutToStart to provide server reference. */
    public static void setServer(net.minecraft.server.MinecraftServer server) {
        serverRef = server;
    }

    private PlayerDataHandler() {}

    public record PlayerRecord(UUID uuid, String grade, String particleId, long lastClaim, int streak) {}

    public static PlayerRecord loadPlayer(UUID uuid) {
        PlayerRecord cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }

        // Debug mode: create a Founder-level default record without hitting the DB
        if (com.arcadia.lib.DebugMode.ENABLED) {
            PlayerRecord debugRecord = new PlayerRecord(uuid, "founder", "", 0L, 0);
            cache.put(uuid, debugRecord);
            return debugRecord;
        }

        // In-memory mode (singleplayer): load from world SavedData for persistence across restarts
        if (DatabaseManager.isDebugMode()) {
            long lastClaim = 0L;
            int streak = 0;
            if (serverRef != null) {
                long[] saved = PlayerDataSavedData.getOrCreate(serverRef).get(uuid);
                if (saved != null) { lastClaim = saved[0]; streak = (int) saved[1]; }
            }
            PlayerRecord record = new PlayerRecord(uuid, null, "", lastClaim, streak);
            cache.put(uuid, record);
            return record;
        }

        String uuidStr = uuid.toString();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT uuid, grade, particle_id, last_claim, streak FROM arcadia_prestige_player_data WHERE uuid = ?")) {

            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PlayerRecord record = new PlayerRecord(
                            uuid,
                            rs.getString("grade"),
                            rs.getString("particle_id"),
                            rs.getLong("last_claim"),
                            rs.getInt("streak")
                    );
                    cache.put(uuid, record);
                    return record;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load player data for {}", uuidStr, e);
        }

        // Not in DB, insert default row
        PlayerRecord defaultRecord = new PlayerRecord(uuid, null, "", 0L, 0);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO arcadia_prestige_player_data (uuid, grade, particle_id, last_claim, streak) VALUES (?, ?, ?, ?, ?)")) {

            ps.setString(1, uuidStr);
            ps.setString(2, defaultRecord.grade());
            ps.setString(3, defaultRecord.particleId());
            ps.setLong(4, defaultRecord.lastClaim());
            ps.setInt(5, defaultRecord.streak());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to insert default player data for {}", uuidStr, e);
        }

        cache.put(uuid, defaultRecord);
        return defaultRecord;
    }

    public static void saveParticle(UUID uuid, String particleId) {
        // Always update cache
        cache.computeIfPresent(uuid, (k, old) ->
                new PlayerRecord(old.uuid(), old.grade(), particleId, old.lastClaim(), old.streak()));

        if (DatabaseManager.isDebugMode()) return;

        String uuidStr = uuid.toString();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE arcadia_prestige_player_data SET particle_id = ? WHERE uuid = ?")) {

            ps.setString(1, particleId);
            ps.setString(2, uuidStr);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to save particle for {}", uuidStr, e);
        }

    }

    public static String getParticle(UUID uuid) {
        PlayerRecord record = cache.get(uuid);
        if (record != null) {
            return record.particleId();
        }
        return loadPlayer(uuid).particleId();
    }

    public static int claimDaily(UUID uuid) {
        PlayerRecord record = loadPlayer(uuid);
        long now = System.currentTimeMillis();
        long elapsed = now - record.lastClaim();

        // In debug mode only: skip cooldown for easy testing
        if (!com.arcadia.lib.DebugMode.ENABLED && elapsed < CLAIM_COOLDOWN_MS) {
            return -1;
        }

        int newStreak;
        if (elapsed > STREAK_RESET_MS) {
            newStreak = 1;
        } else {
            newStreak = record.streak() + 1;
        }

        PlayerRecord updated = new PlayerRecord(uuid, record.grade(), record.particleId(), now, newStreak);
        cache.put(uuid, updated);

        if (DatabaseManager.isDebugMode()) {
            // Persist to world SavedData for singleplayer so claims survive restarts
            if (serverRef != null) {
                PlayerDataSavedData.getOrCreate(serverRef).save(uuid, now, newStreak);
            }
            return newStreak;
        }

        String uuidStr = uuid.toString();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE arcadia_prestige_player_data SET last_claim = ?, streak = ? WHERE uuid = ?")) {

            ps.setLong(1, now);
            ps.setInt(2, newStreak);
            ps.setString(3, uuidStr);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to update daily claim for {}", uuidStr, e);
            return -1;
        }

        return newStreak;
    }

    public static int getStreak(UUID uuid) {
        PlayerRecord record = cache.get(uuid);
        if (record != null) {
            return record.streak();
        }
        return loadPlayer(uuid).streak();
    }

    /**
     * Subtracts 24 h from the player's lastClaim timestamp, making the daily reward
     * immediately claimable again. Used by the /pets simday admin command.
     */
    public static void advanceDay(UUID uuid) {
        PlayerRecord record = loadPlayer(uuid);
        long newLastClaim = record.lastClaim() - CLAIM_COOLDOWN_MS;
        PlayerRecord updated = new PlayerRecord(uuid, record.grade(), record.particleId(), newLastClaim, record.streak());
        cache.put(uuid, updated);

        if (DatabaseManager.isDebugMode()) return;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE arcadia_prestige_player_data SET last_claim = ? WHERE uuid = ?")) {
            ps.setLong(1, newLastClaim);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to advance day for {}", uuid, e);
        }
    }

    public static boolean canClaimDaily(UUID uuid) {
        if (com.arcadia.lib.DebugMode.ENABLED) return true;
        long elapsed = System.currentTimeMillis() - loadPlayer(uuid).lastClaim();
        return elapsed >= CLAIM_COOLDOWN_MS;
    }

    public static void registerPet(java.util.UUID petId, String mobType, int rarityOrdinal, int totalStars) {
        if (DatabaseManager.isDebugMode()) return; // arcadia_prestige_pet_registry not used in debug mode

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO arcadia_prestige_pet_registry (pet_id, owner_uuid, mob_type, rarity, total_stars) VALUES (?, ?, ?, ?, ?)")) {

            ps.setString(1, petId.toString());
            ps.setString(2, "");
            ps.setString(3, mobType);
            ps.setInt(4, rarityOrdinal);
            ps.setInt(5, totalStars);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to register pet {}", petId, e);
        }
    }

    public static void updatePetOwner(UUID petId, UUID ownerUuid) {
        if (DatabaseManager.isDebugMode()) return;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE arcadia_prestige_pet_registry SET owner_uuid = ? WHERE pet_id = ?")) {

            ps.setString(1, ownerUuid.toString());
            ps.setString(2, petId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to update pet owner for pet {}", petId, e);
        }
    }

    public static void invalidateCache(UUID uuid) {
        cache.remove(uuid);
    }
}
