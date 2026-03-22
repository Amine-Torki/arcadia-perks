package com.arcadia.prestige.server;

import com.arcadia.prestige.ModMenus;
import com.arcadia.ah.server.AhLeaderboardMenu;
import com.arcadia.pets.server.FusionMenu;
import com.arcadia.pets.server.PetManager;
import com.arcadia.pets.server.PetCollectionSavedData;
import com.arcadia.ah.auction.AuctionListing;
import com.arcadia.ah.auction.AuctionManager;
import com.arcadia.ah.auction.NumismaticsCompat;
import com.arcadia.lib.data.PlayerDataHandler;
import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetItem;
import com.arcadia.prestige.network.PacketHandler;
import com.arcadia.ah.network.S2COpenAhSearch;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-side container menu for the Arcadia Dashboard GUI.
 * <p>
 * The menu manages 54 display-only slots (the dashboard area) plus the standard
 * player inventory (27 slots) and hotbar (9 slots).  All content is driven
 * server-side: tab navigation, cosmetic selection, pet management, and daily
 * reward claims are handled here and then synced to the client via vanilla
 * container synchronisation.
 */
public class DashboardMenu extends AbstractContainerMenu {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final SimpleContainer dashboardContainer = new SimpleContainer(54);
    private int currentTab      = 0;
    private int petPage         = 0;
    private int cosmeticsPage   = 0;
    private int ahPage          = 0;
    private String ahCategory   = ""; // "" = all
    private boolean ahMyListings = false;
    private final Player player;
    /** Synced to client via DataSlot so DashboardScreen can read the active tab. */
    private final DataSlot tabSlot = DataSlot.standalone();
    public int getCurrentTab() { return tabSlot.get(); }

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new DashboardMenu.  This single constructor is used by both the
     * server (direct instantiation via {@link Provider}) and the client (via
     * {@link ModMenus#DASHBOARD_MENU} factory registration).
     */
    public DashboardMenu(int containerId, Inventory playerInv) {
        super(ModMenus.DASHBOARD_MENU.get(), containerId);
        this.player = playerInv.player;

        // --- 54 display-only dashboard slots ---
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                final int index = col + row * 9;
                addSlot(new Slot(dashboardContainer, index, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack s) {
                        return false;
                    }

                    @Override
                    public boolean mayPickup(Player p) {
                        return false;
                    }
                });
            }
        }

