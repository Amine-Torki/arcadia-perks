package com.arcadia.ah.client;

import com.arcadia.ah.server.AhLeaderboardMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class AhLeaderboardScreen extends AbstractContainerScreen<AhLeaderboardMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public AhLeaderboardScreen(AhLeaderboardMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 222;
        this.imageWidth = 176;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, 6 * 18 + 17);
        graphics.blit(TEXTURE, x, y + 6 * 18 + 17, 0, 126, this.imageWidth, 96);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component label = Component.literal("§6Top Business");
        graphics.drawString(this.font, label,
                (this.imageWidth - this.font.width(label)) / 2, 6, 0xFFD700, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
