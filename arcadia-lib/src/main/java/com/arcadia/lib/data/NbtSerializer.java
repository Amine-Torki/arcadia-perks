package com.arcadia.lib.data;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.*;
import java.util.Base64;

/**
 * Shared NBT serialization utilities for database storage.
 * Converts ItemStack and CompoundTag to/from Base64-encoded compressed NBT.
 * Used by all Arcadia mods for DB persistence — eliminates duplicate code.
 */
public final class NbtSerializer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private NbtSerializer() {}

    // ── ItemStack ───────────────────────────────────────────────────────────

    /** Serializes an ItemStack to a Base64-encoded compressed NBT string. */
    public static String serializeStack(ItemStack stack, HolderLookup.Provider registries) {
        try {
            CompoundTag tag = (CompoundTag) stack.save(registries);
            return serializeTag(tag);
        } catch (Exception e) {
            LOGGER.error("[ArcadiaLib] ItemStack serialization failed", e);
            return "";
        }
    }

    /** Deserializes a Base64-encoded compressed NBT string to an ItemStack. */
    public static ItemStack deserializeStack(String base64, HolderLookup.Provider registries) {
        try {
            CompoundTag tag = deserializeTag(base64);
            if (tag == null) return ItemStack.EMPTY;
            return ItemStack.parseOptional(registries, tag);
        } catch (Exception e) {
            LOGGER.error("[ArcadiaLib] ItemStack deserialization failed", e);
            return ItemStack.EMPTY;
        }
    }

    // ── CompoundTag ─────────────────────────────────────────────────────────

    /** Serializes a CompoundTag to a Base64-encoded compressed NBT string. */
    public static String serializeTag(CompoundTag tag) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, new DataOutputStream(baos));
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            LOGGER.error("[ArcadiaLib] NBT serialization failed", e);
            return "";
        }
    }

    /** Deserializes a Base64-encoded compressed NBT string to a CompoundTag. */
    public static CompoundTag deserializeTag(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return NbtIo.readCompressed(
                    new DataInputStream(new ByteArrayInputStream(bytes)),
                    NbtAccounter.unlimitedHeap());
        } catch (Exception e) {
            LOGGER.error("[ArcadiaLib] NBT deserialization failed", e);
            return null;
        }
    }
}
