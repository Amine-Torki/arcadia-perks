package com.arcadia.pets.server;

import com.arcadia.lib.data.DatabaseManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.*;
import java.sql.*;
import java.util.*;

/**
 * Database-backed storage for pet collections. Used when MySQL is active
 * to provide cross-server persistence. Falls through to SavedData when
 * the database is inactive.
 */
public final class PetCollectionDatabase {

    private static final Logger LOGGER = LogUtils.getLogger();

    private PetCollectionDatabase() {}

    /** Returns true when the database backend should be used for pet storage. */
    public static boolean isActive() {
        return DatabaseManager.isDatabaseActive();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /** Loads the full pet collection for a player from the database. */
    public static List<ItemStack> loadCollection(UUID ownerUuid, HolderLookup.Provider registries) {
        List<ItemStack> result = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT slot_index, item_nbt FROM arcadia_pet_collections WHERE owner_uuid = ? ORDER BY slot_index ASC")) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack stack = deserializeStack(rs.getString("item_nbt"), registries);
                    if (!stack.isEmpty()) result.add(stack);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[ArcadiaPets] Failed to load pet collection for {}", ownerUuid, e);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /** Saves the entire collection for a player, replacing all existing rows. */
    public static void saveCollection(UUID ownerUuid, List<ItemStack> collection, HolderLookup.Provider registries) {
        DatabaseManager.executeAsync(() -> {
            try (Connection conn = DatabaseManager.getConnection()) {
                // Delete existing rows
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM arcadia_pet_collections WHERE owner_uuid = ?")) {
                    del.setString(1, ownerUuid.toString());
                    del.executeUpdate();
                }
                // Re-insert all
                if (!collection.isEmpty()) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO arcadia_pet_collections (owner_uuid, slot_index, item_nbt) VALUES (?, ?, ?)")) {
                        for (int i = 0; i < collection.size(); i++) {
                            ItemStack stack = collection.get(i);
                            if (stack.isEmpty()) continue;
                            ins.setString(1, ownerUuid.toString());
                            ins.setInt(2, i);
                            ins.setString(3, serializeStack(stack, registries));
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("[ArcadiaPets] Failed to save pet collection for {}", ownerUuid, e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Serialization helpers (Base64 compressed NBT, same format as AH)
    // -------------------------------------------------------------------------

    public static String serializeStack(ItemStack stack, HolderLookup.Provider registries) {
        try {
            CompoundTag tag = (CompoundTag) stack.save(registries);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, new DataOutputStream(baos));
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            LOGGER.error("[ArcadiaPets] ItemStack serialization failed", e);
            return "";
        }
    }

    public static ItemStack deserializeStack(String base64, HolderLookup.Provider registries) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            CompoundTag tag = NbtIo.readCompressed(
                    new DataInputStream(new ByteArrayInputStream(bytes)),
                    net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            return ItemStack.parseOptional(registries, tag);
        } catch (Exception e) {
            LOGGER.error("[ArcadiaPets] ItemStack deserialization failed", e);
            return ItemStack.EMPTY;
        }
    }
}
