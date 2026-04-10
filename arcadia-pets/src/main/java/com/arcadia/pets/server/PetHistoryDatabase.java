package com.arcadia.pets.server;

import com.arcadia.lib.data.DatabaseManager;
import com.arcadia.pets.item.PetData;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import org.slf4j.Logger;

import java.io.*;
import java.sql.*;
import java.util.*;

/**
 * Database-backed storage for pet reveal history. Used when MySQL is active
 * to provide cross-server persistence.
 */
public final class PetHistoryDatabase {

    private static final Logger LOGGER = LogUtils.getLogger();

    private PetHistoryDatabase() {}

    public static boolean isActive() {
        return DatabaseManager.isDatabaseActive();
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /** Logs a pet reveal to the database. */
    public static void log(UUID ownerUuid, UUID petId, CompoundTag petTag, long timestamp) {
        DatabaseManager.executeAsync(() -> {
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO arcadia_pet_history (owner_uuid, pet_id, pet_nbt, created_at) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, ownerUuid.toString());
                ps.setString(2, petId.toString());
                ps.setString(3, serializeTag(petTag));
                ps.setLong(4, timestamp);
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("[ArcadiaPets] Failed to log pet history for {}", ownerUuid, e);
            }
        });

        // Enforce max log cap per player
        DatabaseManager.executeAsync(() -> {
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement count = conn.prepareStatement(
                         "SELECT COUNT(*) FROM arcadia_pet_history WHERE owner_uuid = ?")) {
                count.setString(1, ownerUuid.toString());
                try (ResultSet rs = count.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > PetHistorySavedData.MAX_LOGS) {
                        int excess = rs.getInt(1) - PetHistorySavedData.MAX_LOGS;
                        try (PreparedStatement del = conn.prepareStatement(
                                "DELETE FROM arcadia_pet_history WHERE owner_uuid = ? ORDER BY created_at ASC LIMIT ?")) {
                            del.setString(1, ownerUuid.toString());
                            del.setInt(2, excess);
                            del.executeUpdate();
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("[ArcadiaPets] Failed to trim pet history for {}", ownerUuid, e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /** Loads the full history for a player from the database, newest first. */
    public static List<PetHistorySavedData.HistoryEntry> loadHistory(UUID ownerUuid) {
        List<PetHistorySavedData.HistoryEntry> result = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT pet_id, pet_nbt, created_at FROM arcadia_pet_history WHERE owner_uuid = ? ORDER BY created_at DESC")) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID petId = UUID.fromString(rs.getString("pet_id"));
                    CompoundTag tag = deserializeTag(rs.getString("pet_nbt"));
                    if (tag != null) {
                        result.add(new PetHistorySavedData.HistoryEntry(petId, tag, rs.getLong("created_at")));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[ArcadiaPets] Failed to load pet history for {}", ownerUuid, e);
        }
        return result;
    }

    /** Finds a specific pet entry by its UUID. */
    public static Optional<PetHistorySavedData.HistoryEntry> findByPetId(UUID ownerUuid, UUID petId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT pet_id, pet_nbt, created_at FROM arcadia_pet_history WHERE owner_uuid = ? AND pet_id = ? LIMIT 1")) {
            ps.setString(1, ownerUuid.toString());
            ps.setString(2, petId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    CompoundTag tag = deserializeTag(rs.getString("pet_nbt"));
                    if (tag != null) {
                        return Optional.of(new PetHistorySavedData.HistoryEntry(
                                UUID.fromString(rs.getString("pet_id")), tag, rs.getLong("created_at")));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[ArcadiaPets] Failed to find pet {} in history for {}", petId, ownerUuid, e);
        }
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // NBT serialization (Base64 compressed)
    // -------------------------------------------------------------------------

    private static String serializeTag(CompoundTag tag) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, new DataOutputStream(baos));
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            LOGGER.error("[ArcadiaPets] NBT serialization failed", e);
            return "";
        }
    }

    private static CompoundTag deserializeTag(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return NbtIo.readCompressed(new DataInputStream(new ByteArrayInputStream(bytes)), NbtAccounter.unlimitedHeap());
        } catch (Exception e) {
            LOGGER.error("[ArcadiaPets] NBT deserialization failed", e);
            return null;
        }
    }
}
