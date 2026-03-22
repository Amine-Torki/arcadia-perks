package com.arcadia.ah.auction;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Base64;

/** Converts ItemStack ↔ base64-encoded NBT string for DB storage. */
public final class AuctionItemSerializer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private AuctionItemSerializer() {}

    public static String toBase64(ItemStack stack, net.minecraft.core.HolderLookup.Provider reg) {
        try {
            CompoundTag tag = (CompoundTag) stack.save(reg);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, new DataOutputStream(baos));
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            LOGGER.error("[ArcadiaPrestige] ItemStack serialization failed", e);
            return "";
        }
    }

    public static ItemStack fromBase64(String base64, net.minecraft.core.HolderLookup.Provider reg) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            CompoundTag tag = NbtIo.readCompressed(new DataInputStream(new ByteArrayInputStream(bytes)), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            return ItemStack.parseOptional(reg, tag);
        } catch (Exception e) {
            LOGGER.error("[ArcadiaPrestige] ItemStack deserialization failed", e);
            return ItemStack.EMPTY;
        }
    }
}
