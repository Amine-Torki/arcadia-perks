package com.arcadia.prestige.quest;

import com.arcadia.lib.data.DatabaseManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages daily quest generation, progress tracking, and claim logic.
 *
 * <h3>Quest generation</h3>
 * Three quests (EASY, MEDIUM, HARD) are generated daily, seeded by
 * {@code playerUuid.hashCode() XOR (dateKey.hashCode() << 32)}.
 * This makes each player's quests unique to them but deterministic —
 * the same quests will be regenerated from scratch if the cache is empty
 * (e.g. after a server restart), matching whatever was stored in the DB.
 */
public final class QuestManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Cache: playerUuid → dateKey → QuestInstance[3]. */
    private static final Map<UUID, Map<String, QuestInstance[]>> CACHE = new ConcurrentHashMap<>();

    // Mob pool for KILL_MOB quests
    private static final String[] MOB_POOL = {
            "minecraft:zombie", "minecraft:skeleton", "minecraft:spider",
            "minecraft:creeper", "minecraft:enderman", "minecraft:witch",
            "minecraft:pillager", "minecraft:vindicator", "minecraft:blaze",
            "minecraft:guardian"
    };

    // Block pool for BLOCK_BREAK quests
    private static final String[] BLOCK_POOL = {
            "minecraft:stone", "minecraft:deepslate", "minecraft:oak_log",
            "minecraft:sand", "minecraft:gravel", "minecraft:cobblestone"
    };

    private QuestManager() {}

    // =========================================================================
    // Public API
    // =========================================================================

    public static String todayKey() {
        return LocalDate.now().toString();
    }

    /** Returns today's quests for a player, generating and persisting them if needed. */
    public static QuestInstance[] getOrGenerate(UUID playerUuid, String dateKey) {
        return CACHE
                .computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(dateKey, k -> loadOrGenerate(playerUuid, dateKey));
    }

    /**
     * Records progress for all matching quests of a player.
     *
     * @return true if at least one quest was updated
     */
    public static boolean trackProgress(UUID playerUuid, String typeId, String context, int amount) {
        String dateKey = todayKey();
        QuestInstance[] quests = getOrGenerate(playerUuid, dateKey);
        boolean any = false;
        for (int i = 0; i < quests.length; i++) {
            QuestInstance q = quests[i];
            if (q.claimed() || q.isCompleted()) continue;
            QuestDefinition def = q.def();
            if (!def.type().name().equals(typeId)) continue;
            // For context-specific quests the context must match exactly
            if (!def.context().isEmpty() && !def.context().equals(context)) continue;

            int newProg = q.progress() + amount;
            quests[i] = q.withProgress(newProg);
            any = true;
            if (!DatabaseManager.isDebugMode()) {
                final int fi = i;
                final int fp = quests[i].progress();
                DatabaseManager.executeAsync(() -> QuestDatabase.updateProgress(playerUuid, dateKey, fi, fp));
            }
        }
        return any;
    }

    /**
     * Attempts to claim a completed quest and dispatch rewards.
     *
     * @return true on success; false if not found, not completed, or already claimed
     */
    public static boolean claimQuest(ServerPlayer player, String dateKey, int questIndex) {
        UUID uuid = player.getUUID();
        Map<String, QuestInstance[]> dayMap = CACHE.get(uuid);
        if (dayMap == null) return false;
        QuestInstance[] quests = dayMap.get(dateKey);
        if (quests == null || questIndex < 0 || questIndex >= quests.length) return false;

        QuestInstance q = quests[questIndex];
        if (q.claimed() || !q.isCompleted()) return false;

        quests[questIndex] = q.markClaimed();
        if (!DatabaseManager.isDebugMode()) {
            DatabaseManager.executeAsync(() -> QuestDatabase.markClaimed(uuid, dateKey, questIndex));
        }

        grantRewards(player, q.def());
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§6[Quest] §aClaimed: §f" + q.def().title()
                + " §7(+" + q.def().rewardCoins() + " coins"
                + (q.def().rewardEssence() > 0 ? ", +" + q.def().rewardEssence() + " ✦" : "")
                + ")"));
        return true;
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private static QuestInstance[] loadOrGenerate(UUID playerUuid, String dateKey) {
        if (!DatabaseManager.isDebugMode()) {
            QuestInstance[] loaded = QuestDatabase.loadInstances(playerUuid, dateKey);
            if (loaded != null) return loaded;
        }
        return generate(playerUuid, dateKey);
    }

    private static QuestInstance[] generate(UUID playerUuid, String dateKey) {
        long seed = ((long) playerUuid.hashCode()) ^ ((long) dateKey.hashCode() << 32);
        Random rng = new Random(seed);

        // Shuffle quest types to assign one (non-repeating) type per difficulty slot
        List<QuestType> typePool = new ArrayList<>(Arrays.asList(QuestType.values()));
        Collections.shuffle(typePool, rng);

        QuestDifficulty[] diffs = QuestDifficulty.values(); // EASY, MEDIUM, HARD
        QuestInstance[] instances = new QuestInstance[3];

        for (int i = 0; i < 3; i++) {
            QuestType type       = typePool.get(i % typePool.size());
            QuestDifficulty diff = diffs[i];
            int range  = diff.maxTarget - diff.minTarget;
            int target = diff.minTarget + (range > 0 ? rng.nextInt(range + 1) : 0);
            String ctx = type.usesContext() ? pickContext(type, rng) : "";
            QuestDefinition def = new QuestDefinition(
                    type, diff, ctx, target, diff.rewardCoins, diff.rewardEssence);
            instances[i] = new QuestInstance(playerUuid, dateKey, i, def, 0, false);
        }

        if (!DatabaseManager.isDebugMode()) {
            final QuestInstance[] toSave = instances;
            DatabaseManager.executeAsync(
                    () -> QuestDatabase.insertInstances(playerUuid, dateKey, toSave));
        }
        return instances;
    }

    private static String pickContext(QuestType type, Random rng) {
        if (type == QuestType.KILL_MOB)    return MOB_POOL  [rng.nextInt(MOB_POOL.length)];
        if (type == QuestType.BLOCK_BREAK) return BLOCK_POOL[rng.nextInt(BLOCK_POOL.length)];
        return "";
    }

    // ── Reward dispatch (inner classes avoid hard cross-module dependencies) ──

    private static void grantRewards(ServerPlayer player, QuestDefinition def) {
        // Arcadia Pass holders receive a 50% bonus on all quest rewards
        boolean hasPass = com.arcadia.lib.permissions.PermissionService.hasPermission(player, "arcadia.pass");
        int essence = def.rewardEssence();
        int coins   = def.rewardCoins();
        if (hasPass) {
            essence = (int) Math.ceil(essence * 1.5);
            coins   = (int) Math.ceil(coins   * 1.5);
        }

        if (essence > 0 && ModList.get().isLoaded("arcadia_pets")) {
            final int e = essence;
            EssenceReward.give(player, e);
        }
        if (coins > 0 && ModList.get().isLoaded("numismatics")) {
            final int c = coins;
            NumismaticsReward.give(player, c);
        }
    }

    private static final class EssenceReward {
        static void give(ServerPlayer player, int amount) {
            try {
                Class<?> itemsClass = Class.forName("com.arcadia.pets.PetsModItems");
                java.lang.reflect.Field field = itemsClass.getField("STAR_ESSENCE");
                Object reg = field.get(null); // DeferredHolder
                net.minecraft.world.item.Item item =
                        (net.minecraft.world.item.Item) reg.getClass()
                                .getMethod("get").invoke(reg);
                net.minecraft.world.item.ItemStack stack =
                        new net.minecraft.world.item.ItemStack(item, amount);
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            } catch (Exception e) {
                LOGGER.warn("[QuestManager] Could not give Star Essence reward: {}", e.getMessage());
            }
        }
    }

    private static final class NumismaticsReward {
        static void give(ServerPlayer player, int amount) {
            try {
                Class<?> compat = Class.forName("com.arcadia.ah.compat.NumismaticsCompat");
                compat.getMethod("giveCoins",
                        ServerPlayer.class, int.class).invoke(null, player, amount);
            } catch (Exception e) {
                LOGGER.debug("[QuestManager] Numismatics reward skipped: {}", e.getMessage());
            }
        }
    }
}
