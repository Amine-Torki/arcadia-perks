package com.arcadia.prestige.client;

import com.arcadia.lib.client.ArcadiaTheme;
import com.arcadia.prestige.PrestigeCard;
import com.arcadia.prestige.network.C2SDashboardAction;
import com.arcadia.prestige.server.DashboardMenu;
import com.arcadia.pets.client.PetGuideScreen;
import com.arcadia.pets.client.PetHudSettingsScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class DashboardScreen extends AbstractContainerScreen<DashboardMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    // Mini card dimensions — slightly smaller than the hub cards (88×99)
    private static final int MINI_W = 70;
    private static final int MINI_H = 80;
    private static final int MINI_GAP = 6;

    public DashboardScreen(DashboardMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 222;
        this.imageWidth = 176;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        // Top section: 6 rows of slots + header
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, 6 * 18 + 17);
        // Bottom section: player inventory
        graphics.blit(TEXTURE, x, y + 6 * 18 + 17, 0, 126, this.imageWidth, 96);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Dynamic title per tab
        Component displayTitle = switch (menu.getCurrentTab()) {
            case 0 -> Component.translatable("arcadia_prestige.gui.tab.cosmetics");
            case 1 -> Component.translatable("arcadia_prestige.gui.tab.pets");
            case 2 -> Component.translatable("arcadia_prestige.gui.tab.daily");
            case 3 -> Component.translatable("arcadia_prestige.gui.tab.auction_house");
            default -> this.title;
        };
        // Centered title with copper theme
        int titleX = (this.imageWidth - this.font.width(displayTitle)) / 2;
        graphics.drawString(this.font, displayTitle, titleX + 1, 7, 0x22000000, false);
        graphics.drawString(this.font, displayTitle, titleX, 6, ArcadiaTheme.BRASS, false);
        graphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw mini tab cards before the inventory so they appear behind tooltips
        renderTabCards(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);

        int tab = menu.getCurrentTab();
        if (tab == 0 || tab == 1) {
            renderFirstPersonToggle(graphics, mouseX, mouseY);
        }
    }

    // -------------------------------------------------------------------------
    // Mini tab navigation cards (rendered outside the inventory GUI)
    // -------------------------------------------------------------------------

    private void renderTabCards(GuiGraphics g, int mouseX, int mouseY) {
        int currentTab = menu.getCurrentTab();
        int prevTab = (currentTab + 3) % 4;
        int nextTab = (currentTab + 1) % 4;

        // Center the card vertically at 30% of the total inventory height from the top
        int cardY = this.topPos + (int)(this.imageHeight * 0.30f) - MINI_H / 2;
        int leftX  = this.leftPos - MINI_GAP - MINI_W;
        int rightX = this.leftPos + this.imageWidth + MINI_GAP;

        if (leftX >= 0) {
            boolean hovered = mouseX >= leftX && mouseX < leftX + MINI_W
                    && mouseY >= cardY && mouseY < cardY + MINI_H;
            PrestigeHubScreen.drawCard(g, PrestigeCard.ALL.get(prevTab), leftX, cardY, MINI_W, MINI_H, hovered, 0.8f);
        }
        if (rightX + MINI_W <= this.width) {
            boolean hovered = mouseX >= rightX && mouseX < rightX + MINI_W
                    && mouseY >= cardY && mouseY < cardY + MINI_H;
            PrestigeHubScreen.drawCard(g, PrestigeCard.ALL.get(nextTab), rightX, cardY, MINI_W, MINI_H, hovered, 0.8f);
        }
    }

    // -------------------------------------------------------------------------
    // First-person hide toggle
    // -------------------------------------------------------------------------

    private void renderFirstPersonToggle(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hidden = PlayerEffectCache.isHideOwnEffectsFirstPerson();
        Component label = Component.translatable(hidden ? "arcadia_prestige.gui.cosmetics.hide_effects_on" : "arcadia_prestige.gui.cosmetics.hide_effects_off");
        int bx = this.leftPos + 4;
        int by = this.topPos + this.imageHeight + 3;
        int bw = this.imageWidth - 8;
        boolean hovered = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 10;
        graphics.fill(bx - 1, by - 1, bx + bw + 1, by + 11, hovered ? 0x90302818 : 0x70181210);
        graphics.drawString(this.font, label, bx + 2, by + 1, ArcadiaTheme.TEXT_PRIMARY, false);
    }

    // -------------------------------------------------------------------------
    // Mouse clicks
    // -------------------------------------------------------------------------

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int currentTab = menu.getCurrentTab();
            // Center the card vertically at 30% of the total inventory height from the top
        int cardY = this.topPos + (int)(this.imageHeight * 0.30f) - MINI_H / 2;
            int leftX  = this.leftPos - MINI_GAP - MINI_W;
            int rightX = this.leftPos + this.imageWidth + MINI_GAP;

            // Left mini card — navigate to prev tab (in-place refresh, no container reopen)
            if (leftX >= 0
                    && mouseX >= leftX && mouseX < leftX + MINI_W
                    && mouseY >= cardY && mouseY < cardY + MINI_H) {
                playClick();
                int prevTab = (currentTab + 3) % 4;
                PacketDistributor.sendToServer(new C2SDashboardAction(C2SDashboardAction.SWITCH_TAB, String.valueOf(prevTab)));
                return true;
            }

            // Right mini card — navigate to next tab (in-place refresh, no container reopen)
            if (rightX + MINI_W <= this.width
                    && mouseX >= rightX && mouseX < rightX + MINI_W
                    && mouseY >= cardY && mouseY < cardY + MINI_H) {
                playClick();
                int nextTab = (currentTab + 1) % 4;
                PacketDistributor.sendToServer(new C2SDashboardAction(C2SDashboardAction.SWITCH_TAB, String.valueOf(nextTab)));
                return true;
            }

            // First-person toggle
            if (currentTab == 0 || currentTab == 1) {
                int bx = this.leftPos + 4;
                int by = this.topPos + this.imageHeight + 3;
                int bw = this.imageWidth - 8;
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 10) {
                    playClick();
                    PlayerEffectCache.toggleHideOwnEffectsFirstPerson();
                    return true;
                }
            }

            // Preview button (slot 47, Cosmetics tab)
            if (currentTab == 0) {
                var slot47 = this.menu.slots.get(47);
                int sx = this.leftPos + slot47.x;
                int sy = this.topPos + slot47.y;
                if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                    playClick();
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    String effect = mc.player != null
                            ? PlayerEffectCache.getEffect(mc.player.getUUID())
                            : null;
                    EffectPreviewHandler.startPreview(effect);
                    return true;
                }
            }

            // Guide book (slot 47, Pets tab)
            if (currentTab == 1) {
                var slot47 = this.menu.slots.get(47);
                int sx = this.leftPos + slot47.x;
                int sy = this.topPos + slot47.y;
                if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                    playClick();
                    net.minecraft.client.Minecraft.getInstance().setScreen(new PetGuideScreen());
                    return true;
                }
            }

            // HUD Settings (slot 48, Pets tab)
            if (currentTab == 1) {
                var slot48 = this.menu.slots.get(48);
                int sx = this.leftPos + slot48.x;
                int sy = this.topPos + slot48.y;
                if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                    playClick();
                    net.minecraft.client.Minecraft.getInstance().setScreen(new PetHudSettingsScreen());
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
