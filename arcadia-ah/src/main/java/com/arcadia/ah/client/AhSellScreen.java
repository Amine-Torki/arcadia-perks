package com.arcadia.ah.client;

import com.arcadia.ah.network.C2SAhSell;
import com.arcadia.lib.client.ArcadiaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Price input screen for selling an item on the Auction House.
 * Opened when a player shift-clicks an item from inventory while on the AH tab.
 */
public class AhSellScreen extends Screen {

    private final ItemStack itemToSell;
    private final int slotIndex;
    private EditBox priceInput;

    public AhSellScreen(ItemStack itemToSell, int slotIndex) {
        super(Component.literal("Sell Item"));
        this.itemToSell = itemToSell.copy();
        this.slotIndex = slotIndex;
    }

    public static void open(ItemStack item, int slotIndex) {
        Minecraft.getInstance().setScreen(new AhSellScreen(item, slotIndex));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        priceInput = new EditBox(this.font, cx - 60, cy - 5, 120, 18, Component.literal("Price"));
        priceInput.setMaxLength(15);
        priceInput.setValue("");
        priceInput.setFocused(true);
        priceInput.setHint(Component.literal("Enter price..."));
        addRenderableWidget(priceInput);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, ArcadiaTheme.OVERLAY_BG);

        int cx = this.width / 2;
        int cy = this.height / 2;

        // Panel
        ArcadiaTheme.drawPanel(g, cx - 90, cy - 50, 180, 100, false, ArcadiaTheme.COPPER);

        // Title
        ArcadiaTheme.drawCenteredText(g, Component.literal("Sell on Auction House"), cx, cy - 42, ArcadiaTheme.BRASS);

        // Item preview
        g.renderItem(itemToSell, cx - 8, cy - 30);
        g.renderItemDecorations(this.font, itemToSell, cx - 8, cy - 30);

        // Item name
        Component itemName = itemToSell.getHoverName();
        g.drawCenteredString(this.font, itemName, cx, cy - 18, ArcadiaTheme.TEXT_PRIMARY);

        // Quantity
        if (itemToSell.getCount() > 1) {
            g.drawCenteredString(this.font, "x" + itemToSell.getCount(), cx + 20, cy - 26, ArcadiaTheme.TEXT_SECONDARY);
        }

        super.render(g, mouseX, mouseY, partialTick);

        // Buttons
        int btnY = cy + 20;
        int btnW = 80;
        int btnH = 16;

        // Confirm button
        boolean hovConfirm = mouseX >= cx - btnW - 4 && mouseX < cx - 4 && mouseY >= btnY && mouseY < btnY + btnH;
        int confirmBg = hovConfirm ? ArcadiaTheme.brighten(0xFF1A3A1A, 25) : 0xFF1A3A1A;
        g.fill(cx - btnW - 4, btnY, cx - 4, btnY + btnH, confirmBg);
        ArcadiaTheme.drawBorder(g, cx - btnW - 4, btnY, btnW, btnH,
                hovConfirm ? ArcadiaTheme.COPPER : ArcadiaTheme.BORDER_IDLE);
        g.drawCenteredString(this.font, "Sell", cx - btnW / 2 - 4, btnY + 4, ArcadiaTheme.TEXT_PRIMARY);

        // Cancel button
        boolean hovCancel = mouseX >= cx + 4 && mouseX < cx + btnW + 4 && mouseY >= btnY && mouseY < btnY + btnH;
        int cancelBg = hovCancel ? ArcadiaTheme.brighten(0xFF3A1A1A, 25) : 0xFF3A1A1A;
        g.fill(cx + 4, btnY, cx + btnW + 4, btnY + btnH, cancelBg);
        ArcadiaTheme.drawBorder(g, cx + 4, btnY, btnW, btnH,
                hovCancel ? ArcadiaTheme.COPPER : ArcadiaTheme.BORDER_IDLE);
        g.drawCenteredString(this.font, "Cancel", cx + btnW / 2 + 4, btnY + 4, ArcadiaTheme.TEXT_PRIMARY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int btnY = cy + 20;
        int btnW = 80;
        int btnH = 16;

        // Confirm
        if (mouseX >= cx - btnW - 4 && mouseX < cx - 4 && mouseY >= btnY && mouseY < btnY + btnH) {
            confirm();
            return true;
        }
        // Cancel
        if (mouseX >= cx + 4 && mouseX < cx + btnW + 4 && mouseY >= btnY && mouseY < btnY + btnH) {
            this.onClose();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { confirm(); return true; } // Enter
        if (keyCode == 256) { this.onClose(); return true; } // Escape
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirm() {
        String text = priceInput.getValue().trim();
        try {
            long price = Long.parseLong(text);
            if (price <= 0) return;
            PacketDistributor.sendToServer(new C2SAhSell(price, slotIndex));
            this.onClose();
        } catch (NumberFormatException ignored) {
            // Invalid input — do nothing
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
