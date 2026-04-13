package com.arcadia.prestige.server;

import com.arcadia.lib.ArcadiaModRegistry;
import com.arcadia.lib.LibModItems;
import com.arcadia.lib.data.DatabaseManager;
import com.arcadia.lib.data.PlayerDataHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Handles snake-path daily reward claims.
 *
 * <p>The path has 24 steps. Milestone positions (days 5, 10, 15, 20) give
 * larger rewards and display as diamond blocks (vs diamond for regular days).
 * After day 24 the path resets automatically; the cycle repeats indefinitely
 * with no additional advantage past the first lap.</p>
 */
public final class DailyRewardHandler {

    /**
     * Dashboard slot indices for the snake path (24 steps, in claim order).
     * Row-by-row snake: down col 1, across top, up col 2, across mid, etc.
     */
    public static final int[] PATH = {
        9, 18, 27, 36, 37, 38, 29, 20, 11, 12,
        13, 22, 31, 40, 41, 42, 33, 24, 15, 16,
        17, 26, 35, 44
    };

    /** Path slot indices that are milestones (diamond block, bigger reward). */
    public static final Set<Integer> MILESTONE_SLOTS = Set.of(37, 12, 41, 16);

    /**
     * The 3 enclosed gift slots per milestone, indexed [milestoneIdx][rankIdx].
     * rankIdx: 0=VIP, 1=VIP+, 2=MVP. Ordered top-to-bottom in the enclosed cell.
     */
    /**
     * Gift slots per milestone, ordered [VIP=0, VIP+=1, MVP=2].
     * Slots are arranged so VIP is closest to the milestone diamond block,
     * reading away from it: VIP → VIP+ → MVP.
     *
     * Milestones 0 & 2 have their diamond at the BOTTOM of the enclosed column,
     * so VIP is at the bottom slot (nearest diamond) and MVP at the top.
     * Milestones 1 & 3 have their diamond at the TOP, so VIP is at the top slot.
     */
    public static final int[][] MILESTONE_GIFT_SLOTS = {
        {28, 19, 10},   // milestone 0 — day 5  (diamond at slot 37, bottom → VIP nearest)
        {21, 30, 39},   // milestone 1 — day 10 (diamond at slot 12, top   → VIP nearest)
        {32, 23, 14},   // milestone 2 — day 15 (diamond at slot 41, bottom → VIP nearest)
        {25, 34, 43},   // milestone 3 — day 20 (diamond at slot 16, top   → VIP nearest)
    };

    /** Path array indices (0-based) where each milestone sits. */
    public static final int[] MILESTONE_PATH_INDICES = {4, 9, 14, 19};

    /** Required gameplay grades per rank slot (index = rankIdx). */
    public static final String[] RANK_GRADES   = {"vip", "vip+", "mvp"};
    /** Display names shown in the UI. */
    public static final String[] RANK_DISPLAY  = {"VIP", "VIP+", "MVP"};

    private static final int CYCLE = 24;
    private static final int[] LUCK_CHANCES = {0, 5, 10, 18, 28, 40};

    private DailyRewardHandler() {}

    // -------------------------------------------------------------------------
    // Milestone gift claims
    // -------------------------------------------------------------------------

    /** In-memory cache for milestone claims: "uuid:cycle" → bitmask. Also used as fallback in debug/singleplayer. */
    private static final java.util.Map<String, Integer> claimsCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static String claimKey(UUID uuid, int cycle) { return uuid + ":" + cycle; }

    /** Returns the bitmask of milestone gifts claimed by the player in the given cycle. Uses in-memory cache to avoid blocking the main thread. */
    public static int getMilestoneClaims(UUID uuid, int cycle) {
        return claimsCache.getOrDefault(claimKey(uuid, cycle), 0);
    }

