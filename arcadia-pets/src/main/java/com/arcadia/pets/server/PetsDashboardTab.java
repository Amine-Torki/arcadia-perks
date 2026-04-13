package com.arcadia.pets.server;

import com.arcadia.lib.dashboard.DashboardTabHandler;
import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implements all Pets tab (tab 1) logic for the Arcadia Dashboard.
 * Registered into DashboardMenu at startup when arcadia-pets is present.
 */
public final class PetsDashboardTab implements DashboardTabHandler {

    private int petPage = 0;

    // ── DashboardTabHandler ───────────────────────────────────────────────────

    @Override
    public void buildTab(SimpleContainer container, ServerPlayer player) {
        List<ItemStack> col = getCollection(player);
        int total   = col.size();
        int maxPage = total == 0 ? 0 : (total - 1) / 36;
        if (petPage > maxPage) petPage = maxPage;

        UUID designatedId  = PetManager.getDesignatedPetId(player.getUUID());
        UUID equippedId    = null;
        PetData equippedData = PetManager.getActivePetData(player.getUUID());
        if (equippedData == null) equippedData = PetManager.getPocketPetData(player.getUUID());
        if (equippedData != null) equippedId = equippedData.petId();

        // ── Pet grid: slots 9–44 (4 rows × 9 = 36 per page) ─────────────────
        for (int i = 0; i < 36; i++) {
            int slot     = 9 + i;
            int colIndex = petPage * 36 + i;
            if (colIndex < total) {
                ItemStack src  = col.get(colIndex);
                PetData pd     = PetData.fromStack(src);
                ItemStack display = src.copy();
                boolean isDesignated = designatedId != null && pd != null && designatedId.equals(pd.petId());
                boolean isEquipped   = equippedId   != null && pd != null && equippedId.equals(pd.petId());
                if (isEquipped || isDesignated) display.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

                List<Component> lore = new ArrayList<>();
                if (isEquipped) lore.add(Component.translatable("arcadia_pets.gui.pets.equipped_label")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                else if (isDesignated) lore.add(Component.translatable("arcadia_pets.gui.pets.active_label")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                lore.add(Component.translatable("arcadia_pets.gui.pets.set_active").withStyle(ChatFormatting.AQUA));
                lore.add(Component.translatable("arcadia_pets.gui.pets.retrieve").withStyle(ChatFormatting.YELLOW));
                display.set(DataComponents.LORE, new ItemLore(lore));
                container.setItem(slot, display);
            } else {
                ItemStack empty = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
                setName(empty, Component.translatable("arcadia_pets.gui.pets.empty_slot").withStyle(ChatFormatting.DARK_GRAY));
                setLore(empty, List.of(Component.translatable("arcadia_pets.gui.pets.shift_deposit").withStyle(ChatFormatting.GRAY)));
                container.setItem(slot, empty);
            }
        }

        // ── Bottom nav (slots 45–53) ──────────────────────────────────────────
        // Layout: [◀][filler][guide][hud][page][fuse][recall][filler][▶]
        buildBottomBar(container, petPage, maxPage);

        ItemStack guide = new ItemStack(Items.BOOK);
        setName(guide, Component.translatable("arcadia_pets.gui.pets.guide_btn").withStyle(ChatFormatting.LIGHT_PURPLE));
        setLore(guide, List.of(Component.translatable("arcadia_pets.gui.pets.guide_lore").withStyle(ChatFormatting.GRAY)));
        container.setItem(47, guide);

        ItemStack settings = new ItemStack(Items.COMPARATOR);
        setName(settings, Component.translatable("arcadia_pets.gui.pets.hud_settings_btn").withStyle(ChatFormatting.GRAY));
        setLore(settings, List.of(Component.translatable("arcadia_pets.gui.pets.hud_settings_lore").withStyle(ChatFormatting.DARK_GRAY)));
        container.setItem(48, settings);

        ItemStack pageInfo = new ItemStack(Items.PAPER);
        setName(pageInfo, Component.translatable("arcadia_pets.gui.pets.page_info", petPage + 1, maxPage + 1).withStyle(ChatFormatting.WHITE));
        setLore(pageInfo, List.of(Component.literal(total + " / " + PetCollectionSavedData.MAX_PETS + " pets stored").withStyle(ChatFormatting.GRAY)));
        container.setItem(49, pageInfo);

        ItemStack fuse = new ItemStack(Items.BLAZE_POWDER);
        setName(fuse, Component.translatable("arcadia_pets.gui.pets.fusion_btn").withStyle(ChatFormatting.GOLD));
        setLore(fuse, List.of(Component.translatable("arcadia_pets.gui.pets.fusion_lore").withStyle(ChatFormatting.GRAY)));
        container.setItem(50, fuse);

        if (PetManager.getDesignatedPetId(player.getUUID()) != null) {
            ItemStack undesignate = new ItemStack(Items.BARRIER);
            setName(undesignate, Component.translatable("arcadia_pets.gui.pets.undesignate_btn").withStyle(ChatFormatting.RED));
            setLore(undesignate, List.of(Component.translatable("arcadia_pets.gui.pets.undesignate_lore").withStyle(ChatFormatting.GRAY)));
            container.setItem(51, undesignate);
        }
    }

    @Override
    public boolean handleClick(int slotId, int button, ServerPlayer sp, Runnable refreshTab) {
        if (slotId == 45) {
            if (petPage > 0) { petPage--; refreshTab.run(); }
            return true;
        }
        if (slotId == 53) {
            int total = getCollection(sp).size();
            if ((petPage + 1) * 36 < total) { petPage++; refreshTab.run(); }
            return true;
        }
        if (slotId == 51) {
            UUID designatedId = PetManager.getDesignatedPetId(sp.getUUID());
            if (designatedId != null) {
                PetData activePd = PetManager.getActivePetData(sp.getUUID());
                if (activePd == null) activePd = PetManager.getPocketPetData(sp.getUUID());
                if (activePd != null && activePd.petId().equals(designatedId)) PetManager.despawn(sp);
                PetManager.clearDesignatedPet(sp.getUUID(), designatedId);
                refreshTab.run();
            }
            return true;
        }
        if (slotId == 50) {
            sp.closeContainer();
            FusionMenu.openFor(sp);
            return true;
        }
        if (slotId == 47 || slotId == 48) return true; // handled client-side

        if (slotId >= 9 && slotId <= 44) {
            int colIndex = petPage * 36 + (slotId - 9);
            List<ItemStack> col = getCollection(sp);
            if (colIndex < col.size()) {
                if (button == 1) {
                    withdrawPetToInventory(sp, colIndex, refreshTab);
                } else {
                    ItemStack petStack = col.get(colIndex);
                    PetData pd = PetData.fromStack(petStack);
                    if (pd != null) PetManager.setDesignatedPet(sp, pd.petId());
                    refreshTab.run();
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public ItemStack getNavBarItem(ServerPlayer player) {
        UUID designatedId = PetManager.getDesignatedPetId(player.getUUID());
        if (designatedId == null) return ItemStack.EMPTY;
        ItemStack petStack = PetManager.findPetStackAnywhere(player, designatedId);
        if (petStack.isEmpty()) return ItemStack.EMPTY;
        ItemStack display = petStack.copy();
        PetData pd = PetData.fromStack(display);
        if (pd != null) {
            setName(display, Component.literal("▶ ")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(display.getHoverName()));
            setLore(display, List.of(
                    Component.translatable("arcadia_pets.gui.pets.active_label").withStyle(ChatFormatting.GOLD),
                    Component.translatable("arcadia_pets.gui.pets.click_panel").withStyle(ChatFormatting.GREEN)));
        }
        return display;
    }

    @Override
    public boolean handleNavBarClick(ServerPlayer player, Runnable refreshTab) {
        UUID designated = PetManager.getDesignatedPetId(player.getUUID());
        if (designated != null) {
            ItemStack petStack = PetManager.findPetStackAnywhere(player, designated);
            if (!petStack.isEmpty()) PetManager.openPanelFor(player, petStack);
        }
        return true;
    }

    @Override
    public boolean handleInventoryShiftClick(Slot slot, ServerPlayer sp, Runnable refreshTab) {
        ItemStack clickedStack = slot.getItem();
        if (!(clickedStack.getItem() instanceof PetItem)) return false;
        PetData pd = PetData.fromStack(clickedStack);
        if (pd == null) return true;
        // Block depositing equipped pet
        PetData eq = PetManager.getActivePetData(sp);
        if (eq == null) eq = PetManager.getPocketPetData(sp.getUUID());
        if (eq != null && eq.petId().equals(pd.petId())) {
            sp.sendSystemMessage(Component.translatable("arcadia_pets.gui.pets.recall_first").withStyle(ChatFormatting.RED));
            return true;
        }
        if (sp.getServer() == null) return true;
        PetCollectionSavedData col = PetCollectionSavedData.getOrCreate(sp.getServer());
        if (col.size(sp.getUUID()) >= PetCollectionSavedData.MAX_PETS) {
            sp.sendSystemMessage(Component.translatable("arcadia_pets.msg.collection_full").withStyle(ChatFormatting.RED));
        } else if (col.deposit(sp.getUUID(), clickedStack)) {
            slot.set(ItemStack.EMPTY);
            sp.sendSystemMessage(Component.translatable("arcadia_pets.msg.pet_deposited").withStyle(ChatFormatting.GREEN));
            refreshTab.run();
        }
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<ItemStack> getCollection(ServerPlayer sp) {
        if (sp.getServer() == null) return List.of();
        return PetCollectionSavedData.getOrCreate(sp.getServer()).getCollection(sp.getUUID());
    }

    private void withdrawPetToInventory(ServerPlayer sp, int colIndex, Runnable refreshTab) {
        if (sp.getServer() == null) return;
        if (sp.getInventory().getFreeSlot() < 0) {
            sp.sendSystemMessage(Component.translatable("arcadia_pets.msg.inventory_full").withStyle(ChatFormatting.RED));
            return;
        }
        PetCollectionSavedData data = PetCollectionSavedData.getOrCreate(sp.getServer());
        ItemStack removed = data.withdraw(sp.getUUID(), colIndex);
        if (removed.isEmpty()) return;
        PetData withdrawnPet = PetData.fromStack(removed);
        if (withdrawnPet != null) {
            PetData activePet = PetManager.getActivePetData(sp);
            if (activePet == null) activePet = PetManager.getPocketPetData(sp.getUUID());
            if (activePet != null && activePet.petId().equals(withdrawnPet.petId())) PetManager.despawn(sp);
            UUID designatedId = PetManager.getDesignatedPetId(sp.getUUID());
            if (designatedId != null && designatedId.equals(withdrawnPet.petId()))
                PetManager.clearDesignatedPet(sp.getUUID(), designatedId);
        }
        sp.getInventory().add(removed);
        sp.sendSystemMessage(Component.translatable("arcadia_pets.msg.pet_retrieved").withStyle(ChatFormatting.YELLOW));
        int remaining = data.size(sp.getUUID());
        if (petPage > 0 && petPage * 36 >= remaining) petPage--;
        refreshTab.run();
    }

    private void buildBottomBar(SimpleContainer container, int page, int maxPage) {
        for (int i = 45; i <= 53; i++) {
            ItemStack g = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
            setName(g, Component.literal(" "));
            container.setItem(i, g);
        }
        container.setItem(45, arrowItem("◀ Previous"));
        ItemStack pi = new ItemStack(Items.PAPER);
        setName(pi, Component.literal("§fPage " + (page + 1) + " / " + (maxPage + 1)));
        container.setItem(49, pi);
        container.setItem(53, arrowItem("Next ▶"));
    }

    private ItemStack arrowItem(String label) {
        ItemStack s = new ItemStack(Items.ARROW);
        setName(s, Component.literal(label).withStyle(ChatFormatting.AQUA));
        return s;
    }

    private static void setName(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
    }

    private static void setLore(ItemStack stack, List<Component> lore) {
        stack.set(DataComponents.LORE, new ItemLore(lore));
    }
}
