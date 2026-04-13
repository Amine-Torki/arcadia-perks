package com.arcadia.lib.economy;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Central economy API for all Arcadia mods. Delegates to the configured
 * {@link EconomyBackend} based on {@code config/arcadia/lib/economy.toml}.
 *
 * <p>Usage: {@code EconomyService.deduct(player, 500)}</p>
 */
public final class EconomyService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static EconomyBackend backend = EconomyBackend.NONE;

    private EconomyService() {}

    /** Initializes the economy backend based on config. Call at server start. */
    public static void init() {
        String provider = EconomyConfig.ACTIVE_PROVIDER.toLowerCase();
        backend = switch (provider) {
            case "numismatics" -> {
                NumismaticsBackend nb = new NumismaticsBackend();
                if (nb.isAvailable()) {
                    LOGGER.info("[ArcadiaLib] Economy: Create: Numismatics");
                    yield nb;
                }
                LOGGER.warn("[ArcadiaLib] Numismatics selected but not installed — falling back to 'none'.");
                yield EconomyBackend.NONE;
            }
            case "items", "item" -> {
                LOGGER.info("[ArcadiaLib] Economy: Item Currency ({})", EconomyConfig.ITEM_ID);
                yield new ItemCurrencyBackend();
            }
            case "none", "free", "disabled" -> {
                LOGGER.info("[ArcadiaLib] Economy: Disabled (free mode)");
                yield EconomyBackend.NONE;
            }
            default -> {
                LOGGER.warn("[ArcadiaLib] Unknown economy provider '{}' — falling back to 'none'.", provider);
                yield EconomyBackend.NONE;
            }
        };
    }

    /** Resets to NONE (call on server stop). */
    public static void shutdown() {
        backend = EconomyBackend.NONE;
    }

    /** Returns the active backend. */
    public static EconomyBackend getBackend() { return backend; }

    // ── Delegated API ───────────────────────────────────────────────────────

    public static long getBalance(ServerPlayer player) {
        return backend.getBalance(player);
    }

    public static boolean deduct(ServerPlayer player, long amount) {
        return backend.deduct(player, amount);
    }

    public static void add(ServerPlayer player, long amount) {
        backend.add(player, amount);
    }

    public static String formatPrice(long amount) {
        return backend.formatPrice(amount);
    }

    public static boolean isAvailable() {
        return backend.isAvailable();
    }

    public static String getProviderName() {
        return backend.getName();
    }
}