        // --- Player inventory (3 rows × 9 cols) ---
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }

        // --- Hotbar (9 slots) ---
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }

        // Sync current tab to client
        addDataSlot(tabSlot);

        // Populate on the server side only
        if (!player.level().isClientSide()) {
            refreshTab();
        }
    }

    // -------------------------------------------------------------------------
    // Click handling
    // -------------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0) {
            super.clicked(slotId, button, clickType, player);
            return;
        }

        if (slotId < 54) {
            // Dashboard area — fully intercepted
            if (slotId < 9) {
                // Top bar: arrows + active pet
                if (slotId == 0) switchTab((currentTab + 3) % 4, player); // prev tab
                else if (slotId == 8) switchTab((currentTab + 1) % 4, player); // next tab
                else if (slotId == 4 && player instanceof ServerPlayer sp) {
                    // Click active pet → open its panel
                    UUID designated = PetManager.getDesignatedPetId(sp.getUUID());
                    if (designated != null) {
                        ItemStack petStack = PetManager.findPetStackAnywhere(sp, designated);
                        if (!petStack.isEmpty()) PetManager.openPanelFor(sp, petStack);
                    }
                }
            } else {
                handleContentClick(slotId, button, player);
            }
            broadcastChanges();
            return;
        }

        // Player inventory: shift-click a pet item to deposit it into the collection
        if (clickType == ClickType.QUICK_MOVE && player instanceof ServerPlayer sp && currentTab == 1) {
            Slot slot = this.slots.get(slotId);
            ItemStack clickedStack = slot.getItem();
            if (clickedStack.getItem() instanceof PetItem) {
                PetData pd = PetData.fromStack(clickedStack);
                if (pd != null) {
                    // Block depositing equipped pet
                    PetData eq = PetManager.getActivePetData(sp);
                    if (eq == null) eq = PetManager.getPocketPetData(sp.getUUID());
                    if (eq != null && eq.petId().equals(pd.petId())) {
                        sp.sendSystemMessage(Component.translatable("arcadia_prestige.gui.pets.recall_first")
                                .withStyle(ChatFormatting.RED));
                        return;
                    }
                    if (sp.getServer() != null) {
                        PetCollectionSavedData col = PetCollectionSavedData.getOrCreate(sp.getServer());
                        if (col.size(sp.getUUID()) >= PetCollectionSavedData.MAX_PETS) {
                            sp.sendSystemMessage(Component.translatable("arcadia_prestige.msg.collection_full")
                                    .withStyle(ChatFormatting.RED));
                        } else if (col.deposit(sp.getUUID(), clickedStack)) {
                            slot.set(ItemStack.EMPTY);
                            sp.sendSystemMessage(Component.translatable("arcadia_prestige.msg.pet_deposited")
                                    .withStyle(ChatFormatting.GREEN));
                            refreshTab();
                        }
                    }
                }
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    // -------------------------------------------------------------------------
    // Tab management
    // -------------------------------------------------------------------------

    public void switchTab(int tab, Player player) {
        this.currentTab = tab;
        this.tabSlot.set(tab);
        if (!player.level().isClientSide()) {
            refreshTab();
        }
    }

    private void handleContentClick(int slotId, int button, Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }

        // Daily tab — snake path claim + milestone rank gifts
        if (currentTab == 2) {
            int streak  = PlayerDataHandler.getStreak(sp.getUUID());
            int pathLen = DailyRewardHandler.PATH.length;
            int pathPos = streak % pathLen;
            int cycle   = streak / pathLen;

            // Check milestone gift slot clicks
            outer:
            for (int mi = 0; mi < DailyRewardHandler.MILESTONE_GIFT_SLOTS.length; mi++) {
                for (int ri = 0; ri < 3; ri++) {
                    if (slotId == DailyRewardHandler.MILESTONE_GIFT_SLOTS[mi][ri]) {
                        if (pathPos > DailyRewardHandler.MILESTONE_PATH_INDICES[mi]
                                && LuckPermsHook.hasMinimumGrade(sp, DailyRewardHandler.RANK_GRADES[ri])) {
                            DailyRewardHandler.claimMilestoneGift(sp, cycle, mi, ri);
                            refreshTab();
                        }
                        break outer;
                    }
                }
            }

            // Regular path claim
            if (slotId == DailyRewardHandler.PATH[pathPos]) {
                DailyRewardHandler.tryClaim(sp);
                refreshTab();
            }
            return;
        }

        switch (currentTab) {
            case 0 -> handleCosmeticsClick(slotId, sp);
            case 1 -> handlePetsClick(slotId, button, sp);
            case 3 -> handleAhClick(slotId, sp);
        }
    }

    private void handleCosmeticsClick(int slotId, ServerPlayer sp) {
        // Page navigation
        if (slotId == 45) { if (cosmeticsPage > 0)       { cosmeticsPage--; refreshTab(); } return; }
        if (slotId == 53) { if (cosmeticsPage < 1)        { cosmeticsPage++; refreshTab(); } return; }

        // Deactivate (slot 51)
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

        // Effect grid: header at rowStart, effects at rowStart+1..+5
        // Page 0 rows: 9, 18, 27  |  Page 1 rows: 9, 18
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
                    if (LuckPermsHook.canUseParticle(sp, pid)) {
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

    private void handlePetsClick(int slotId, int button, ServerPlayer sp) {
        // ── Bottom navigation row (slots 45–53) ──────────────────────────────
        if (slotId == 45) { // ◀ Prev
            if (petPage > 0) { petPage--; refreshTab(); }
            return;
        }
        if (slotId == 53) { // ▶ Next
            int total = getCollection(sp).size();
            if ((petPage + 1) * 36 < total) { petPage++; refreshTab(); }
            return;
        }
        if (slotId == 51) { // Undesignate pet (also despawns if active)
            UUID designatedId = PetManager.getDesignatedPetId(sp.getUUID());
            if (designatedId != null) {
                PetData activePd = PetManager.getActivePetData(sp.getUUID());
                if (activePd == null) activePd = PetManager.getPocketPetData(sp.getUUID());
                if (activePd != null && activePd.petId().equals(designatedId)) {
                    PetManager.despawn(sp);
                }
                PetManager.clearDesignatedPet(sp.getUUID(), designatedId);
                refreshTab();
            }
            return;
        }
        if (slotId == 50) { // Pet Fusion
            sp.closeContainer();
            FusionMenu.openFor(sp);
            return;
        }
        if (slotId == 47 || slotId == 48) {
            // Guide (47) and HUD Settings (48) are opened client-side by DashboardScreen.mouseClicked
            return;
        }

        // ── Pet grid (slots 9–44) ─────────────────────────────────────────────
        if (slotId >= 9 && slotId <= 44) {
            int colIndex = petPage * 36 + (slotId - 9);
            List<ItemStack> col = getCollection(sp);
            if (colIndex < col.size()) {
                if (button == 1) {
                    // Right-click: retrieve to inventory
                    withdrawPetToInventory(sp, colIndex);
                } else {
                    // Left-click: designate as active pet (updates top bar)
                    ItemStack petStack = col.get(colIndex);
                    PetData pd = PetData.fromStack(petStack);
                    if (pd != null) PetManager.setDesignatedPet(sp, pd.petId());
                    refreshTab();
                }
            }
        }
    }

    private List<ItemStack> getCollection(ServerPlayer sp) {
        if (sp.getServer() == null) return List.of();
        return PetCollectionSavedData.getOrCreate(sp.getServer()).getCollection(sp.getUUID());
    }

    private void depositFirstPetFromInventory(ServerPlayer sp) {
        if (sp.getServer() == null) return;
        PetCollectionSavedData data = PetCollectionSavedData.getOrCreate(sp.getServer());
        if (data.size(sp.getUUID()) >= PetCollectionSavedData.MAX_PETS) {
            sp.sendSystemMessage(Component.translatable("arcadia_prestige.msg.collection_full")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (s.getItem() instanceof PetItem) {
                // Block depositing an actively-summoned or pocket pet to prevent desync
                PetData pd = PetData.fromStack(s);
                if (pd != null) {
                    PetData active = PetManager.getActivePetData(sp);
                    if (active == null) active = PetManager.getPocketPetData(sp.getUUID());
                    if (active != null && active.petId().equals(pd.petId())) {
                        sp.sendSystemMessage(Component.translatable("arcadia_prestige.gui.pets.recall_first")
                                .withStyle(ChatFormatting.RED));
                        return;
                    }
                }
                if (data.deposit(sp.getUUID(), s)) {
                    sp.getInventory().removeItem(i, 1);
                    sp.sendSystemMessage(Component.translatable("arcadia_prestige.msg.pet_deposited")
                            .withStyle(ChatFormatting.GREEN));
                    refreshTab();
                }
                return;
            }
        }
        sp.sendSystemMessage(Component.translatable("arcadia_prestige.msg.no_pet_in_inv")
                .withStyle(ChatFormatting.GRAY));
    }

    private void withdrawPetToInventory(ServerPlayer sp, int colIndex) {
        if (sp.getServer() == null) return;
        if (sp.getInventory().getFreeSlot() < 0) {
            sp.sendSystemMessage(Component.translatable("arcadia_prestige.msg.inventory_full")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        PetCollectionSavedData data = PetCollectionSavedData.getOrCreate(sp.getServer());
        ItemStack removed = data.withdraw(sp.getUUID(), colIndex);
        if (!removed.isEmpty()) {
            // If this pet is currently summoned or in pocket mode, dismiss it first
            // to prevent the player from having both an active entity and the item.
            PetData withdrawnPet = PetData.fromStack(removed);
            if (withdrawnPet != null) {
                PetData activePet = PetManager.getActivePetData(sp);
                if (activePet == null) activePet = PetManager.getPocketPetData(sp.getUUID());
                if (activePet != null && activePet.petId().equals(withdrawnPet.petId())) {
                    PetManager.despawn(sp);
                }
                // Clear designation so the withdrawn pet is no longer marked as active
                UUID designatedId = PetManager.getDesignatedPetId(sp.getUUID());
                if (designatedId != null && designatedId.equals(withdrawnPet.petId())) {
                    PetManager.clearDesignatedPet(sp.getUUID(), designatedId);
                }
            }
            sp.getInventory().add(removed);
            sp.sendSystemMessage(Component.translatable("arcadia_prestige.msg.pet_retrieved")
                    .withStyle(ChatFormatting.YELLOW));
            // Clamp page if we removed the last item on this page
            int remaining = data.size(sp.getUUID());
            if (petPage > 0 && petPage * 36 >= remaining) petPage--;
            refreshTab();
        }
    }

    // -------------------------------------------------------------------------
    // AbstractContainerMenu overrides
    // -------------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
    }

    // -------------------------------------------------------------------------
    // Tab rendering (server-side)
    // -------------------------------------------------------------------------

    private void refreshTab() {
        for (int i = 0; i < 54; i++) {
            dashboardContainer.setItem(i, ItemStack.EMPTY);
        }
        buildNavBar();
        switch (currentTab) {
            case 0 -> buildCosmeticsTab();
            case 1 -> buildPetsTab();
            case 2 -> buildDailyTab();
            case 3 -> buildAuctionHouseTab();
        }
        broadcastChanges();
    }

    // -------------------------------------------------------------------------
    // Shared UI helpers
    // -------------------------------------------------------------------------

    /** Returns a named black glass pane used as a visual filler. */
    private ItemStack glassPane() {
        ItemStack f = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        setName(f, Component.literal(" "));
        return f;
    }

    /** Returns a labeled arrow item used for prev/next navigation. */
    private ItemStack arrowItem(String label) {
        ItemStack s = new ItemStack(Items.ARROW);
        setName(s, Component.literal(label).withStyle(ChatFormatting.AQUA));
        return s;
    }

    /**
     * Fills the standard bottom-bar page navigation: slot 45 (◀), 49 (page X/Y), 53 (▶).
     * Tabs can override slot 49 afterward to add custom lore.
     */
    private void buildPageNav(int page, int maxPage) {
        dashboardContainer.setItem(45, arrowItem("◀ Previous"));
        ItemStack pi = new ItemStack(Items.PAPER);
        setName(pi, Component.literal("§fPage " + (page + 1) + " / " + (maxPage + 1)));
        dashboardContainer.setItem(49, pi);
        dashboardContainer.setItem(53, arrowItem("Next ▶"));
    }

    /** Fills all 9 bottom-bar slots (45-53) with glass, then fills prev/page/next via buildPageNav. */
    private void buildBottomBar(int page, int maxPage) {
        for (int i = 45; i <= 53; i++) dashboardContainer.setItem(i, glassPane());
        buildPageNav(page, maxPage);
    }

    private void buildNavBar() {
        // Slots 0 and 8: black glass pane placeholders (tab navigation handled client-side by mini cards)
        dashboardContainer.setItem(0, glassPane());
        dashboardContainer.setItem(8, glassPane());

        // Slot 4: Active (designated) pet display
        ServerPlayer sp = (player instanceof ServerPlayer s) ? s : null;
        UUID designatedId = sp != null ? PetManager.getDesignatedPetId(sp.getUUID()) : null;
        if (designatedId != null && sp != null) {
            ItemStack petStack = PetManager.findPetStackAnywhere(sp, designatedId);
            if (!petStack.isEmpty()) {
                ItemStack display = petStack.copy();
                PetData pd = PetData.fromStack(display);
                if (pd != null) {
                    setName(display, Component.literal("▶ ")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(display.getHoverName()));
                    setLore(display, List.of(
                            Component.translatable("arcadia_prestige.gui.pets.active_label")
                                    .withStyle(ChatFormatting.GOLD),
                            Component.translatable("arcadia_prestige.gui.pets.click_panel")
                                    .withStyle(ChatFormatting.GREEN)));
                }
                dashboardContainer.setItem(4, display);
            } else {
                setEmptyActivePetSlot();
            }
        } else {
            setEmptyActivePetSlot();
        }

        // Filler (title is now rendered dynamically by the client)
        for (int i : new int[]{1, 2, 3, 5, 6, 7}) {
            ItemStack sep = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
            setName(sep, Component.literal(" "));
            dashboardContainer.setItem(i, sep);
        }
    }

    private void setEmptyActivePetSlot() {
        ItemStack empty = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        setName(empty, Component.translatable("arcadia_prestige.gui.pets.no_active")
                .withStyle(ChatFormatting.DARK_GRAY));
        dashboardContainer.setItem(4, empty);
    }

    // -------------------------------------------------------------------------
    // Cosmetics tab
    // -------------------------------------------------------------------------

    private void buildCosmeticsTab() {
        record ParticleInfo(String id, String name, String grade, Item icon) {}

        // Page 0: Static / orbital effects (3 rows)
        List<ParticleInfo> static1 = List.of(
                new ParticleInfo("orbit",    "Orbit",    "vip",     Items.END_ROD),
                new ParticleInfo("aura",     "Aura",     "vip+",    Items.SOUL_LANTERN),
                new ParticleInfo("wings",    "Wings",    "vip+",    Items.DRAGON_HEAD),
                new ParticleInfo("storm",    "Storm",    "founder", Items.LIGHTNING_ROD),
                new ParticleInfo("platform", "Platform", "founder", Items.GOLD_BLOCK)
        );
        List<ParticleInfo> static2 = List.of(
                new ParticleInfo("snow",   "Snow",   "vip",     Items.SNOWBALL),
                new ParticleInfo("void",   "Void",   "vip+",    Items.ENDER_PEARL),
                new ParticleInfo("dragon", "Dragon", "vip+",    Items.DRAGON_BREATH),
                new ParticleInfo("helix",  "Helix",  "founder", Items.AMETHYST_SHARD),
                new ParticleInfo("meteor", "Meteor", "founder", Items.FIRE_CHARGE)
        );
        List<ParticleInfo> static3 = List.of(
                new ParticleInfo("comet",  "Comet",  "vip",     Items.SNOWBALL),
                new ParticleInfo("pulsar", "Pulsar", "vip+",    Items.NETHER_STAR),
                new ParticleInfo("binary", "Binary", "vip+",    Items.BLAZE_ROD),
                new ParticleInfo("nova",   "Nova",   "founder", Items.FIRE_CHARGE),
                new ParticleInfo("galaxy", "Galaxy", "founder", Items.AMETHYST_SHARD)
        );

        // Page 1: Movement effects (2 rows)
        List<ParticleInfo> move1 = List.of(
                new ParticleInfo("trail",   "Trail",   "vip",     Items.LIME_DYE),
                new ParticleInfo("hearts",  "Hearts",  "vip",     Items.RED_DYE),
                new ParticleInfo("enchant", "Enchant", "vip+",    Items.ENCHANTED_BOOK),
                new ParticleInfo("flame",   "Flame",   "vip+",    Items.BLAZE_POWDER),
                new ParticleInfo("stars",   "Stars",   "founder", Items.END_CRYSTAL)
        );
        List<ParticleInfo> move2 = List.of(
                new ParticleInfo("bubble",    "Bubble",    "vip",     Items.WATER_BUCKET),
                new ParticleInfo("ghost",     "Ghost",     "vip",     Items.SOUL_LANTERN),
                new ParticleInfo("sakura",    "Sakura",    "vip+",    Items.PINK_DYE),
                new ParticleInfo("shockwave", "Shockwave", "vip+",    Items.LIGHTNING_ROD),
                new ParticleInfo("rainbow",   "Rainbow",   "founder", Items.PRISMARINE_CRYSTALS)
        );

        String currentParticle = PlayerDataHandler.getParticle(player.getUUID());

        // Clear content area (slots 9-44)
        for (int i = 9; i <= 44; i++) dashboardContainer.setItem(i, ItemStack.EMPTY);
        buildBottomBar(cosmeticsPage, 1);

        // Helper: place a category row — header at rowStart, effects at rowStart+1..+5
        java.util.function.BiConsumer<Integer, List<ParticleInfo>> placeRow = (rowStart, effects) -> {
            for (int i = 0; i < effects.size(); i++) {
                ParticleInfo p = effects.get(i);
                dashboardContainer.setItem(rowStart + 1 + i,
                        buildParticleIcon(p.id(), p.name(), p.grade(), p.icon(), currentParticle));
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

        // Bottom bar specifics: slot 47 = preview, slot 51 = deactivate
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

    private ItemStack buildParticleIcon(String id, String name, String grade, Item icon, String currentParticle) {
        boolean unlocked = LuckPermsHook.hasMinimumGrade(player, grade);
        boolean selected = id.equals(currentParticle);

        ItemStack stack;
        if (unlocked) {
            stack = new ItemStack(icon);
            setName(stack, Component.literal(name)
                    .withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.AQUA));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.translatable("arcadia_prestige.gui.cosmetics.grade_req", grade.toUpperCase())
                    .withStyle(ChatFormatting.DARK_AQUA));
            if (selected) {
                lore.add(Component.translatable("arcadia_prestige.gui.cosmetics.active").withStyle(ChatFormatting.GREEN));
            } else {
                lore.add(Component.translatable("arcadia_prestige.gui.cosmetics.activate_hint").withStyle(ChatFormatting.GRAY));
            }
            setLore(stack, lore);
            if (selected) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
        } else {
            stack = new ItemStack(Items.BARRIER);
            setName(stack, Component.literal("\u2717 " + name).withStyle(ChatFormatting.RED));
            setLore(stack, List.of(
                    Component.translatable("arcadia_prestige.gui.cosmetics.grade_req", grade.toUpperCase())
                            .withStyle(ChatFormatting.DARK_RED)));
        }
        return stack;
    }

    // -------------------------------------------------------------------------
    // Pets tab
    // -------------------------------------------------------------------------

    private void buildPetsTab() {
        if (!(player instanceof ServerPlayer sp)) return;
        List<ItemStack> col = getCollection(sp);
        int total    = col.size();
        int maxPage  = total == 0 ? 0 : (total - 1) / 36;
        if (petPage > maxPage) petPage = maxPage;

        UUID designatedId = PetManager.getDesignatedPetId(player.getUUID());
        UUID equippedId   = null;
        PetData equippedData = PetManager.getActivePetData(player.getUUID());
        if (equippedData == null) equippedData = PetManager.getPocketPetData(player.getUUID());
        if (equippedData != null) equippedId = equippedData.petId();
        // ── Pet grid: slots 9–44 (4 rows × 9 = 36 slots per page) ────────────
        for (int i = 0; i < 36; i++) {
            int slot     = 9 + i;
            int colIndex = petPage * 36 + i;
            if (colIndex < total) {
                ItemStack src = col.get(colIndex);
                PetData pd = PetData.fromStack(src);
                ItemStack display = src.copy();
                boolean isDesignated = designatedId != null && pd != null && designatedId.equals(pd.petId());
                boolean isEquipped   = equippedId   != null && pd != null && equippedId.equals(pd.petId());
                if (isEquipped || isDesignated) display.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

                // Append interaction hints only — appendHoverText already shows stats/skills
                List<Component> lore = new java.util.ArrayList<>();
                if (isEquipped) lore.add(Component.translatable("arcadia_prestige.gui.pets.equipped_label")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                else if (isDesignated) lore.add(Component.translatable("arcadia_prestige.gui.pets.active_label")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                lore.add(Component.translatable("arcadia_prestige.gui.pets.set_active")
                        .withStyle(ChatFormatting.AQUA));
                lore.add(Component.translatable("arcadia_prestige.gui.pets.retrieve")
                        .withStyle(ChatFormatting.YELLOW));
                display.set(DataComponents.LORE, new ItemLore(lore));

                dashboardContainer.setItem(slot, display);
            } else {
                ItemStack empty = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
                setName(empty, Component.translatable("arcadia_prestige.gui.pets.empty_slot").withStyle(ChatFormatting.DARK_GRAY));
                setLore(empty, List.of(
                        Component.translatable("arcadia_prestige.gui.pets.shift_deposit")
                                .withStyle(ChatFormatting.GRAY)));
                dashboardContainer.setItem(slot, empty);
            }
        }

        // ── Bottom navigation row (slots 45–53) ──────────────────────────────
        // Layout: [◀ prev][filler][guide][hud settings][page][fuse][recall][filler][▶ next]
        //          45      46      47     48             49    50    51      52      53
        buildBottomBar(petPage, maxPage);

        // 46: empty (cleared by buildBottomBar)

        // 47: Pet Guide
        ItemStack guide = new ItemStack(Items.BOOK);
        setName(guide, Component.translatable("arcadia_prestige.gui.pets.guide_btn").withStyle(ChatFormatting.LIGHT_PURPLE));
        setLore(guide, List.of(Component.translatable("arcadia_prestige.gui.pets.guide_lore").withStyle(ChatFormatting.GRAY)));
        dashboardContainer.setItem(47, guide);

        // 48: HUD Settings
        ItemStack settings = new ItemStack(Items.COMPARATOR);
        setName(settings, Component.translatable("arcadia_prestige.gui.pets.hud_settings_btn").withStyle(ChatFormatting.GRAY));
        setLore(settings, List.of(Component.translatable("arcadia_prestige.gui.pets.hud_settings_lore").withStyle(ChatFormatting.DARK_GRAY)));
        dashboardContainer.setItem(48, settings);

        // 49: Page info
        int displayPage = maxPage + 1;
        int currentDisplay = petPage + 1;
        ItemStack pageInfo = new ItemStack(Items.PAPER);
        setName(pageInfo, Component.translatable("arcadia_prestige.gui.pets.page_info", currentDisplay, displayPage)
                .withStyle(ChatFormatting.WHITE));
        setLore(pageInfo, List.of(
                Component.literal(total + " / " + PetCollectionSavedData.MAX_PETS + " pets stored")
                        .withStyle(ChatFormatting.GRAY)));
        dashboardContainer.setItem(49, pageInfo);

        // 50: Pet Fusion
        ItemStack fuse = new ItemStack(Items.BLAZE_POWDER);
        setName(fuse, Component.translatable("arcadia_prestige.gui.pets.fusion_btn").withStyle(ChatFormatting.GOLD));
        setLore(fuse, List.of(Component.translatable("arcadia_prestige.gui.pets.fusion_lore").withStyle(ChatFormatting.GRAY)));
        dashboardContainer.setItem(50, fuse);

        // 51: Undesignate — visible whenever a pet is designated
        if (PetManager.getDesignatedPetId(player.getUUID()) != null) {
            ItemStack undesignate = new ItemStack(Items.BARRIER);
            setName(undesignate, Component.translatable("arcadia_prestige.gui.pets.undesignate_btn").withStyle(ChatFormatting.RED));
            setLore(undesignate, List.of(Component.translatable("arcadia_prestige.gui.pets.undesignate_lore").withStyle(ChatFormatting.GRAY)));
            dashboardContainer.setItem(51, undesignate);
        }
        // 52: empty (already cleared by buildBottomBar)
    }

    // -------------------------------------------------------------------------
    // Auction House tab (tab 3)
    // -------------------------------------------------------------------------

    /** Called by C2SAhSearch after the server updates the player's search string. */
    public void refreshAhTab() {
        currentTab = 3;
        tabSlot.set(3);
        refreshTab();
    }

    private void handleAhClick(int slotId, ServerPlayer sp) {
        if (slotId == 45) { // ◀ prev
            if (ahPage > 0) { ahPage--; refreshTab(); }
            return;
        }
        if (slotId == 53) { // ▶ next
            List<AuctionListing> filtered = getAhFiltered(sp);
            if ((ahPage + 1) * 36 < filtered.size()) { ahPage++; refreshTab(); }
            return;
        }
        if (slotId == 46) { // 🔍 Search
            PacketHandler.sendToPlayer(sp, new S2COpenAhSearch(AuctionManager.getSearch(sp.getUUID())));
            return;
        }
        if (slotId == 47) { // Filter cycle
            ahCategory = switch (ahCategory) {
                case "" -> "pet";
                case "pet" -> "misc";
                default -> "";
            };
            ahPage = 0;
            refreshTab();
            return;
        }
        if (slotId == 48) { // Leaderboard
            AhLeaderboardMenu.openFor(sp);
            return;
        }
        if (slotId == 51) { // My Listings toggle
            ahMyListings = !ahMyListings;
            ahPage = 0;
            refreshTab();
            return;
        }

        // Listing grid: slots 9–44 = 36 slots (4 rows × 9 cols)
        if (slotId >= 9 && slotId <= 44) {
            int idx = ahPage * 36 + (slotId - 9);
            List<AuctionListing> filtered = getAhFiltered(sp);
            if (idx >= filtered.size()) return;
            AuctionListing listing = filtered.get(idx);
            if (ahMyListings) {
                AuctionManager.cancelListing(sp, listing.listingId(), sp.getServer());
            } else {
                AuctionManager.buyListing(sp, listing.listingId(), sp.getServer());
            }
            refreshTab();
        }
    }

    private List<AuctionListing> getAhFiltered(ServerPlayer sp) {
        String search = AuctionManager.getSearch(sp.getUUID());
        if (ahMyListings) return AuctionManager.getByPlayer(sp.getUUID());
        return AuctionManager.getFiltered(ahCategory, search);
    }

    private void buildAuctionHouseTab() {
        if (!(player instanceof ServerPlayer sp)) return;

        List<AuctionListing> filtered = getAhFiltered(sp);
        int total  = filtered.size();
        int maxPage = total == 0 ? 0 : (total - 1) / 36;
        if (ahPage > maxPage) ahPage = maxPage;

        // Listing grid: 4 rows × 9 cols = 36 per page
        for (int i = 0; i < 36; i++) {
            int slot = 9 + i;
            int listIdx = ahPage * 36 + i;
            if (listIdx < total) {
                AuctionListing listing = filtered.get(listIdx);
                net.minecraft.core.HolderLookup.Provider reg = sp.getServer().registryAccess();
                ItemStack item = com.arcadia.ah.auction.AuctionItemSerializer.fromBase64(listing.itemNbt(), reg);
                if (item.isEmpty()) {
                    item = new ItemStack(Items.BARRIER);
                    setName(item, Component.literal("§cInvalid item"));
                } else {
                    // Append price + seller to lore
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.literal("§7Seller: §f" + listing.sellerName()));
                    lore.add(Component.literal("§6Price: §f" + NumismaticsCompat.formatPrice(listing.price())));
                    lore.add(Component.literal("§7Server: §f" + listing.serverId()));
                    // Stamp expiry epoch so the client can compute a live countdown
                    net.minecraft.nbt.CompoundTag ahTag = item.getOrDefault(DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                    ahTag.putLong("arcadia_ah_expires", listing.expiresAt());
                    item.set(DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.of(ahTag));
                    if (ahMyListings) {
                        lore.add(Component.literal("§c[Click to cancel]").withStyle(ChatFormatting.RED));
                    } else {
                        lore.add(Component.literal("§a[Click to buy]").withStyle(ChatFormatting.GREEN));
                    }
                    // Merge with existing lore
                    net.minecraft.world.item.component.ItemLore existingLore = item.get(DataComponents.LORE);
                    if (existingLore != null) {
                        List<Component> merged = new ArrayList<>(existingLore.lines());
                        merged.addAll(lore);
                        lore = merged;
                    }
                    item.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
                }
                if (listing.sellerUuid().equals(sp.getUUID())) {
                    item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                }
                dashboardContainer.setItem(slot, item);
            } else {
                ItemStack empty = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
                setName(empty, Component.literal("§8No listing"));
                dashboardContainer.setItem(slot, empty);
            }
        }

        // Bottom nav (slots 45-53) — shared prev/page/next via helper, then AH specifics
        buildBottomBar(ahPage, maxPage);

        // 46: Search
        String curSearch = AuctionManager.getSearch(sp.getUUID());
        ItemStack search = new ItemStack(Items.SPYGLASS);
        setName(search, Component.literal("§bSearch").withStyle(ChatFormatting.AQUA));
        setLore(search, List.of(
                Component.literal(curSearch.isEmpty() ? "§7Click to search..." : "§fQuery: §e" + curSearch),
                Component.literal("§7Click to open search").withStyle(ChatFormatting.GRAY)
        ));
        dashboardContainer.setItem(46, search);

        // 47: Filter
        String catDisplay = ahCategory.isEmpty() ? "All" : (ahCategory.equals("pet") ? "Pets" : "Misc");
        ItemStack filter = new ItemStack(Items.HOPPER);
        setName(filter, Component.literal("§dFilter: §f" + catDisplay));
        setLore(filter, List.of(Component.literal("§7Click to cycle: All → Pets → Misc").withStyle(ChatFormatting.GRAY)));
        dashboardContainer.setItem(47, filter);

        // 48: Leaderboard
        ItemStack lb = new ItemStack(Items.NETHER_STAR);
        setName(lb, Component.literal("⭐ Top Business").withStyle(ChatFormatting.GOLD));
        setLore(lb, List.of(Component.literal("View top sellers by unique clients").withStyle(ChatFormatting.GRAY)));
        dashboardContainer.setItem(48, lb);

        // 49: override page info with listing count lore
        ItemStack pageInfo = new ItemStack(Items.PAPER);
        setName(pageInfo, Component.literal("§fPage " + (ahPage + 1) + " / " + (maxPage + 1)).withStyle(ChatFormatting.WHITE));
        setLore(pageInfo, List.of(Component.literal(total + " listing(s)").withStyle(ChatFormatting.GRAY)));
        dashboardContainer.setItem(49, pageInfo);

        // 51: My Listings toggle
        ItemStack myBtn = new ItemStack(ahMyListings ? Items.LIME_DYE : Items.GRAY_DYE);
        setName(myBtn, Component.literal(ahMyListings ? "§a◉ My Listings" : "§7◎ My Listings"));
        setLore(myBtn, List.of(Component.literal(ahMyListings ? "§7Click to browse all" : "§7Click to see yours").withStyle(ChatFormatting.GRAY)));
        dashboardContainer.setItem(51, myBtn);
    }

    // -------------------------------------------------------------------------
    // Daily tab
    // -------------------------------------------------------------------------

    private void buildDailyTab() {
        int streak   = PlayerDataHandler.getStreak(player.getUUID());
        int pathLen  = DailyRewardHandler.PATH.length; // 24
        int pagePos  = streak % pathLen;               // 0-23: next slot index to claim
        int page     = streak / pathLen;               // 0-based page number
        boolean canClaim = PlayerDataHandler.canClaimDaily(player.getUUID());

        // Fill all non-nav slots with glass initially
        ItemStack bg = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        setName(bg, Component.literal(" "));
        for (int s = 9; s < 54; s++) {
            dashboardContainer.setItem(s, bg.copy());
        }

        // Render the 24 path slots
        for (int i = 0; i < pathLen; i++) {
            int slot      = DailyRewardHandler.PATH[i];
            boolean isMilestone = DailyRewardHandler.MILESTONE_SLOTS.contains(slot);
            int cycleDay  = i + 1; // 1-24
            int totalDay  = page * pathLen + cycleDay;

            ItemStack item;
            if (i < pagePos) {
                // Claimed — coal or coal block
                item = new ItemStack(isMilestone ? Items.COAL_BLOCK : Items.COAL);
                setName(item, Component.literal("Day " + totalDay).withStyle(ChatFormatting.DARK_GRAY));
                setLore(item, List.of(Component.literal("\u2713 Claimed").withStyle(ChatFormatting.GREEN)));

            } else if (i == pagePos) {
                // Current — diamond or diamond block (glints when claimable)
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
                // Undiscovered — white glass (future path, not yet reached)
                item = new ItemStack(Items.WHITE_STAINED_GLASS_PANE);
                setName(item, Component.literal("Day " + totalDay).withStyle(ChatFormatting.WHITE));
                setLore(item, List.of(Component.literal("\uD83D\uDD12 Not yet reached").withStyle(ChatFormatting.GRAY)));
            }

            dashboardContainer.setItem(slot, item);
        }

        // Milestone rank gift slots
        int cycle = page;
        int milestoneClaims = DailyRewardHandler.getMilestoneClaims(player.getUUID(), cycle);
        String playerGrade = LuckPermsHook.getGrade(player);
        for (int mi = 0; mi < DailyRewardHandler.MILESTONE_GIFT_SLOTS.length; mi++) {
            boolean milestoneReached = pagePos > DailyRewardHandler.MILESTONE_PATH_INDICES[mi];
            for (int ri = 0; ri < 3; ri++) {
                int giftSlot = DailyRewardHandler.MILESTONE_GIFT_SLOTS[mi][ri];
                String requiredGrade = DailyRewardHandler.RANK_GRADES[ri];
                String displayRank   = DailyRewardHandler.RANK_DISPLAY[ri];
                boolean hasGrade = LuckPermsHook.hasMinimumGrade(player, requiredGrade);
                boolean claimed  = (milestoneClaims & DailyRewardHandler.claimBit(mi, ri)) != 0;
                ChatFormatting rankColor = ri == 0 ? ChatFormatting.GOLD
                        : ri == 1 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA;
                Item rankIcon = ri == 0 ? Items.GOLD_INGOT
                        : ri == 1 ? Items.AMETHYST_SHARD : Items.NETHER_STAR;

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

        // Streak summary at slot 49
        ItemStack streakItem = new ItemStack(Items.EXPERIENCE_BOTTLE);
        int displayPage = page + 1;
        setName(streakItem, Component.literal("\u2746 Streak: " + streak).withStyle(ChatFormatting.GOLD));
        setLore(streakItem, List.of(
                Component.literal("Page " + displayPage + "  \u2014  Day " + pagePos + "/" + pathLen + " done").withStyle(ChatFormatting.YELLOW),
                Component.literal("Don't miss a day!").withStyle(ChatFormatting.GRAY)));
        dashboardContainer.setItem(49, streakItem);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void setName(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
    }

    private void setLore(ItemStack stack, List<Component> lore) {
        stack.set(DataComponents.LORE, new ItemLore(lore));
    }

    // -------------------------------------------------------------------------
    // MenuProvider / static factory
    // -------------------------------------------------------------------------

    /**
     * {@link MenuProvider} implementation used to open the dashboard for a player.
     */
    public static final class Provider implements MenuProvider {

        @Override
        public Component getDisplayName() {
            return Component.translatable("arcadia_prestige.gui.dashboard").withStyle(ChatFormatting.GOLD);
        }

        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
            return new DashboardMenu(id, inv);
        }
    }

    /**
     * Opens the Arcadia Dashboard menu for the given server player.
     */
    public static void openFor(ServerPlayer player) {
        player.openMenu(new Provider());
    }

    /**
     * Opens the Arcadia Dashboard menu starting on the given tab index
     * (0 = Cosmetics, 1 = Pets, 2 = Daily Reward).
     */
    public static void openFor(ServerPlayer player, int initialTab) {
        player.openMenu(new ProviderWithTab(initialTab));
    }

    public static final class ProviderWithTab implements MenuProvider {
        private final int initialTab;
        ProviderWithTab(int initialTab) { this.initialTab = initialTab; }

        @Override
        public Component getDisplayName() {
            return Component.translatable("arcadia_prestige.gui.dashboard").withStyle(ChatFormatting.GOLD);
        }

        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
            DashboardMenu menu = new DashboardMenu(id, inv);
            menu.switchTab(initialTab, player);
            return menu;
        }
    }
}
