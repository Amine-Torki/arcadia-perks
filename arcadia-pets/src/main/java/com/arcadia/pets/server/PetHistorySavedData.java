package com.arcadia.pets.server;

import com.arcadia.pets.item.PetData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent log of every pet ever revealed from a bag, per player.
 * Capped at {@value MAX_LOGS} entries per player (oldest dropped first).
 * Saved to {@code world/data/arcadia_pet_history.dat}.
 *
 * Admin commands use this to look up and restore lost pets.
 */
public final class PetHistorySavedData extends SavedData {

    public static final int  MAX_LOGS = 100;
    public static final int  PAGE_SIZE = 10;
    private static final String NAME  = "arcadia_pet_history";

    // playerUUID → list of entries, oldest first / newest last
    private final Map<UUID, List<HistoryEntry>> history = new ConcurrentHashMap<>();

    public record HistoryEntry(UUID petId, CompoundTag petTag, long timestamp) {}

    // -------------------------------------------------------------------------
    // Static access
    // -------------------------------------------------------------------------

    public static PetHistorySavedData getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PetHistorySavedData::new, PetHistorySavedData::load, null), NAME);
    }

    // -------------------------------------------------------------------------
    // Write API
    // -------------------------------------------------------------------------

    /**
     * Log a pet at the moment it is revealed from a bag.
     * Call this server-side immediately after the PetData is created.
     */
    public void log(UUID playerUuid, CompoundTag petTag) {
        PetData data = PetData.fromTag(petTag);
        if (data == null) return;
        List<HistoryEntry> entries = history.computeIfAbsent(playerUuid, k -> new ArrayList<>());
        entries.add(new HistoryEntry(data.petId(), petTag.copy(), System.currentTimeMillis()));
        if (entries.size() > MAX_LOGS) entries.remove(0); // drop oldest
        setDirty();
    }

    // -------------------------------------------------------------------------
    // Read API
    // -------------------------------------------------------------------------

    /** Returns `PAGE_SIZE` entries for page N (1-based), newest first. */
    public List<HistoryEntry> getPage(UUID playerUuid, int page) {
        List<HistoryEntry> all = history.getOrDefault(playerUuid, List.of());
        // Reverse view (newest first)
        List<HistoryEntry> reversed = new ArrayList<>(all);
        Collections.reverse(reversed);
        int from = (page - 1) * PAGE_SIZE;
        if (from >= reversed.size()) return List.of();
        return reversed.subList(from, Math.min(from + PAGE_SIZE, reversed.size()));
    }

    /** Total pages for a player's history. */
    public int pageCount(UUID playerUuid) {
        int total = history.getOrDefault(playerUuid, List.of()).size();
        return Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
    }

    /** Total entries for a player. */
    public int totalCount(UUID playerUuid) {
        return history.getOrDefault(playerUuid, List.of()).size();
    }

    /** All entries for a player, newest first. */
    public List<HistoryEntry> getAll(UUID playerUuid) {
        List<HistoryEntry> all = new ArrayList<>(history.getOrDefault(playerUuid, List.of()));
        Collections.reverse(all);
        return all;
    }

    /** Finds a history entry by its pet UUID, or empty if not found. */
    public Optional<HistoryEntry> findByPetId(UUID playerUuid, UUID petId) {
        for (HistoryEntry e : history.getOrDefault(playerUuid, List.of())) {
            if (e.petId().equals(petId)) return Optional.of(e);
        }
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider prov) {
        CompoundTag playersTag = new CompoundTag();
        for (var entry : history.entrySet()) {
            ListTag listTag = new ListTag();
            for (HistoryEntry e : entry.getValue()) {
                CompoundTag eTag = new CompoundTag();
                eTag.putUUID("petId", e.petId());
                eTag.put("petTag", e.petTag().copy());
                eTag.putLong("ts", e.timestamp());
                listTag.add(eTag);
            }
            if (!listTag.isEmpty()) playersTag.put(entry.getKey().toString(), listTag);
        }
        tag.put("players", playersTag);
        return tag;
    }

    private static PetHistorySavedData load(CompoundTag tag, HolderLookup.Provider prov) {
        PetHistorySavedData data = new PetHistorySavedData();
        CompoundTag playersTag = tag.getCompound("players");
        for (String key : playersTag.getAllKeys()) {
            UUID uuid;
            try { uuid = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            ListTag listTag = playersTag.getList(key, Tag.TAG_COMPOUND);
            List<HistoryEntry> entries = new ArrayList<>();
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag eTag = listTag.getCompound(i);
                if (!eTag.hasUUID("petId")) continue;
                entries.add(new HistoryEntry(
                        eTag.getUUID("petId"),
                        eTag.getCompound("petTag"),
                        eTag.getLong("ts")));
            }
            if (!entries.isEmpty()) data.history.put(uuid, entries);
        }
        return data;
    }
}
