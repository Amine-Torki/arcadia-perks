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
        int panelTop = this.height / 2 - 65;

        priceInput = new EditBox(this.font, cx - 60, panelTop + 82, 120, 16, Component.literal("Price"));
        priceInput.setMaxLength(15);
        priceInput.setValue("");
        priceInput.setFocused(true);
        priceInput.setHint(Component.translatable("arcadia_ah.gui.sell.price_hint"));
        addRenderableWidget(priceInput);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, ArcadiaTheme.OVERLAY_BG);

        int cx = this.width / 2;
        int panelW = 200;
        int panelH = 130;
        int panelX = cx - panelW / 2;
        int panelY = this.height / 2 - 65;

        // Panel
        ArcadiaTheme.drawPanel(g, panelX, panelY, panelW, panelH, false, ArcadiaTheme.COPPER);

        int y = panelY + 8;

        // Title
        ArcadiaTheme.drawCenteredText(g, Component.translatable("arcadia_ah.gui.sell.title"), cx, y, ArcadiaTheme.BRASS);
        y += 14;

        // Separator
        ArcadiaTheme.drawSeparator(g, panelX, y, panelW, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x44));
        y += 6;

        // Item icon (centered)
        g.renderItem(itemToSell, cx - 8, y);
        y += 20;

        // Item name + quantity on same line
        String nameStr = itemToSell.getHoverName().getString();
        if (itemToSell.getCount() > 1) {
            nameStr += " x" + itemToSell.getCount();
        }
        g.drawCenteredString(this.font, nameStr, cx, y, ArcadiaTheme.TEXT_PRIMARY);
        y += 14;

        // "Price:" label
        g.drawCenteredString(this.font, Component.translatable("arcadia_ah.gui.sell.price_label"), cx, y, ArcadiaTheme.TEXT_SECONDARY);
        y += 12;

        // Input field is rendered by widget system (positioned in init)

        // Buttons below input
        int btnY = panelY + panelH - 24;
        int btnW = 80;
        int btnH = 16;

        // Sell button
        boolean hovSell = mouseX >= cx - btnW - 4 && mouseX < cx - 4 && mouseY >= btnY && mouseY < btnY + btnH;
        int sellBg = hovSell ? ArcadiaTheme.brighten(0xFF1A3A1A, 25) : 0xFF1A3A1A;
        g.fill(cx - btnW - 4, btnY, cx - 4, btnY + btnH, sellBg);
        ArcadiaTheme.drawBorder(g, cx - btnW - 4, btnY, btnW, btnH,
                hovSell ? ArcadiaTheme.COPPER : ArcadiaTheme.BORDER_IDLE);
        g.drawCenteredString(this.font, Component.translatable("arcadia_ah.gui.sell.confirm"), cx - btnW / 2 - 4, btnY + 4, ArcadiaTheme.TEXT_PRIMARY);

        // Cancel button
        boolean hovCancel = mouseX >= cx + 4 && mouseX < cx + btnW + 4 && mouseY >= btnY && mouseY < btnY + btnH;
        int cancelBg = hovCancel ? ArcadiaTheme.brighten(0xFF3A1A1A, 25) : 0xFF3A1A1A;
        g.fill(cx + 4, btnY, cx + btnW + 4, btnY + btnH, cancelBg);
        ArcadiaTheme.drawBorder(g, cx + 4, btnY, btnW, btnH,
                hovCancel ? ArcadiaTheme.COPPER : ArcadiaTheme.BORDER_IDLE);
        g.drawCenteredString(this.font, Component.translatable("arcadia_ah.gui.sell.cancel"), cx + btnW / 2 + 4, btnY + 4, ArcadiaTheme.TEXT_PRIMARY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int cx = this.width / 2;
        int panelW = 200;
        int panelH = 130;
        int panelY = this.height / 2 - 65;
        int btnY = panelY + panelH - 24;
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
