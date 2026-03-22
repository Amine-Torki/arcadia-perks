package com.arcadia.pets.client;

import com.arcadia.pets.server.PetHistoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class PetHistoryScreen extends AbstractContainerScreen<PetHistoryMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public PetHistoryScreen(PetHistoryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 222;
        this.imageWidth = 176;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;
        g.blit(TEXTURE, x, y, 0, 0, this.imageWidth, PetHistoryMenu.ROWS * 18 + 17);
        g.blit(TEXTURE, x, y + PetHistoryMenu.ROWS * 18 + 17, 0, 126, this.imageWidth, 96);
    }
}
