package com.arcadia.ah.auction;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * World-file persistence for AH listings in singleplayer / no-DB mode.
 * Saves to {@code world/data/arcadia_ah_listings.dat}.
 */
public final class AuctionSavedData extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = "arcadia_ah_listings";

    private final List<AuctionListing> listings = new CopyOnWriteArrayList<>();

    public static AuctionSavedData getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(AuctionSavedData::new, AuctionSavedData::load, null), NAME);
    }

    public List<AuctionListing> getListings() { return listings; }

    public void addListing(AuctionListing listing) {
        listings.add(listing);
        setDirty();
    }

    public void removeListing(UUID listingId) {
        listings.removeIf(l -> l.listingId().equals(listingId));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider prov) {
        ListTag list = new ListTag();
        for (AuctionListing l : listings) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", l.listingId().toString());
            entry.putString("server", l.serverId());
            entry.putString("seller", l.sellerUuid().toString());
            entry.putString("sellerName", l.sellerName());
            entry.putString("nbt", l.itemNbt());
            entry.putString("itemName", l.itemDisplayName());
            entry.putString("itemType", l.itemType());
            entry.putString("category", l.category());
            entry.putLong("price", l.price());
            entry.putLong("listed", l.listedAt());
            entry.putLong("expires", l.expiresAt());
            list.add(entry);
        }
        tag.put("listings", list);
        return tag;
    }

    private static AuctionSavedData load(CompoundTag tag, HolderLookup.Provider prov) {
        AuctionSavedData data = new AuctionSavedData();
        ListTag list = tag.getList("listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                data.listings.add(new AuctionListing(
                        UUID.fromString(entry.getString("id")),
                        entry.getString("server"),
                        UUID.fromString(entry.getString("seller")),
                        entry.getString("sellerName"),
                        entry.getString("nbt"),
                        entry.getString("itemName"),
                        entry.getString("itemType"),
                        entry.getString("category"),
                        entry.getLong("price"),
                        entry.getLong("listed"),
                        entry.getLong("expires")
                ));
            } catch (Exception e) {
                LOGGER.warn("[ArcadiaAH] Failed to load listing from SavedData", e);
            }
        }
        return data;
    }
}
