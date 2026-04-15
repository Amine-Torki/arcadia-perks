package com.arcadia.lib.economy;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Create: Numismatics economy backend using pure reflection.
 * No compile-time dependency on Numismatics — safe to load even if absent.
 *
 * <p>Currency: 1g = 10,000 spurs, 1s = 100 spurs, 1c = 1 spur.</p>
 */
public final class NumismaticsBackend implements EconomyBackend {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static Boolean present;

    // Cached reflection handles
    private static Object bankManager;
    private static Method getOrCreateAccount;
    private static Method getBalance;
    private static Method deductMethod;
    private static Method depositMethod;
    private static Method markDirtyMethod;
    private static Object playerType;

    @Override
    public boolean isAvailable() {
        if (present == null) {
            try {
                Class.forName("dev.ithundxr.createnumismatics.content.coins.CoinItem");
                initReflection();
                present = true;
                LOGGER.info("[ArcadiaLib] Create: Numismatics detected — economy enabled.");
            } catch (Throwable e) {
                present = false;
                LOGGER.info("[ArcadiaLib] Create: Numismatics not found — economy inactive.");
            }
        }
        return present;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initReflection() throws Exception {
        Class<?> numClass = Class.forName("dev.ithundxr.createnumismatics.Numismatics");
        var bankField = numClass.getField("BANK");
        bankManager = bankField.get(null);

        Class typeEnum = Class.forName("dev.ithundxr.createnumismatics.content.backend.BankAccount$Type");
        playerType = Enum.valueOf(typeEnum, "PLAYER");

        getOrCreateAccount = bankManager.getClass().getMethod("getOrCreateAccount", UUID.class, typeEnum);

        Class<?> accountClass = Class.forName("dev.ithundxr.createnumismatics.content.backend.BankAccount");
        getBalance = accountClass.getMethod("getBalance");
        // Numismatics API: deduct(int amount, boolean force) — force=false for normal deduction
        deductMethod = accountClass.getMethod("deduct", int.class, boolean.class);
        depositMethod = accountClass.getMethod("deposit", int.class);
        markDirtyMethod = accountClass.getMethod("markDirty");
    }

    @Override public String getName() { return "Create: Numismatics"; }

    private Object getAccount(ServerPlayer player) throws Exception {
        return getOrCreateAccount.invoke(bankManager, player.getUUID(), playerType);
    }

    @Override
    public long getBalance(ServerPlayer player) {
        if (!isAvailable()) return Long.MAX_VALUE;
        try {
            Object account = getAccount(player);
            return ((Number) getBalance.invoke(account)).longValue();
        } catch (Throwable e) { return 0; }
    }

    @Override
    public boolean deduct(ServerPlayer player, long amount) {
        if (!isAvailable()) return true;
        try {
            Object account = getAccount(player);
            long balance = ((Number) getBalance.invoke(account)).longValue();
            if (balance < amount) return false;
            deductMethod.invoke(account, (int) Math.min(amount, Integer.MAX_VALUE), false);
            markDirtyMethod.invoke(account);
            return true;
        } catch (Throwable e) { return false; }
    }

    @Override
    public void add(ServerPlayer player, long amount) {
        if (!isAvailable()) return;
        try {
            Object account = getAccount(player);
            depositMethod.invoke(account, (int) Math.min(amount, Integer.MAX_VALUE));
            markDirtyMethod.invoke(account);
        } catch (Throwable ignored) {}
    }

    @Override
    public String formatPrice(long spurs) {
        if (spurs <= 0) return "Free";
        long gold   = spurs / 10_000;
        long silver = (spurs % 10_000) / 100;
        long copper = spurs % 100;
        StringBuilder sb = new StringBuilder();
        if (gold > 0)   sb.append(gold).append("g ");
        if (silver > 0) sb.append(silver).append("s ");
        if (copper > 0 || sb.isEmpty()) sb.append(copper).append("c");
        return sb.toString().trim();
    }
}
