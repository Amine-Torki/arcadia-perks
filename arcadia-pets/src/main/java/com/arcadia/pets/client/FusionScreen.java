package com.arcadia.pets.client;


import com.arcadia.pets.server.FusionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import com.arcadia.pets.network.C2SPetAction;

/**
 * Client-side screen for the Pet Fusion altar ({@link FusionMenu}).
 * Uses the standard double-chest background texture — no custom atlas required.
 */
public class FusionScreen extends AbstractContainerScreen<FusionMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public FusionScreen(FusionMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 222;       // 6-row chest height
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = (this.width  - this.imageWidth)  / 2;
        int y = (this.height - this.imageHeight) / 2;
        g.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);

        // Back to /pets button
        Component back = Component.literal("§7← Back to §e/pets");
        int bx = this.leftPos + 4;
        int by = this.topPos + this.imageHeight + 3;
        int bw = this.imageWidth - 8;
        boolean hovered = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 10;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 11, hovered ? 0x90303030 : 0x70101010);
        g.drawString(this.font, back, bx + 2, by + 1, 0xFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int bx = this.leftPos + 4;
            int by = this.topPos + this.imageHeight + 3;
            int bw = this.imageWidth - 8;
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + 10) {
                PacketDistributor.sendToServer(new C2SPetAction(C2SPetAction.OPEN_PANEL, new java.util.UUID(0, 0)));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

}
