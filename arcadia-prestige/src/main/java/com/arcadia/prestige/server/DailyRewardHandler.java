package com.arcadia.prestige.server;

import com.arcadia.pets.PetsModItems;
import com.arcadia.pets.server.PetManager;
import com.arcadia.lib.LibModItems;
import com.arcadia.lib.data.DatabaseManager;
import com.arcadia.lib.data.PlayerDataHandler;
import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetStat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    public static List<ItemStack> buildRewards(int cycleDay, ServerPlayer player) {
        List<ItemStack> rewards = new ArrayList<>();

        switch (cycleDay) {
            case 1  -> rewards.add(treat(4));
            case 2  -> { rewards.add(token(3)); rewards.add(treat(2)); }
            case 3  -> rewards.add(bag(COMMON));
            case 4  -> { rewards.add(snack(2)); rewards.add(token(3)); }
            case 5  -> { rewards.add(bag(UNCOMMON)); rewards.add(token(5)); }             // ★ milestone
            case 6  -> { rewards.add(treat(6)); rewards.add(token(4)); }
            case 7  -> { rewards.add(snack(3)); rewards.add(token(3)); }
            case 8  -> { rewards.add(token(8)); rewards.add(treat(4)); }
            case 9  -> { rewards.add(bag(COMMON)); rewards.add(token(3)); }
            case 10 -> { rewards.add(bag(UNCOMMON)); rewards.add(essence(1)); }           // ★★ milestone
            case 11 -> { rewards.add(snack(3)); rewards.add(treat(4)); }
            case 12 -> { rewards.add(token(12)); rewards.add(treat(3)); }
            case 13 -> { rewards.add(bag(UNCOMMON)); rewards.add(token(3)); }
            case 14 -> { rewards.add(snack(4)); rewards.add(token(5)); }
            case 15 -> { rewards.add(bag(UNCOMMON)); rewards.add(token(8)); rewards.add(essence(1)); } // ★★★ milestone
            case 16 -> { rewards.add(token(12)); rewards.add(treat(5)); }
            case 17 -> { rewards.add(treat(8)); rewards.add(snack(3)); }
            case 18 -> { rewards.add(token(15)); rewards.add(snack(2)); }
            case 19 -> { rewards.add(bag(UNCOMMON)); rewards.add(token(5)); }
            case 20 -> { rewards.add(bag(UNCOMMON)); rewards.add(essence(2)); rewards.add(token(20)); } // ★★★★ milestone
            case 21 -> { rewards.add(essence(1)); rewards.add(snack(4)); }
            case 22 -> { rewards.add(token(15)); rewards.add(treat(8)); }
            case 23 -> { rewards.add(snack(5)); rewards.add(token(5)); }
            case 24 -> { rewards.add(bag(UNCOMMON)); rewards.add(token(8)); }
            default -> rewards.add(treat(3));
        }

        // Luck bonus: active pet's LCK may grant an extra common bag
        PetData activePet = PetManager.getActivePetData(player.getUUID());
        if (activePet != null) {
            int luck = activePet.stats().getOrDefault(PetStat.LUCK, 0);
            if (luck >= 1 && luck <= 5 && new Random().nextInt(100) < LUCK_CHANCES[luck]) {
                rewards.add(bag(COMMON));
                player.sendSystemMessage(
                        Component.translatable("arcadia_prestige.msg.luck_bonus").withStyle(ChatFormatting.AQUA));
            }
        }

        return rewards;
    }

    // -------------------------------------------------------------------------
    // Preview helpers
    // -------------------------------------------------------------------------

    public static Component previewReward(int cycleDay) {
        return switch (cycleDay) {
            case 3, 9        -> Component.translatable("arcadia_prestige.reward.common_bag");
            case 5, 13, 19   -> Component.translatable("arcadia_prestige.reward.rare_bag");
            case 10, 15, 24  -> Component.translatable("arcadia_prestige.reward.epic_bag");
            case 20          -> Component.translatable("arcadia_prestige.reward.legendary_bag");
            case 21          -> Component.translatable("arcadia_prestige.reward.star_essence");
            default          -> cycleDay % 3 == 0
                    ? Component.translatable("arcadia_prestige.reward.token_snack")
                    : Component.translatable("arcadia_prestige.reward.token_treat");
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
        String grade = LuckPermsHook.getGrade(player);
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
        return new ItemStack(switch (tier) {
            case UNCOMMON -> PetsModItems.UNCOMMON_PET_BAG.get();
            case RARE     -> PetsModItems.RARE_PET_BAG.get();
            case EPIC     -> PetsModItems.EPIC_PET_BAG.get();
            default       -> PetsModItems.COMMON_PET_BAG.get();
        }, 1);
    }

    private static ItemStack treat(int count)   { return new ItemStack(PetsModItems.PET_TREAT.get(), count); }
    private static ItemStack snack(int count)   { return new ItemStack(PetsModItems.PET_SNACK.get(), count); }
    private static ItemStack token(int count)   { return new ItemStack(LibModItems.ARCADIA_TOKEN.get(), count); }
    private static ItemStack essence(int count) { return new ItemStack(PetsModItems.STAR_ESSENCE.get(), count); }
}
