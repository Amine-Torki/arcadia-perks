package com.arcadia.pets.client;

import com.arcadia.lib.client.ArcadiaTheme;
import com.arcadia.pets.server.PetHistoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PetHistoryScreen extends AbstractContainerScreen<PetHistoryMenu> {

    public PetHistoryScreen(PetHistoryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 222;
        this.imageWidth = 176;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ArcadiaTheme.drawContainerBg(g, this.leftPos, this.topPos, this.imageWidth, 6);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int titleX = (this.imageWidth - this.font.width(this.title)) / 2;
        g.drawString(this.font, this.title, titleX + 1, 7, 0x22000000, false);
        g.drawString(this.font, this.title, titleX, 6, ArcadiaTheme.BRASS, false);
        g.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, ArcadiaTheme.TEXT_DIM, false);
    }
}
