package com.arcadia.prestige.server;

import com.arcadia.lib.dashboard.DashboardTabHandler;
import com.arcadia.lib.data.PlayerDataHandler;
import com.arcadia.prestige.ModMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server-side container menu for the Arcadia Dashboard GUI.
 *
 * Tabs 0 (Cosmetics) and 2 (Daily) are handled directly here.
 * Tabs 1 (Pets) and 3 (AH) are delegated to {@link DashboardTabHandler} instances
 * registered at startup by arcadia-pets and arcadia-ah respectively.
 * If a module is absent its handler is null and the tab shows an empty/unavailable state.
 */
public class DashboardMenu extends AbstractContainerMenu {

    // ── Module handler registration (called from ArcadiaDashboard.onCommonSetup) ──

    private static Supplier<DashboardTabHandler> petsHandlerFactory;
    private static Supplier<DashboardTabHandler> ahHandlerFactory;

    public static void registerPetsHandler(Supplier<DashboardTabHandler> factory) { petsHandlerFactory = factory; }
    public static void registerAhHandler(Supplier<DashboardTabHandler> factory)   { ahHandlerFactory  = factory; }

    // ── State ─────────────────────────────────────────────────────────────────

    private final SimpleContainer dashboardContainer = new SimpleContainer(54);
    private int currentTab    = 0;
    private int cosmeticsPage = 0;
    private final Player player;
    private final DataSlot tabSlot = DataSlot.standalone();
    public int getCurrentTab() { return tabSlot.get(); }

    // Per-instance tab handlers (null when module absent)
    private final DashboardTabHandler petsTab;
    private final DashboardTabHandler ahTab;

    // ── Constructor ───────────────────────────────────────────────────────────

