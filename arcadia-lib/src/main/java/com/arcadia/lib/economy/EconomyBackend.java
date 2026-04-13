package com.arcadia.lib.economy;

import net.minecraft.server.level.ServerPlayer;

/**
 * Pluggable economy backend interface. The lib provides implementations for
 * supported currency mods. Configure which one to use in arcadia/lib/economy.toml.
 */
public interface EconomyBackend {

    /** Returns the player's balance in the smallest currency unit. */
    long getBalance(ServerPlayer player);

    /** Deducts amount from player. Returns true if successful. */
    boolean deduct(ServerPlayer player, long amount);

    /** Adds amount to player's balance. */
    void add(ServerPlayer player, long amount);

    /** Returns true if this backend's mod is installed and functional. */
    boolean isAvailable();

    /** Display name of this economy backend. */
    String getName();

    /** Formats an amount for display (e.g. "1g 2s 3c" or "150 coins"). */
    String formatPrice(long amount);

    /** No-op backend: unlimited funds, no real deduction. */
    EconomyBackend NONE = new EconomyBackend() {
        @Override public long getBalance(ServerPlayer player) { return Long.MAX_VALUE; }
        @Override public boolean deduct(ServerPlayer player, long amount) { return true; }
        @Override public void add(ServerPlayer player, long amount) {}
        @Override public boolean isAvailable() { return true; }
        @Override public String getName() { return "None (Free)"; }
        @Override public String formatPrice(long amount) {
            if (amount <= 0) return "Free";
            return amount + " coins";
        }
    };
}