    /** Pre-loads milestone claims from DB into cache. Call on player login via executeAsync. */
    public static void preloadClaims(UUID uuid, int cycle) {
        if (DatabaseManager.isDebugMode()) return; // already in-memory
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT claims FROM arcadia_prestige_daily_milestone_claims WHERE uuid=? AND cycle=?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, cycle);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) claimsCache.put(claimKey(uuid, cycle), rs.getInt("claims"));
            }
        } catch (SQLException ignored) {}
    }

    private static void saveClaims(UUID uuid, int cycle, int claims) {
        claimsCache.put(claimKey(uuid, cycle), claims);
        if (DatabaseManager.isDebugMode()) return;
        DatabaseManager.executeAsync(() -> {
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO arcadia_prestige_daily_milestone_claims (uuid, cycle, claims) VALUES (?,?,?) " +
                         "ON DUPLICATE KEY UPDATE claims=?")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, cycle);
                ps.setInt(3, claims);
                ps.setInt(4, claims);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }

    /** Bit index for a (milestoneIdx, rankIdx) pair. */
    public static int claimBit(int milestoneIdx, int rankIdx) {
        return 1 << (milestoneIdx * 3 + rankIdx);
    }

    /**
     * Attempts to claim the rank gift at the given milestone for the player.
     * @return true if the reward was granted, false if already claimed or not yet reached.
     */
    public static boolean claimMilestoneGift(ServerPlayer player, int cycle, int milestoneIdx, int rankIdx) {
        UUID uuid = player.getUUID();
        int current = getMilestoneClaims(uuid, cycle);
        int bit = claimBit(milestoneIdx, rankIdx);
        if ((current & bit) != 0) return false;

        saveClaims(uuid, cycle, current | bit);

        List<ItemStack> rewards = buildMilestoneRewards(milestoneIdx, rankIdx);
        for (ItemStack reward : rewards) {
            if (!player.getInventory().add(reward)) player.drop(reward, false);
        }
        player.sendSystemMessage(Component.literal(
                "§6[Daily Milestone] §fYou claimed your §e" + RANK_DISPLAY[rankIdx] + "§f gift!"));
        return true;
    }

    /** Returns the reward items for a given milestone/rank combination. */
    public static List<ItemStack> buildMilestoneRewards(int milestoneIdx, int rankIdx) {
        List<ItemStack> rewards = new ArrayList<>();
        switch (milestoneIdx) {
            case 0 -> { // Day 5
                switch (rankIdx) {
                    case 0 -> rewards.add(treat(6));
                    case 1 -> { rewards.add(snack(2)); rewards.add(token(3)); }
                    case 2 -> { rewards.add(bag(UNCOMMON)); rewards.add(token(5)); }
                }
            }
            case 1 -> { // Day 10
                switch (rankIdx) {
                    case 0 -> { rewards.add(snack(3)); rewards.add(token(5)); }
                    case 1 -> { rewards.add(bag(UNCOMMON)); rewards.add(token(5)); }
                    case 2 -> { rewards.add(bag(UNCOMMON)); rewards.add(token(10)); }
                }
            }
            case 2 -> { // Day 15
                switch (rankIdx) {
                    case 0 -> { rewards.add(snack(5)); rewards.add(token(5)); }
                    case 1 -> { rewards.add(bag(UNCOMMON)); rewards.add(token(5)); }
                    case 2 -> { rewards.add(bag(UNCOMMON)); rewards.add(essence(1)); rewards.add(token(10)); }
                }
            }
            case 3 -> { // Day 20
                switch (rankIdx) {
                    case 0 -> { rewards.add(snack(5)); rewards.add(token(8)); }
                    case 1 -> { rewards.add(bag(UNCOMMON)); rewards.add(token(10)); }
                    case 2 -> { rewards.add(bag(UNCOMMON)); rewards.add(essence(1)); rewards.add(token(15)); }
                }
            }
        }
        return rewards;
    }

    // -------------------------------------------------------------------------
    // Core claim logic
    // -------------------------------------------------------------------------

    /**
     * Attempts to claim the daily reward for the given player.
     *
     * @return {@code true} if the claim succeeded, {@code false} if on cooldown
     */
    public static boolean tryClaim(ServerPlayer player) {
        UUID uuid = player.getUUID();

        int result = PlayerDataHandler.claimDaily(uuid);
        if (result == -1) {
            player.sendSystemMessage(
                    Component.translatable("arcadia_prestige.msg.daily_already_claimed")
                            .withStyle(ChatFormatting.RED));
            return false;
        }

        int streak = result;
        int cycleDay = ((streak - 1) % CYCLE) + 1; // 1-24
        List<ItemStack> rewards = buildRewards(cycleDay, player);

        // Auto-collect any unclaimed milestone gifts before starting a new cycle
        if (streak % CYCLE == 0) {
            int completedCycle = streak / CYCLE - 1; // streak already incremented; cycle is 0-based
            autoCollectMilestones(player, completedCycle);
        }

        // Print chat messages BEFORE giving (Inventory.add modifies stack count → empty → "Air")
        if (streak % CYCLE == 0) {
            player.sendSystemMessage(
                    Component.translatable("arcadia_prestige.msg.daily_cycle_complete").withStyle(ChatFormatting.GOLD));
        }
        player.sendSystemMessage(
                Component.translatable("arcadia_prestige.msg.daily_claimed_headline", streak)
                        .withStyle(ChatFormatting.GOLD));
        for (ItemStack reward : rewards) {
            player.sendSystemMessage(
                    Component.literal("  + ").withStyle(ChatFormatting.GRAY)
                            .append(reward.getHoverName().copy().withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(reward.getCount() > 1 ? " \u00d7" + reward.getCount() : "")
                                    .withStyle(ChatFormatting.GRAY)));
        }

        for (ItemStack reward : rewards) {
            if (!player.getInventory().add(reward)) {
                player.drop(reward, false);
            }
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Reward table — 24-day cycle
    // Milestones: day 5, 10, 15, 20 (diamond block slots)
    // -------------------------------------------------------------------------

    /** Total cycles before looping (15 × 24 = 360 days ≈ 1 year). */
    public static final int MAX_CYCLES = 15;

    /**
     * Builds rewards for a given cycle day (1-24), scaling with the cycle number.
     * Cycle 0 = basic, Cycle 14 = best. After cycle 15 it loops.
     * Rewards include: pet items, vanilla ores, Numismatics coins, tokens.
     */
    public static List<ItemStack> buildRewards(int cycleDay, ServerPlayer player) {
        List<ItemStack> rewards = new ArrayList<>();

        // Determine cycle tier (0-14) for scaling
        int streak = PlayerDataHandler.getStreak(player.getUUID());
        int cycle = Math.min((streak - 1) / CYCLE, MAX_CYCLES - 1);

        // Base reward per day slot
        switch (cycleDay) {
            case 1  -> { rewards.add(treat(4 + cycle)); rewards.add(coins(player, 50 + cycle * 30)); }
            case 2  -> { rewards.add(token(3 + cycle)); rewards.add(new ItemStack(Items.IRON_INGOT, 2 + cycle / 2)); }
            case 3  -> { rewards.add(bag(COMMON)); rewards.add(coins(player, 80 + cycle * 20)); }
            case 4  -> { rewards.add(snack(2 + cycle / 3)); rewards.add(new ItemStack(Items.GOLD_INGOT, 1 + cycle / 3)); }
            case 5  -> { rewards.add(bag(UNCOMMON)); rewards.add(coins(player, 150 + cycle * 50)); rewards.add(token(5 + cycle)); } // ★ milestone
            case 6  -> { rewards.add(treat(6 + cycle)); rewards.add(new ItemStack(Items.LAPIS_LAZULI, 4 + cycle)); }
            case 7  -> { rewards.add(snack(3 + cycle / 3)); rewards.add(new ItemStack(Items.EMERALD, 1 + cycle / 2)); }
            case 8  -> { rewards.add(token(8 + cycle * 2)); rewards.add(coins(player, 120 + cycle * 40)); }
            case 9  -> { rewards.add(bag(COMMON)); rewards.add(new ItemStack(Items.DIAMOND, 1 + cycle / 4)); }
            case 10 -> { rewards.add(bag(UNCOMMON)); rewards.add(essence(1)); rewards.add(coins(player, 300 + cycle * 80)); } // ★★ milestone
            case 11 -> { rewards.add(snack(3 + cycle / 2)); rewards.add(new ItemStack(Items.GOLD_INGOT, 3 + cycle / 2)); }
            case 12 -> { rewards.add(token(12 + cycle * 2)); rewards.add(new ItemStack(Items.EMERALD, 2 + cycle / 3)); }
            case 13 -> { rewards.add(bag(cycle >= 5 ? RARE : UNCOMMON)); rewards.add(coins(player, 200 + cycle * 50)); }
            case 14 -> { rewards.add(snack(4 + cycle / 2)); rewards.add(new ItemStack(Items.DIAMOND, 1 + cycle / 3)); }
            case 15 -> { rewards.add(bag(RARE)); rewards.add(essence(1 + cycle / 5)); rewards.add(coins(player, 500 + cycle * 100)); } // ★★★ milestone
            case 16 -> { rewards.add(token(12 + cycle * 3)); rewards.add(new ItemStack(Items.EMERALD, 3 + cycle / 2)); }
            case 17 -> { rewards.add(treat(8 + cycle)); rewards.add(new ItemStack(Items.DIAMOND, 2 + cycle / 3)); }
            case 18 -> { rewards.add(coins(player, 400 + cycle * 80)); rewards.add(new ItemStack(Items.GOLD_INGOT, 5 + cycle)); }
            case 19 -> { rewards.add(bag(cycle >= 8 ? RARE : UNCOMMON)); rewards.add(token(5 + cycle * 2)); }
            case 20 -> { // ★★★★ milestone — best daily reward
                rewards.add(bag(cycle >= 10 ? EPIC : RARE));
                rewards.add(essence(2 + cycle / 4));
                rewards.add(coins(player, 1000 + cycle * 200));
                if (cycle >= 5) rewards.add(new ItemStack(Items.DIAMOND, 3 + cycle / 2));
                if (cycle >= 10) rewards.add(new ItemStack(Items.NETHERITE_INGOT, 1));
            }
            case 21 -> { rewards.add(essence(1)); rewards.add(new ItemStack(Items.EMERALD, 4 + cycle / 2)); }
            case 22 -> { rewards.add(coins(player, 300 + cycle * 60)); rewards.add(new ItemStack(Items.DIAMOND, 1 + cycle / 3)); }
            case 23 -> { rewards.add(snack(5 + cycle / 2)); rewards.add(token(5 + cycle * 2)); }
            case 24 -> { // Cycle completion bonus
                rewards.add(bag(cycle >= 12 ? EPIC : cycle >= 6 ? RARE : UNCOMMON));
                rewards.add(token(10 + cycle * 3));
                rewards.add(coins(player, 500 + cycle * 150));
                if (cycle >= 8) rewards.add(new ItemStack(Items.NETHERITE_SCRAP, 1 + cycle / 5));
            }
            default -> { rewards.add(treat(3)); rewards.add(coins(player, 30)); }
        }

        return rewards;
    }

    // -------------------------------------------------------------------------
    // Preview helpers
    // -------------------------------------------------------------------------

    public static Component previewReward(int cycleDay) {
        return switch (cycleDay) {
            case 5          -> Component.translatable("arcadia_prestige.reward.milestone_1");
            case 10         -> Component.translatable("arcadia_prestige.reward.milestone_2");
            case 15         -> Component.translatable("arcadia_prestige.reward.milestone_3");
            case 20         -> Component.translatable("arcadia_prestige.reward.milestone_4");
            case 24         -> Component.translatable("arcadia_prestige.reward.cycle_bonus");
            case 3, 9, 13   -> Component.translatable("arcadia_prestige.reward.bag_coins");
            case 17, 22     -> Component.translatable("arcadia_prestige.reward.diamonds_coins");
            case 21         -> Component.translatable("arcadia_prestige.reward.star_essence");
            default         -> Component.translatable("arcadia_prestige.reward.mixed");
        };
    }

    /** Compact 24-cell streak bar for chat messages. */
    public static MutableComponent getStreakBar(int streak) {
        int pos = streak % CYCLE;
        MutableComponent bar = Component.empty();
        for (int i = 1; i <= CYCLE; i++) {
            if (i > 1 && (i - 1) % 6 == 0) bar.append(Component.literal(" ").withStyle(ChatFormatting.RESET));
            if (i <= pos) {
                bar.append(Component.literal("\u25a0").withStyle(ChatFormatting.GOLD));
            } else if (i == pos + 1) {
                bar.append(Component.literal("\u25a0").withStyle(ChatFormatting.YELLOW));
            } else {
                bar.append(Component.literal("\u25a1").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return bar;
    }

    // -------------------------------------------------------------------------
    // Auto-collect unclaimed milestone gifts on cycle completion
    // -------------------------------------------------------------------------

    private static void autoCollectMilestones(ServerPlayer player, int completedCycle) {
        String grade = com.arcadia.lib.permissions.PermissionService.getGrade(player);
        int maxRankIdx = switch (grade) {
            case "mvp"  -> 2;
            case "vip+" -> 1;
            case "vip"  -> 0;
            default     -> -1;
        };
        if (maxRankIdx < 0) return;

        UUID uuid = player.getUUID();
        int claims = getMilestoneClaims(uuid, completedCycle);
        boolean anyCollected = false;

        for (int mi = 0; mi < MILESTONE_GIFT_SLOTS.length; mi++) {
            for (int ri = 0; ri <= maxRankIdx; ri++) {
                int bit = claimBit(mi, ri);
                if ((claims & bit) == 0) {
                    claims |= bit;
                    List<ItemStack> rewards = buildMilestoneRewards(mi, ri);
                    for (ItemStack reward : rewards) {
                        if (!player.getInventory().add(reward)) player.drop(reward, false);
                    }
                    anyCollected = true;
                }
            }
        }

        if (anyCollected) {
            saveClaims(uuid, completedCycle, claims);
            player.sendSystemMessage(
                    Component.translatable("arcadia_prestige.msg.daily_milestone_autocollect").withStyle(ChatFormatting.GOLD));
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static final int COMMON   = 0;
    private static final int UNCOMMON = 1;
    private static final int RARE     = 2;
    private static final int EPIC     = 3;

    private static ItemStack bag(int tier) {
        String itemId = switch (tier) {
            case UNCOMMON -> "uncommon_pet_bag";
            case RARE     -> "rare_pet_bag";
            case EPIC     -> "epic_pet_bag";
            default       -> "common_pet_bag";
        };
        ItemStack stack = ArcadiaModRegistry.createRewardItem(itemId);
        return stack.isEmpty() ? new ItemStack(LibModItems.ARCADIA_TOKEN.get()) : stack;
    }

    private static ItemStack treat(int count) {
        ItemStack s = ArcadiaModRegistry.createRewardItem("pet_treat", count);
        return s.isEmpty() ? new ItemStack(Items.COOKIE, count) : s;
    }
    private static ItemStack snack(int count) {
        ItemStack s = ArcadiaModRegistry.createRewardItem("pet_snack", count);
        return s.isEmpty() ? new ItemStack(Items.GOLDEN_APPLE, count) : s;
    }
    private static ItemStack token(int count) { return new ItemStack(LibModItems.ARCADIA_TOKEN.get(), count); }
    private static ItemStack essence(int count) {
        ItemStack s = ArcadiaModRegistry.createRewardItem("star_essence", count);
        return s.isEmpty() ? new ItemStack(Items.NETHER_STAR, count) : s;
    }

    /**
     * Gives coins via EconomyService. Returns a visual emerald stack as a "receipt"
     * so the player sees something in the reward list. The actual balance is added directly.
     */
    private static ItemStack coins(ServerPlayer player, int amount) {
        com.arcadia.lib.economy.EconomyService.add(player, amount);
        // Return a visual item — the coins are already credited
        ItemStack receipt = new ItemStack(Items.SUNFLOWER, 1);
        receipt.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("+" + com.arcadia.lib.economy.EconomyService.formatPrice(amount))
                        .withStyle(ChatFormatting.GOLD));
        return receipt;
    }
}
