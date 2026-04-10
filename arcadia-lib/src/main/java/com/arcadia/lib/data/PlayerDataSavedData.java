package com.arcadia.lib.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World-file persistence for player claim data (lastClaim, streak) when MySQL is inactive
 * (singleplayer / database disabled). Saves to {@code world/data/arcadia_player_data.dat}.
 *
 * <p>When MySQL is active, this class is not used — data comes from the database.</p>
 */
public final class PlayerDataSavedData extends SavedData {

    private static final String NAME = "arcadia_player_data";
    private final Map<UUID, long[]> data = new ConcurrentHashMap<>(); // uuid -> [lastClaim, streak]

    public static PlayerDataSavedData getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PlayerDataSavedData::new, PlayerDataSavedData::load, null), NAME);
    }

    /** Stores claim data for a player. */
    public void save(UUID uuid, long lastClaim, int streak) {
        data.put(uuid, new long[]{lastClaim, streak});
        setDirty();
    }

    /** Returns [lastClaim, streak] or null if no data exists. */
    public long[] get(UUID uuid) {
        return data.get(uuid);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider prov) {
        CompoundTag players = new CompoundTag();
        for (var entry : data.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putLong("lastClaim", entry.getValue()[0]);
            p.putInt("streak", (int) entry.getValue()[1]);
            players.put(entry.getKey().toString(), p);
        }
        tag.put("players", players);
        return tag;
    }

    private static PlayerDataSavedData load(CompoundTag tag, HolderLookup.Provider prov) {
        PlayerDataSavedData saved = new PlayerDataSavedData();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                CompoundTag p = players.getCompound(key);
                saved.data.put(uuid, new long[]{p.getLong("lastClaim"), p.getInt("streak")});
            } catch (IllegalArgumentException ignored) {}
        }
        return saved;
    }
}