    public DashboardMenu(int containerId, Inventory playerInv) {
        super(ModMenus.DASHBOARD_MENU.get(), containerId);
        this.player = playerInv.player;
        this.petsTab = petsHandlerFactory != null ? petsHandlerFactory.get() : null;
        this.ahTab   = ahHandlerFactory  != null ? ahHandlerFactory.get()   : null;

        // 54 display-only slots
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                final int index = col + row * 9;
                addSlot(new Slot(dashboardContainer, index, 8 + col * 18, 18 + row * 18) {
                    @Override public boolean mayPlace(ItemStack s) { return false; }
                    @Override public boolean mayPickup(Player p)   { return false; }
                });
            }
        }
        // Player inventory — filtered slots that hide PetItem when not on the Pets tab
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new FilteredInventorySlot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
        // Hotbar
        for (int col = 0; col < 9; col++)
            addSlot(new FilteredInventorySlot(playerInv, col, 8 + col * 18, 198));

        addDataSlot(tabSlot);

        if (!player.level().isClientSide()) refreshTab();
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0) { super.clicked(slotId, button, clickType, player); return; }

        if (slotId < 54) {
            if (slotId < 9) {
                if (slotId == 0) switchTab((currentTab + 3) % 4, player);
                else if (slotId == 8) switchTab((currentTab + 1) % 4, player);
                else if (slotId == 4 && player instanceof ServerPlayer sp) {
                    DashboardTabHandler h = handlerForTab(currentTab);
                    if (h != null) h.handleNavBarClick(sp, this::refreshTab);
                }
            } else {
                handleContentClick(slotId, button, player);
            }
            broadcastChanges();
            return;
        }

        // Shift-click from player inventory — delegate to active tab handler
        if (clickType == ClickType.QUICK_MOVE && player instanceof ServerPlayer sp) {
            DashboardTabHandler h = handlerForTab(currentTab);
            if (h != null) {
                Slot slot = this.slots.get(slotId);
                if (h.handleInventoryShiftClick(slot, sp, this::refreshTab)) return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    // ── Tab management ────────────────────────────────────────────────────────

    public void switchTab(int tab, Player player) {
        // Clear AH search when navigating away from the AH tab
        if (this.currentTab == 3 && tab != 3 && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.arcadia.lib.ArcadiaModRegistry.executeServerAction("ah.clear_search", sp);
        }
        this.currentTab = tab;
        this.tabSlot.set(tab);
        if (!player.level().isClientSide()) refreshTab();
    }

    private void handleContentClick(int slotId, int button, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;

        if (currentTab == 2) {
            // Daily tab
            int streak  = PlayerDataHandler.getStreak(sp.getUUID());
            int pathLen = DailyRewardHandler.PATH.length;
            int pathPos = streak % pathLen;
            int cycle   = streak / pathLen;

            outer:
            for (int mi = 0; mi < DailyRewardHandler.MILESTONE_GIFT_SLOTS.length; mi++) {
                for (int ri = 0; ri < 3; ri++) {
                    if (slotId == DailyRewardHandler.MILESTONE_GIFT_SLOTS[mi][ri]) {
                        if (pathPos > DailyRewardHandler.MILESTONE_PATH_INDICES[mi]
                                && com.arcadia.lib.permissions.PermissionService.hasMinimumGrade(sp, DailyRewardHandler.RANK_GRADES[ri])) {
                            DailyRewardHandler.claimMilestoneGift(sp, cycle, mi, ri);
                            refreshTab();
                        }
                        break outer;
                    }
                }
            }
            if (slotId == DailyRewardHandler.PATH[pathPos]) {
                DailyRewardHandler.tryClaim(sp);
                refreshTab();
            }
            return;
        }

        if (currentTab == 0) { handleCosmeticsClick(slotId, sp); return; }

        DashboardTabHandler h = handlerForTab(currentTab);
        if (h != null) h.handleClick(slotId, button, sp, this::refreshTab);
    }

    private DashboardTabHandler handlerForTab(int tab) {
        return switch (tab) {
            case 1 -> petsTab;
            case 3 -> ahTab;
            default -> null;
        };
    }

    // ── AbstractContainerMenu overrides ──────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public void broadcastChanges() { super.broadcastChanges(); }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer sp) {
            if (petsTab != null) petsTab.onClose(sp);
            if (ahTab   != null) ahTab.onClose(sp);
        }
    }

    // ── AH search refresh (called by AhDashboardBridge.notifySearchUpdated) ──

    public void refreshAhTab() {
        currentTab = 3;
        tabSlot.set(3);
        refreshTab();
    }

    // ── Tab rendering (server-side) ───────────────────────────────────────────

    private void refreshTab() {
        for (int i = 0; i < 54; i++) dashboardContainer.setItem(i, ItemStack.EMPTY);
        buildNavBar();
        switch (currentTab) {
            case 0 -> buildCosmeticsTab();
            case 1 -> { if (petsTab != null) petsTab.buildTab(dashboardContainer, (ServerPlayer) player); }
            case 2 -> buildDailyTab();
            case 3 -> { if (ahTab   != null) ahTab.buildTab(dashboardContainer, (ServerPlayer) player); }
        }
        broadcastChanges();
    }

    // ── Shared UI helpers ─────────────────────────────────────────────────────

    private ItemStack glassPane() {
        ItemStack f = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        setName(f, Component.literal(" "));
        return f;
    }

    private ItemStack arrowItem(String label) {
        ItemStack s = new ItemStack(Items.ARROW);
        setName(s, Component.literal(label).withStyle(ChatFormatting.AQUA));
        return s;
    }

    private void buildPageNav(int page, int maxPage) {
        dashboardContainer.setItem(45, arrowItem("◀ Previous"));
        ItemStack pi = new ItemStack(Items.PAPER);
        setName(pi, Component.literal("§fPage " + (page + 1) + " / " + (maxPage + 1)));
        dashboardContainer.setItem(49, pi);
        dashboardContainer.setItem(53, arrowItem("Next ▶"));
    }

    private void buildBottomBar(int page, int maxPage) {
        for (int i = 45; i <= 53; i++) dashboardContainer.setItem(i, glassPane());
        buildPageNav(page, maxPage);
    }

    private void buildNavBar() {
        dashboardContainer.setItem(0, glassPane());
        dashboardContainer.setItem(8, glassPane());

        // Slot 4: delegate to active tab handler, or show empty filler
        ServerPlayer sp = (player instanceof ServerPlayer s) ? s : null;
        DashboardTabHandler activeHandler = handlerForTab(currentTab);
        ItemStack navItem = (activeHandler != null && sp != null) ? activeHandler.getNavBarItem(sp) : ItemStack.EMPTY;
        if (!navItem.isEmpty()) {
            dashboardContainer.setItem(4, navItem);
        } else {
            ItemStack empty = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
            setName(empty, Component.literal(" "));
            dashboardContainer.setItem(4, empty);
        }

        for (int i : new int[]{1, 2, 3, 5, 6, 7}) {
            ItemStack sep = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
            setName(sep, Component.literal(" "));
            dashboardContainer.setItem(i, sep);
        }
    }

    // ── Cosmetics tab ─────────────────────────────────────────────────────────

    private void handleCosmeticsClick(int slotId, ServerPlayer sp) {
        if (slotId == 45) { if (cosmeticsPage > 0) { cosmeticsPage--; refreshTab(); } return; }
        if (slotId == 53) { if (cosmeticsPage < 1)  { cosmeticsPage++; refreshTab(); } return; }

        if (slotId == 51) {
            String cur = PlayerDataHandler.getParticle(sp.getUUID());
            if (cur != null && !cur.isEmpty()) {
                com.arcadia.lib.data.DatabaseManager.executeAsync(
                        () -> PlayerDataHandler.saveParticle(sp.getUUID(), ""));
                ParticleScheduler.broadcastParticleChange(sp, "");
                refreshTab();
            }
            return;
        }

        String[][] pageIds = {
                {"orbit","aura","wings","storm","platform",   "snow","void","dragon","helix","meteor",   "comet","pulsar","binary","nova","galaxy"},
                {"trail","hearts","enchant","flame","stars",  "bubble","ghost","sakura","shockwave","rainbow"}
        };
        String[] ids = pageIds[cosmeticsPage];
        int[] rows = cosmeticsPage == 0 ? new int[]{9, 18, 27} : new int[]{9, 18};
        for (int r = 0; r < rows.length; r++) {
            for (int col = 0; col < 5; col++) {
                if (slotId == rows[r] + 1 + col) {
                    int idx = r * 5 + col;
                    if (idx >= ids.length) return;
                    String pid = ids[idx];
                    if (com.arcadia.lib.permissions.PermissionService.canUseCosmetic(sp, pid)) {
                        com.arcadia.lib.data.DatabaseManager.executeAsync(
                                () -> PlayerDataHandler.saveParticle(sp.getUUID(), pid));
                        ParticleScheduler.broadcastParticleChange(sp, pid);
                        refreshTab();
                    }
                    return;
                }
            }
        }
    }

    private void buildCosmeticsTab() {
        record ParticleInfo(String id, String name, net.minecraft.world.item.Item icon) {}

        List<ParticleInfo> static1 = List.of(
                new ParticleInfo("orbit",    "Orbit",    Items.END_ROD),
                new ParticleInfo("aura",     "Aura",     Items.SOUL_LANTERN),
                new ParticleInfo("wings",    "Wings",    Items.DRAGON_HEAD),
                new ParticleInfo("storm",    "Storm",    Items.LIGHTNING_ROD),
                new ParticleInfo("platform", "Platform", Items.GOLD_BLOCK)
        );
        List<ParticleInfo> static2 = List.of(
                new ParticleInfo("snow",   "Snow",   Items.SNOWBALL),
                new ParticleInfo("void",   "Void",   Items.ENDER_PEARL),
                new ParticleInfo("dragon", "Dragon", Items.DRAGON_BREATH),
                new ParticleInfo("helix",  "Helix",  Items.AMETHYST_SHARD),
                new ParticleInfo("meteor", "Meteor", Items.FIRE_CHARGE)
        );
        List<ParticleInfo> static3 = List.of(
                new ParticleInfo("comet",  "Comet",  Items.SNOWBALL),
                new ParticleInfo("pulsar", "Pulsar", Items.NETHER_STAR),
                new ParticleInfo("binary", "Binary", Items.BLAZE_ROD),
                new ParticleInfo("nova",   "Nova",   Items.FIRE_CHARGE),
                new ParticleInfo("galaxy", "Galaxy", Items.AMETHYST_SHARD)
        );
        List<ParticleInfo> move1 = List.of(
                new ParticleInfo("trail",   "Trail",   Items.LIME_DYE),
                new ParticleInfo("hearts",  "Hearts",  Items.RED_DYE),
                new ParticleInfo("enchant", "Enchant", Items.ENCHANTED_BOOK),
                new ParticleInfo("flame",   "Flame",   Items.BLAZE_POWDER),
                new ParticleInfo("stars",   "Stars",   Items.END_CRYSTAL)
        );
        List<ParticleInfo> move2 = List.of(
                new ParticleInfo("bubble",    "Bubble",    Items.WATER_BUCKET),
                new ParticleInfo("ghost",     "Ghost",     Items.SOUL_LANTERN),
                new ParticleInfo("sakura",    "Sakura",    Items.PINK_DYE),
                new ParticleInfo("shockwave", "Shockwave", Items.LIGHTNING_ROD),
                new ParticleInfo("rainbow",   "Rainbow",   Items.PRISMARINE_CRYSTALS)
        );

        String currentParticle = PlayerDataHandler.getParticle(player.getUUID());

        for (int i = 9; i <= 44; i++) dashboardContainer.setItem(i, ItemStack.EMPTY);
        buildBottomBar(cosmeticsPage, 1);

        java.util.function.BiConsumer<Integer, List<ParticleInfo>> placeRow = (rowStart, effects) -> {
            for (int i = 0; i < effects.size(); i++) {
                ParticleInfo p = effects.get(i);
                dashboardContainer.setItem(rowStart + 1 + i,
                        buildParticleIcon(p.id(), p.name(), p.icon(), currentParticle));
            }
        };

        if (cosmeticsPage == 0) {
            ItemStack h1 = new ItemStack(Items.CYAN_TERRACOTTA);
            setName(h1, Component.translatable("arcadia_prestige.gui.cosmetics.static1").withStyle(ChatFormatting.GRAY));
            dashboardContainer.setItem(9, h1);
            placeRow.accept(9, static1);

            ItemStack h2 = new ItemStack(Items.CYAN_TERRACOTTA);
            setName(h2, Component.translatable("arcadia_prestige.gui.cosmetics.static2").withStyle(ChatFormatting.GRAY));
            dashboardContainer.setItem(18, h2);
            placeRow.accept(18, static2);

            ItemStack h3 = new ItemStack(Items.CYAN_TERRACOTTA);
            setName(h3, Component.translatable("arcadia_prestige.gui.cosmetics.static3").withStyle(ChatFormatting.GRAY));
            dashboardContainer.setItem(27, h3);
            placeRow.accept(27, static3);
        } else {
            ItemStack h1 = new ItemStack(Items.MAGENTA_TERRACOTTA);
            setName(h1, Component.translatable("arcadia_prestige.gui.cosmetics.move1").withStyle(ChatFormatting.GRAY));
            dashboardContainer.setItem(9, h1);
            placeRow.accept(9, move1);

            ItemStack h2 = new ItemStack(Items.MAGENTA_TERRACOTTA);
            setName(h2, Component.translatable("arcadia_prestige.gui.cosmetics.move2").withStyle(ChatFormatting.GRAY));
            dashboardContainer.setItem(18, h2);
            placeRow.accept(18, move2);
        }

        ItemStack preview = new ItemStack(Items.SPYGLASS);
        setName(preview, Component.translatable("arcadia_prestige.gui.preview.btn").withStyle(ChatFormatting.AQUA));
        setLore(preview, List.of(Component.translatable("arcadia_prestige.gui.preview.lore").withStyle(ChatFormatting.GRAY)));
        dashboardContainer.setItem(47, preview);

        if (currentParticle != null && !currentParticle.isEmpty()) {
            ItemStack deactivate = new ItemStack(Items.BARRIER);
            setName(deactivate, Component.translatable("arcadia_prestige.gui.cosmetics.remove").withStyle(ChatFormatting.RED));
            dashboardContainer.setItem(51, deactivate);
        }
    }

    private ItemStack buildParticleIcon(String id, String name, net.minecraft.world.item.Item icon, String currentParticle) {
        boolean unlocked = com.arcadia.lib.permissions.PermissionService.canUseCosmetic(player, id);
        boolean selected  = id.equals(currentParticle);
        String  tierLabel = CosmeticPermissionScanner.getTierLabel(id); // null = individually sold

        ItemStack stack;
        if (unlocked) {
            stack = new ItemStack(icon);
            setName(stack, Component.literal(name).withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.AQUA));
            List<Component> lore = new ArrayList<>();
            if (tierLabel != null) {
                lore.add(Component.literal("Requires: " + tierLabel).withStyle(ChatFormatting.DARK_AQUA));
            } else {
                lore.add(Component.literal("\u2736 Special").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (selected) lore.add(Component.translatable("arcadia_prestige.gui.cosmetics.active").withStyle(ChatFormatting.GREEN));
            else          lore.add(Component.translatable("arcadia_prestige.gui.cosmetics.activate_hint").withStyle(ChatFormatting.GRAY));
            setLore(stack, lore);
            if (selected) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else {
            stack = new ItemStack(Items.BARRIER);
            setName(stack, Component.literal("\u2717 " + name).withStyle(ChatFormatting.RED));
            String reqLine = tierLabel != null ? "Requires: " + tierLabel : "No access";
            setLore(stack, List.of(Component.literal(reqLine).withStyle(ChatFormatting.DARK_RED)));
        }
        return stack;
    }

    // ── Daily tab ─────────────────────────────────────────────────────────────

    private void buildDailyTab() {
        int streak   = PlayerDataHandler.getStreak(player.getUUID());
        int pathLen  = DailyRewardHandler.PATH.length;
        int pagePos  = streak % pathLen;
        int page     = streak / pathLen;
        boolean canClaim = PlayerDataHandler.canClaimDaily(player.getUUID());

        ItemStack bg = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        setName(bg, Component.literal(" "));
        for (int s = 9; s < 54; s++) dashboardContainer.setItem(s, bg.copy());

        for (int i = 0; i < pathLen; i++) {
            int slot        = DailyRewardHandler.PATH[i];
            boolean isMilestone = DailyRewardHandler.MILESTONE_SLOTS.contains(slot);
            int cycleDay    = i + 1;
            int totalDay    = page * pathLen + cycleDay;

            ItemStack item;
            if (i < pagePos) {
                item = new ItemStack(isMilestone ? Items.COAL_BLOCK : Items.COAL);
                setName(item, Component.literal("Day " + totalDay).withStyle(ChatFormatting.DARK_GRAY));
                setLore(item, List.of(Component.literal("\u2713 Claimed").withStyle(ChatFormatting.GREEN)));
            } else if (i == pagePos) {
                item = new ItemStack(isMilestone ? Items.DIAMOND_BLOCK : Items.DIAMOND);
                ChatFormatting nameColor = isMilestone ? ChatFormatting.GOLD : ChatFormatting.AQUA;
                String star = isMilestone ? " \u2605" : "";
                setName(item, Component.literal("Day " + totalDay + star).withStyle(nameColor));
                List<Component> lore = new ArrayList<>();
                lore.add(DailyRewardHandler.previewReward(cycleDay).copy().withStyle(ChatFormatting.WHITE));
                lore.add(canClaim
                        ? Component.literal("\u25ba Click to claim!").withStyle(ChatFormatting.YELLOW)
                        : Component.literal("\u23f1 Come back tomorrow").withStyle(ChatFormatting.GRAY));
                setLore(item, lore);
                if (canClaim) item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            } else {
                item = new ItemStack(Items.WHITE_STAINED_GLASS_PANE);
                setName(item, Component.literal("Day " + totalDay).withStyle(ChatFormatting.WHITE));
                setLore(item, List.of(Component.literal("\uD83D\uDD12 Not yet reached").withStyle(ChatFormatting.GRAY)));
            }
            dashboardContainer.setItem(slot, item);
        }

        int cycle = page;
        int milestoneClaims = DailyRewardHandler.getMilestoneClaims(player.getUUID(), cycle);
        for (int mi = 0; mi < DailyRewardHandler.MILESTONE_GIFT_SLOTS.length; mi++) {
            boolean milestoneReached = pagePos > DailyRewardHandler.MILESTONE_PATH_INDICES[mi];
            for (int ri = 0; ri < 3; ri++) {
                int giftSlot         = DailyRewardHandler.MILESTONE_GIFT_SLOTS[mi][ri];
                String requiredGrade = DailyRewardHandler.RANK_GRADES[ri];
                String displayRank   = DailyRewardHandler.RANK_DISPLAY[ri];
                boolean hasGrade = com.arcadia.lib.permissions.PermissionService.hasMinimumGrade(player, requiredGrade);
                boolean claimed  = (milestoneClaims & DailyRewardHandler.claimBit(mi, ri)) != 0;
                ChatFormatting rankColor = ri == 0 ? ChatFormatting.GOLD : ri == 1 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA;
                net.minecraft.world.item.Item rankIcon = ri == 0 ? Items.GOLD_INGOT : ri == 1 ? Items.AMETHYST_SHARD : Items.NETHER_STAR;

                ItemStack giftItem;
                if (claimed) {
                    giftItem = new ItemStack(Items.LIGHT_GRAY_DYE);
                    setName(giftItem, Component.literal(displayRank + " Gift \u2713").withStyle(ChatFormatting.GRAY));
                    setLore(giftItem, List.of(Component.literal("\u2713 Claimed").withStyle(ChatFormatting.DARK_GRAY)));
                } else if (!milestoneReached) {
                    giftItem = new ItemStack(rankIcon);
                    setName(giftItem, Component.literal(displayRank + " Gift").withStyle(ChatFormatting.DARK_GRAY));
                    setLore(giftItem, List.of(Component.literal("\uD83D\uDD12 Reach day " +
                            (DailyRewardHandler.MILESTONE_PATH_INDICES[mi] + 1) + " first").withStyle(ChatFormatting.DARK_GRAY)));
                } else if (!hasGrade) {
                    giftItem = new ItemStack(rankIcon);
                    setName(giftItem, Component.literal(displayRank + " Gift \uD83D\uDD12").withStyle(ChatFormatting.GRAY));
                    setLore(giftItem, List.of(Component.literal("Requires " + displayRank).withStyle(ChatFormatting.RED)));
                } else {
                    giftItem = new ItemStack(rankIcon);
                    setName(giftItem, Component.literal(displayRank + " Gift \u2605").withStyle(rankColor));
                    List<Component> lore = new ArrayList<>();
                    for (ItemStack r : DailyRewardHandler.buildMilestoneRewards(mi, ri)) {
                        lore.add(Component.literal("  + ").withStyle(ChatFormatting.GRAY)
                                .append(r.getHoverName().copy().withStyle(ChatFormatting.WHITE))
                                .append(r.getCount() > 1
                                        ? Component.literal(" \u00d7" + r.getCount()).withStyle(ChatFormatting.GRAY)
                                        : Component.empty()));
                    }
                    lore.add(Component.literal("\u25ba Click to claim!").withStyle(ChatFormatting.YELLOW));
                    setLore(giftItem, lore);
                    giftItem.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                }
                dashboardContainer.setItem(giftSlot, giftItem);
            }
        }

        ItemStack streakItem = new ItemStack(Items.EXPERIENCE_BOTTLE);
        setName(streakItem, Component.literal("\u2746 Streak: " + streak).withStyle(ChatFormatting.GOLD));
        setLore(streakItem, List.of(
                Component.literal("Page " + (page + 1) + "  \u2014  Day " + pagePos + "/" + pathLen + " done").withStyle(ChatFormatting.YELLOW),
                Component.literal("Don't miss a day!").withStyle(ChatFormatting.GRAY)));
        dashboardContainer.setItem(49, streakItem);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void setName(ItemStack stack, Component name) { stack.set(DataComponents.CUSTOM_NAME, name); }
    private void setLore(ItemStack stack, List<Component> lore) { stack.set(DataComponents.LORE, new ItemLore(lore)); }

    // ── MenuProvider / static factory ─────────────────────────────────────────

    public static final class Provider implements MenuProvider {
        @Override public Component getDisplayName() {
            return Component.translatable("arcadia_prestige.gui.dashboard").withStyle(ChatFormatting.GOLD);
        }
        @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
            return new DashboardMenu(id, inv);
        }
    }

    public static void openFor(ServerPlayer player) { player.openMenu(new Provider()); }

    public static void openFor(ServerPlayer player, int initialTab) {
        player.openMenu(new ProviderWithTab(initialTab));
    }

    public static final class ProviderWithTab implements MenuProvider {
        private final int initialTab;
        ProviderWithTab(int initialTab) { this.initialTab = initialTab; }
        @Override public Component getDisplayName() {
            return Component.translatable("arcadia_prestige.gui.dashboard").withStyle(ChatFormatting.GOLD);
        }
        @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
            DashboardMenu menu = new DashboardMenu(id, inv);
            menu.switchTab(initialTab, player);
            return menu;
        }
    }

    // ── Filtered slot: hides PetItem when not on Pets tab ─────────────────

    /**
     * Custom inventory slot that hides pet items when the dashboard is not
     * on the Pets tab (tab 1). Prevents pets from cluttering other tabs.
     * Uses isActive() — when false, the slot is not rendered and not interactable.
     */
    private class FilteredInventorySlot extends Slot {
        FilteredInventorySlot(net.minecraft.world.Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public ItemStack getItem() {
            ItemStack real = super.getItem();
            // On Pets tab → show everything
            if (currentTab == 1) return real;
            // On other tabs → hide arcadia_pets items by returning EMPTY
            if (!real.isEmpty() && isPetsModItem(real)) return ItemStack.EMPTY;
            return real;
        }

        @Override
        public boolean allowModification(Player player) {
            // Block interaction with hidden pet items
            if (currentTab != 1 && !super.getItem().isEmpty() && isPetsModItem(super.getItem())) return false;
            return super.allowModification(player);
        }

        @Override
        public boolean mayPickup(Player player) {
            if (currentTab != 1 && !super.getItem().isEmpty() && isPetsModItem(super.getItem())) return false;
            return super.mayPickup(player);
        }

        private static boolean isPetsModItem(ItemStack stack) {
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            return key.getNamespace().equals("arcadia_pets");
        }
    }
}
