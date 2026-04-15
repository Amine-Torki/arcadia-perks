package com.arcadia.pets.server;

import com.arcadia.pets.PetsModItems;
import com.arcadia.pets.PetsModMenus;
import com.arcadia.pets.item.PetData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Read-only chest-like menu (6 rows) showing up to 54 pets from a player's history.
 * The OP can take pet items to restore them.
 * Vanilla container slot-sync populates the client — no extra buf data needed.
 */
public class PetHistoryMenu extends AbstractContainerMenu {

    public static final int ROWS = 6;

    /** Client-side constructor — slots are filled by vanilla sync after open. */
    public PetHistoryMenu(int syncId, Inventory playerInv) {
        this(syncId, playerInv, new SimpleContainer(ROWS * 9));
    }

    /** Server-side constructor — receives a pre-filled SimpleContainer. */
    public PetHistoryMenu(int syncId, Inventory playerInv, SimpleContainer historyContainer) {
        super(PetsModMenus.PET_HISTORY_MENU.get(), syncId);

        // History slots (6 rows × 9 columns)
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(historyContainer, row * 9 + col, 8 + col * 18, 18 + row * 18));
            }
        }
        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }
    }

    /** Opens the history GUI for the given OP, showing the target player's pets (newest first). */
    public static void openFor(Player op, List<PetHistorySavedData.HistoryEntry> entries) {
        if (!(op instanceof net.minecraft.server.level.ServerPlayer sp)) return;

        SimpleContainer container = new SimpleContainer(ROWS * 9);
        for (int i = 0; i < entries.size() && i < ROWS * 9; i++) {
            PetData pet = PetData.fromTag(entries.get(i).petTag());
            if (pet != null) {
                ItemStack stack = new ItemStack(PetsModItems.PET_ITEM.get());
                pet.applyToStack(stack);
                container.setItem(i, stack);
            }
        }

        sp.openMenu(new SimpleMenuProvider(
                (syncId, inv, player) -> new PetHistoryMenu(syncId, inv, container),
                Component.translatable("arcadia_pets.gui.history.title")
        ));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack out = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        out = stack.copy();
        int historySize = ROWS * 9;
        if (index < historySize) {
            if (!this.moveItemStackTo(stack, historySize, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY; // can't put items into history
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == out.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return out;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
