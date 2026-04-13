package com.arcadia.pets.server;

import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Server-side persistent storage for per-player pet collections.
 * Pets deposited here are removed from the player's inventory and stored
 * server-side; they are returned to inventory on withdrawal (for trading).
 *
 * <p>Saved to {@code world/data/arcadia_pet_collections.dat}.</p>
 */
public final class PetCollectionSavedData extends SavedData {

    public static final int MAX_PETS  = 108;
    private static final String NAME  = "arcadia_pet_collections";

    private final Map<UUID, List<ItemStack>> collections = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Static access
    // -------------------------------------------------------------------------

    public static PetCollectionSavedData getOrCreate(MinecraftServer server) {
        PetCollectionSavedData data = server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PetCollectionSavedData::new, PetCollectionSavedData::load, null),
                NAME);
        data.serverRef = server;
        return data;
    }

    /** Reference to the server, used for registry access when syncing to DB. */
    private transient MinecraftServer serverRef;

    // -------------------------------------------------------------------------
    // Read API
    // -------------------------------------------------------------------------

    /** Unmodifiable snapshot of the player's collection. */
    public List<ItemStack> getCollection(UUID playerUuid) {
        loadFromDatabase(playerUuid);
        return Collections.unmodifiableList(
                collections.computeIfAbsent(playerUuid, k -> new ArrayList<>()));
    }

    /** Size of the player's collection. */
    public int size(UUID playerUuid) {
        List<ItemStack> col = collections.get(playerUuid);
        return col == null ? 0 : col.size();
    }

    /**
     * Returns the player's collection serialised as a list of {@link CompoundTag}.
     * Each tag represents one pet {@link ItemStack} and can be decoded client-side
     * via {@link PetData#fromTag(CompoundTag)}.
     */
    public List<CompoundTag> getTagsFor(UUID playerUuid) {
        List<ItemStack> col = collections.get(playerUuid);
        if (col == null) return List.of();
        List<CompoundTag> tags = new ArrayList<>(col.size());
        for (ItemStack s : col) {
            if (!(s.getItem() instanceof PetItem)) continue;
            PetData d = PetData.fromStack(s);
            if (d == null) continue;
            tags.add(d.toTag());
        }
        return tags;
    }

    /**
     * Returns the mutable ItemStack stored for the given petId, or
     * {@link ItemStack#EMPTY} if not found. Callers that modify the returned
     * stack must call {@link #setDirty()} afterwards.
     */
    public ItemStack findStack(UUID playerUuid, UUID petId) {
        List<ItemStack> col = collections.get(playerUuid);
        if (col == null) return ItemStack.EMPTY;
        for (ItemStack s : col) {
            if (!(s.getItem() instanceof PetItem)) continue;
            PetData d = PetData.fromStack(s);
            if (d != null && d.petId().equals(petId)) return s;
        }
        return ItemStack.EMPTY;
    }

    // -------------------------------------------------------------------------
    // Write API
    // -------------------------------------------------------------------------

    /**
     * Deposits a copy of {@code stack} into the player's collection.
     *
     * @return {@code true} if deposited; {@code false} if the collection is full.
     */
    public boolean deposit(UUID playerUuid, ItemStack stack) {
        List<ItemStack> col = collections.computeIfAbsent(playerUuid, k -> new ArrayList<>());
        if (col.size() >= MAX_PETS) return false;
        col.add(stack.copy());
        setDirty();
        syncToDatabase(playerUuid);
        return true;
    }

    /**
     * Removes and returns the pet at the given index.
     *
     * @return the removed stack, or {@link ItemStack#EMPTY} if index out of range.
     */
    public ItemStack withdraw(UUID playerUuid, int index) {
        List<ItemStack> col = collections.get(playerUuid);
        if (col == null || index < 0 || index >= col.size()) return ItemStack.EMPTY;
        ItemStack removed = col.remove(index);
        setDirty();
        syncToDatabase(playerUuid);
        return removed;
    }

    /**
     * Removes the pet with the given petId from the collection. Used by fusion to consume input pets.
     *
     * @return {@code true} if a pet was found and removed.
     */
    public boolean removeByPetId(UUID playerUuid, java.util.UUID petId) {
        List<ItemStack> col = collections.get(playerUuid);
        if (col == null) return false;
        for (int i = 0; i < col.size(); i++) {
            ItemStack s = col.get(i);
            if (!(s.getItem() instanceof PetItem)) continue;
            PetData d = PetData.fromStack(s);
            if (d != null && d.petId().equals(petId)) {
                col.remove(i);
                setDirty();
                syncToDatabase(playerUuid);
                return true;
            }
        }
        return false;
    }

    /**
     * Applies an updater to the pet item identified by {@code petId}.
     *
     * @return {@code true} if found and updated.
     */
    public boolean updatePet(UUID playerUuid, UUID petId, UnaryOperator<PetData> updater) {
        List<ItemStack> col = collections.get(playerUuid);
        if (col == null) return false;
        for (ItemStack s : col) {
            if (!(s.getItem() instanceof PetItem)) continue;
            PetData d = PetData.fromStack(s);
            if (d == null || !d.petId().equals(petId)) continue;
            updater.apply(d).applyToStack(s);
            setDirty();
            syncToDatabase(playerUuid);
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Database sync
    // -------------------------------------------------------------------------

    /**
     * When the database is active, asynchronously persists the player's full
     * collection to MySQL for cross-server availability.
     */
    private void syncToDatabase(UUID playerUuid) {
        if (!PetCollectionDatabase.isActive() || serverRef == null) return;
        List<ItemStack> col = collections.get(playerUuid);
        if (col == null) col = List.of();
        // Snapshot the list to avoid concurrent modification during async write
        List<ItemStack> snapshot = List.copyOf(col);
        HolderLookup.Provider registries = serverRef.registryAccess();
        PetCollectionDatabase.saveCollection(playerUuid, snapshot, registries);
    }

    /**
     * Loads a player's collection from the database into the in-memory map.
     * Called on first access when the database is active.
     */
    public void loadFromDatabase(UUID playerUuid) {
        if (!PetCollectionDatabase.isActive() || serverRef == null) return;
        if (collections.containsKey(playerUuid)) return; // already loaded
        List<ItemStack> dbData = PetCollectionDatabase.loadCollection(playerUuid, serverRef.registryAccess());
        if (!dbData.isEmpty()) {
            collections.put(playerUuid, new ArrayList<>(dbData));
        }
    }

    // -------------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider prov) {
        CompoundTag playersTag = new CompoundTag();
        for (var entry : collections.entrySet()) {
            ListTag listTag = new ListTag();
            for (ItemStack stack : entry.getValue()) {
                if (!stack.isEmpty()) {
                    listTag.add(stack.save(prov));
                }
            }
            if (!listTag.isEmpty()) {
                playersTag.put(entry.getKey().toString(), listTag);
            }
        }
        tag.put("players", playersTag);
        return tag;
    }

    private static PetCollectionSavedData load(CompoundTag tag, HolderLookup.Provider prov) {
        PetCollectionSavedData data = new PetCollectionSavedData();
        CompoundTag playersTag = tag.getCompound("players");
        for (String key : playersTag.getAllKeys()) {
            UUID uuid;
            try { uuid = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            ListTag listTag = playersTag.getList(key, Tag.TAG_COMPOUND);
            List<ItemStack> col = new ArrayList<>();
            for (int i = 0; i < listTag.size(); i++) {
                ItemStack s = ItemStack.parseOptional(prov, listTag.getCompound(i));
                if (!s.isEmpty()) col.add(s);
            }
            if (!col.isEmpty()) data.collections.put(uuid, col);
        }
        return data;
    }
}
