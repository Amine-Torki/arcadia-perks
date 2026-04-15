package com.arcadia.pets.server;

import com.arcadia.pets.PetsModItems;
import com.arcadia.pets.PetsModMenus;
import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetItem;
import com.arcadia.pets.item.PetRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side container menu for the Pet Fusion system.
 *
 * <p>Layout (54-slot chest area + player inventory):
 * <pre>
 *  Row 0 (slots  0- 8): decorative title bar
 *  Row 1 (slots  9-17): ·[P1][P2][P3][P4][P5]··  (slots 10–14)
 *  Row 2 (slots 18-26): ·········[FUSE]·········  (slot 22)
 *  Row 3 (slots 27-35): ·······[OUTPUT]·········  (slot 31)
 *  Row 4 (slots 36-44): decorative
 *  Row 5 (slots 45-53): decorative footer
 * </pre>
 * The 5 pet input slots are real interactive slots (accept PetItem only).
 * All other slots in the 54-slot area are display-only.
 */
public class FusionMenu extends AbstractContainerMenu {

    // ── Slot indices in the 54-slot fusion area ──────────────────────────────
    static final int[] PET_SLOT_INDICES = {11, 12, 13, 14, 15};
    static final int   FUSE_SLOT        = 22;
    static final int   OUTPUT_SLOT      = 31;
    static final int   BACK_SLOT        = 49;

    private static final Set<Integer> PET_SLOT_SET = Set.of(11, 12, 13, 14, 15);

    // The backing container for all 54 display+input slots
    private final SimpleContainer fusionContainer = new SimpleContainer(54);
    private final Player player;

    // ── Constructor ───────────────────────────────────────────────────────────

