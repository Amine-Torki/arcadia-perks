package com.arcadia.ah.auction;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Soft dependency wrapper for Create: Numismatics.
 * All methods are safe to call even when the mod is absent —
 * they fall back to returning false / 0 so the AH can still function
 * (just without currency deduction in debug/no-numismatics environments).
 */
public final class NumismaticsCompat {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static Boolean present = null;

    private NumismaticsCompat() {}

    public static boolean isPresent() {
        if (present == null) {
            try {
                Class.forName("com.simibubi.create.content.equipment.armor.BacktankUtil"); // any Numismatics class
                present = false; // Numismatics is separate from Create base
            } catch (ClassNotFoundException ignored) {}
            try {
                Class.forName("dev.ithundxr.createnumismatics.content.coins.CoinItem");
                present = true;
            } catch (ClassNotFoundException ignored) {
                if (present == null) present = false;
            }
            LOGGER.info("[ArcadiaPrestige] Create:Numismatics present: {}", present);
        }
        return present;
    }

    /**
     * Returns the player's balance in spurs, or 0 if Numismatics is absent.
     */
    public static long getBalance(ServerPlayer player) {
        if (!isPresent()) return Long.MAX_VALUE;
        try {
            Class<?> bankManagerClass = Class.forName("dev.ithundxr.createnumismatics.Numismatics");
            Object bank = bankManagerClass.getField("BANK").get(null);
            Class<?> accountTypeClass = Class.forName("dev.ithundxr.createnumismatics.content.backend.BankAccount$Type");
            Object playerType = accountTypeClass.getField("PLAYER").get(null);
            Object account = bank.getClass().getMethod("getOrCreateAccount", java.util.UUID.class, accountTypeClass)
                    .invoke(bank, player.getUUID(), playerType);
            return ((Number) account.getClass().getMethod("getBalance").invoke(account)).longValue();
        } catch (Exception e) {
            LOGGER.warn("[ArcadiaPrestige] NumismaticsCompat.getBalance failed", e);
            return 0;
        }
    }

    /**
     * Deducts {@code amount} spurs from the player.
     * Returns true if successful, false if insufficient funds or mod absent.
     */
    public static boolean deductBalance(ServerPlayer player, long amount) {
        if (!isPresent()) return true;
        try {
            Class<?> bankManagerClass = Class.forName("dev.ithundxr.createnumismatics.Numismatics");
            Object bank = bankManagerClass.getField("BANK").get(null);
            Class<?> accountTypeClass = Class.forName("dev.ithundxr.createnumismatics.content.backend.BankAccount$Type");
            Object playerType = accountTypeClass.getField("PLAYER").get(null);
            Object account = bank.getClass().getMethod("getOrCreateAccount", java.util.UUID.class, accountTypeClass)
                    .invoke(bank, player.getUUID(), playerType);
            boolean ok = (boolean) account.getClass().getMethod("deduct", int.class).invoke(account, (int) amount);
            if (ok) account.getClass().getMethod("markDirty").invoke(account);
            return ok;
        } catch (Exception e) {
            LOGGER.warn("[ArcadiaPrestige] NumismaticsCompat.deductBalance failed", e);
            return false;
        }
    }

    /**
     * Credits {@code amount} spurs to the player.
     */
    public static void addBalance(ServerPlayer player, long amount) {
        if (!isPresent()) return;
        try {
            Class<?> bankManagerClass = Class.forName("dev.ithundxr.createnumismatics.Numismatics");
            Object bank = bankManagerClass.getField("BANK").get(null);
            Class<?> accountTypeClass = Class.forName("dev.ithundxr.createnumismatics.content.backend.BankAccount$Type");
            Object playerType = accountTypeClass.getField("PLAYER").get(null);
            Object account = bank.getClass().getMethod("getOrCreateAccount", java.util.UUID.class, accountTypeClass)
                    .invoke(bank, player.getUUID(), playerType);
            account.getClass().getMethod("deposit", int.class).invoke(account, (int) amount);
            account.getClass().getMethod("markDirty").invoke(account);
        } catch (Exception e) {
            LOGGER.warn("[ArcadiaPrestige] NumismaticsCompat.addBalance failed", e);
        }
    }

    /** Formats a coin amount into a human-readable string (e.g. "1g 2s 3c"). */
    public static String formatPrice(long spurs) {
        if (spurs <= 0) return "Free";
        long gold   = spurs / 10000;
        long silver = (spurs % 10000) / 100;
        long copper = spurs % 100;
        StringBuilder sb = new StringBuilder();
        if (gold   > 0) sb.append(gold).append("g ");
        if (silver > 0) sb.append(silver).append("s ");
        if (copper > 0) sb.append(copper).append("c");
        return sb.toString().trim();
    }
}