    public FusionMenu(int containerId, Inventory playerInv) {
        super(PetsModMenus.FUSION_MENU.get(), containerId);
        this.player = playerInv.player;

        // 54 fusion area slots — pet input slots are interactive, rest are display-only
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                final int index = col + row * 9;
                final boolean isPetSlot = PET_SLOT_SET.contains(index);
                addSlot(new Slot(fusionContainer, index, 8 + col * 18, 18 + row * 18) {
                    @Override public boolean mayPlace(ItemStack s) {
                        return isPetSlot && s.getItem() instanceof PetItem;
                    }
                    @Override public boolean mayPickup(Player p) {
                        return isPetSlot;
                    }
                });
            }
        }

        // Player inventory (3 rows × 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }

        if (!player.level().isClientSide()) {
            refreshDisplay();
        }
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0) {
            super.clicked(slotId, button, clickType, player);
            return;
        }

        // Back button
        if (slotId == BACK_SLOT) {
            if (player instanceof ServerPlayer sp) {
                UUID pid = PetManager.getDesignatedPetId(sp.getUUID());
                if (pid != null) {
                    ItemStack s = PetManager.findPetStackAnywhere(sp, pid);
                    if (!s.isEmpty()) PetManager.openPanelFor(sp, s);
                }
            }
            return;
        }

        // Fuse button
        if (slotId == FUSE_SLOT) {
            if (player instanceof ServerPlayer sp) performFusion(sp);
            broadcastChanges();
            return;
        }

        // Pet input slots — allow vanilla item movement
        if (PET_SLOT_SET.contains(slotId)) {
            super.clicked(slotId, button, clickType, player);
            if (!player.level().isClientSide()) {
                refreshDisplay();
                broadcastChanges();
            }
            return;
        }

        // Display-only slots in fusion area — eat the click
        if (slotId < 54) {
            broadcastChanges();
            return;
        }

        // Player inventory — vanilla behaviour
        super.clicked(slotId, button, clickType, player);
        if (!player.level().isClientSide()) {
            refreshDisplay();
            broadcastChanges();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Shift-click from player inventory → move to first free pet input slot
        if (index >= 54) {
            Slot source = slots.get(index);
            ItemStack stack = source.getItem();
            if (!stack.isEmpty() && stack.getItem() instanceof PetItem) {
                for (int petIdx : PET_SLOT_INDICES) {
                    if (fusionContainer.getItem(petIdx).isEmpty()) {
                        fusionContainer.setItem(petIdx, stack.split(1));
                        source.setChanged();
                        if (!player.level().isClientSide()) {
                            refreshDisplay();
                            broadcastChanges();
                        }
                        return ItemStack.EMPTY;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        if (!player.level().isClientSide()) {
            refreshDisplay();
            broadcastChanges();
        }
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Return unfused pets to the player on close
        for (int idx : PET_SLOT_INDICES) {
            ItemStack s = fusionContainer.getItem(idx);
            if (!s.isEmpty()) {
                player.getInventory().add(s);
                fusionContainer.setItem(idx, ItemStack.EMPTY);
            }
        }
    }

    // ── Fusion logic ──────────────────────────────────────────────────────────

    private void performFusion(ServerPlayer player) {
        // Collect pet data from all 5 slots
        List<PetData> pets = new ArrayList<>();
        for (int idx : PET_SLOT_INDICES) {
            ItemStack s = fusionContainer.getItem(idx);
            if (s.isEmpty() || !(s.getItem() instanceof PetItem)) {
                player.sendSystemMessage(Component.translatable("arcadia_pets.gui.fusion.place_all").withStyle(ChatFormatting.RED));
                return;
            }
            PetData pd = PetData.fromStack(s);
            if (pd == null) {
                player.sendSystemMessage(Component.translatable("arcadia_pets.gui.fusion.invalid").withStyle(ChatFormatting.RED));
                return;
            }
            pets.add(pd);
        }

        // All must be same rarity
        PetRarity rarity = pets.get(0).rarity();
        for (PetData pd : pets) {
            if (pd.rarity() != rarity) {
                player.sendSystemMessage(Component.translatable("arcadia_pets.gui.fusion.same_rarity").withStyle(ChatFormatting.RED));
                return;
            }
        }

        // Rarity must have a next tier
        if (rarity.next().isEmpty()) {
            player.sendSystemMessage(Component.translatable("arcadia_pets.gui.fusion.max_rarity").withStyle(ChatFormatting.RED));
            return;
        }

        // None may be the active/pocket pet
        PetData active = PetManager.getActivePetData(player.getUUID());
        if (active == null) active = PetManager.getPocketPetData(player.getUUID());
        for (PetData pd : pets) {
            if (active != null && active.petId().equals(pd.petId())) {
                player.sendSystemMessage(Component.translatable("arcadia_pets.gui.fusion.recall_first").withStyle(ChatFormatting.RED));
                return;
            }
        }

        // Remove pets from collection if they were deposited there
        if (player.getServer() != null) {
            PetCollectionSavedData col = PetCollectionSavedData.getOrCreate(player.getServer());
            for (PetData pd : pets) {
                col.removeByPetId(player.getUUID(), pd.petId());
            }
        }

        // Consume all 5 slots
        for (int idx : PET_SLOT_INDICES) {
            fusionContainer.setItem(idx, ItemStack.EMPTY);
        }

        // Award fusion bag
        ItemStack bag = PetsModItems.fusionBagFor(rarity);
        if (!player.getInventory().add(bag)) {
            player.drop(bag, false);
        }

        // Visual & audio feedback
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.75f, 1.0f);
        if (player.level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                    player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.8, 0.5, 0.3);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1, player.getZ(), 20, 0.4, 0.4, 0.4, 0.15);
        }

        PetRarity nextRarity = rarity.next().get();
        player.sendSystemMessage(Component.literal("✨ Fusion complete! You received a ")
                .append(nextRarity.getStyledName())
                .append(Component.literal(" bag!").withStyle(ChatFormatting.GOLD)));

        refreshDisplay();
        broadcastChanges();
    }

    // ── Display builder ───────────────────────────────────────────────────────

    private void refreshDisplay() {
        // Fill all non-pet slots with chrome first, then overlay the interactive elements
        fillChrome();
        buildFuseButton();
        buildOutputPreview();
    }

    private void fillChrome() {
        for (int i = 0; i < 54; i++) {
            if (PET_SLOT_SET.contains(i) || i == FUSE_SLOT || i == OUTPUT_SLOT || i == BACK_SLOT) continue;
            fusionContainer.setItem(i, darkPane(" "));
        }

        // Title bar — row 0
        fusionContainer.setItem(4, titled());

        // Back button — bottom row center
        ItemStack back = new ItemStack(Items.ARROW);
        setName(back, Component.literal("← Back to /pets").withStyle(ChatFormatting.YELLOW));
        setLore(back, List.of(Component.translatable("arcadia_pets.gui.fusion.back_lore").withStyle(ChatFormatting.GRAY)));
        fusionContainer.setItem(BACK_SLOT, back);
    }

    private void buildFuseButton() {
        FusionState state = checkState();
        ItemStack btn;
        switch (state) {
            case READY -> {
                PetRarity rarity   = getCommonRarity();
                PetRarity nextRarity = rarity.next().orElseThrow();
                btn = new ItemStack(Items.BLAZE_POWDER);
                btn.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                setName(btn, Component.literal("✦ FUSE! ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                setLore(btn, List.of(
                        Component.literal("5 × ").withStyle(ChatFormatting.GRAY)
                                .append(rarity.getStyledName())
                                .append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                                .append(nextRarity.getStyledName())
                                .append(Component.literal(" Bag").withStyle(ChatFormatting.GRAY)),
                        Component.translatable("arcadia_pets.gui.fusion.click_fuse").withStyle(ChatFormatting.GREEN)
                ));
            }
            case MISMATCH -> {
                btn = new ItemStack(Items.BARRIER);
                setName(btn, Component.literal("✗ Rarity Mismatch").withStyle(ChatFormatting.RED));
                setLore(btn, List.of(Component.translatable("arcadia_pets.gui.fusion.must_same").withStyle(ChatFormatting.GRAY)));
            }
            case MAX_RARITY -> {
                btn = new ItemStack(Items.BARRIER);
                setName(btn, Component.literal("✗ Cannot Fuse Further").withStyle(ChatFormatting.DARK_RED));
                setLore(btn, List.of(Component.translatable("arcadia_pets.gui.fusion.ceiling").withStyle(ChatFormatting.GRAY)));
            }
            default -> { // INCOMPLETE
                int filled = countFilled();
                btn = new ItemStack(Items.GRAY_DYE);
                setName(btn, Component.translatable("arcadia_pets.gui.fusion.altar").withStyle(ChatFormatting.GRAY));
                setLore(btn, List.of(
                        Component.translatable("arcadia_pets.gui.fusion.fill_slots").withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal("of the same rarity to fuse.").withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal(filled + " / 5 pets placed").withStyle(filled > 0 ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY)
                ));
            }
        }
        fusionContainer.setItem(FUSE_SLOT, btn);
    }

    private void buildOutputPreview() {
        if (checkState() != FusionState.READY) {
            fusionContainer.setItem(OUTPUT_SLOT, darkPane(" "));
            return;
        }
        PetRarity rarity = getCommonRarity();
        PetRarity next   = rarity.next().orElseThrow();
        ItemStack preview = PetsModItems.fusionBagFor(rarity);
        setName(preview, Component.literal("Result: ").withStyle(ChatFormatting.GRAY)
                .append(next.getStyledName())
                .append(Component.literal(" Bag").withStyle(ChatFormatting.GRAY)));
        setLore(preview, List.of(Component.literal("Guaranteed " + next.getDisplayName() + "+ roll").withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY)));
        fusionContainer.setItem(OUTPUT_SLOT, preview);
    }

    // ── State helpers ─────────────────────────────────────────────────────────

    private enum FusionState { INCOMPLETE, MISMATCH, MAX_RARITY, READY }

    private FusionState checkState() {
        if (countFilled() < 5) return FusionState.INCOMPLETE;
        PetRarity rarity = getCommonRarity();
        if (rarity == null) return FusionState.MISMATCH;
        if (rarity.next().isEmpty()) return FusionState.MAX_RARITY;
        return FusionState.READY;
    }

    private int countFilled() {
        int n = 0;
        for (int idx : PET_SLOT_INDICES) {
            if (!fusionContainer.getItem(idx).isEmpty()) n++;
        }
        return n;
    }

    /** Returns the shared rarity if all filled slots agree, null if they differ. */
    private PetRarity getCommonRarity() {
        PetRarity found = null;
        for (int idx : PET_SLOT_INDICES) {
            ItemStack s = fusionContainer.getItem(idx);
            if (s.isEmpty()) continue;
            PetData pd = PetData.fromStack(s);
            if (pd == null) return null;
            if (found == null) found = pd.rarity();
            else if (found != pd.rarity()) return null;
        }
        return found;
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack darkPane(String name) {
        ItemStack s = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        setName(s, Component.literal(name));
        return s;
    }

    private ItemStack titled() {
        ItemStack s = new ItemStack(Items.NETHER_STAR);
        setName(s, Component.literal("✦ Pet Fusion ✦").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        setLore(s, List.of(
                Component.literal("Place 5 pets of the same rarity").withStyle(ChatFormatting.GRAY),
                Component.literal("in the row of slots, then fuse!").withStyle(ChatFormatting.GRAY)
        ));
        return s;
    }

    private void setName(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
    }

    private void setLore(ItemStack stack, List<Component> lore) {
        stack.set(DataComponents.LORE, new ItemLore(lore));
    }

    // ── MenuProvider / static factory ─────────────────────────────────────────

    public static final class Provider implements MenuProvider {
        @Override
        public Component getDisplayName() {
            return Component.translatable("arcadia_pets.gui.fusion.title").withStyle(ChatFormatting.LIGHT_PURPLE);
        }

        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
            return new FusionMenu(id, inv);
        }
    }

    public static void openFor(ServerPlayer player) {
        player.openMenu(new Provider());
    }
}
