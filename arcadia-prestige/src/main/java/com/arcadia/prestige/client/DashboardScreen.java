package com.arcadia.prestige.client;

import com.arcadia.lib.ArcadiaModRegistry;
import com.arcadia.lib.client.ArcadiaHubScreen;
import com.arcadia.lib.client.ArcadiaModCard;
import com.arcadia.lib.client.ArcadiaTheme;
import com.arcadia.prestige.network.C2SDashboardAction;
import com.arcadia.prestige.server.DashboardMenu;
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
        // Custom steampunk container — no chest texture
        ArcadiaTheme.drawContainerBg(graphics, this.leftPos, this.topPos, this.imageWidth, 6);
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
        // Centered title with copper theme + shadow
        int titleX = (this.imageWidth - this.font.width(displayTitle)) / 2;
        graphics.drawString(this.font, displayTitle, titleX + 1, 7, 0x22000000, false);
        graphics.drawString(this.font, displayTitle, titleX, 6, ArcadiaTheme.BRASS, false);
        // Player inventory label in warm tone
        graphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, ArcadiaTheme.TEXT_DIM, false);
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
        java.util.List<ArcadiaModCard> allCards = ArcadiaModRegistry.getCards();
        if (allCards.size() < 2) return; // no navigation needed with < 2 cards

        int currentTab = menu.getCurrentTab();
        // Find current card index in the sorted list
        int currentIdx = -1;
        for (int i = 0; i < allCards.size(); i++) {
            if (allCards.get(i).sortOrder() == currentTab) { currentIdx = i; break; }
        }
        if (currentIdx < 0) return;

        int prevIdx = (currentIdx + allCards.size() - 1) % allCards.size();
        int nextIdx = (currentIdx + 1) % allCards.size();

        int cardY = this.topPos + (int)(this.imageHeight * 0.30f) - MINI_H / 2;
        int leftX  = this.leftPos - MINI_GAP - MINI_W;
        int rightX = this.leftPos + this.imageWidth + MINI_GAP;

        if (leftX >= 0) {
            boolean hovered = mouseX >= leftX && mouseX < leftX + MINI_W
                    && mouseY >= cardY && mouseY < cardY + MINI_H;
            ArcadiaHubScreen.drawCard(g, allCards.get(prevIdx), leftX, cardY, MINI_W, MINI_H, hovered, 0.8f);
        }
        if (rightX + MINI_W <= this.width) {
            boolean hovered = mouseX >= rightX && mouseX < rightX + MINI_W
                    && mouseY >= cardY && mouseY < cardY + MINI_H;
            ArcadiaHubScreen.drawCard(g, allCards.get(nextIdx), rightX, cardY, MINI_W, MINI_H, hovered, 0.8f);
        }
    }

    // -------------------------------------------------------------------------
    // First-person hide toggle
    // -------------------------------------------------------------------------

    private void renderFirstPersonToggle(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hidden = PlayerEffectCache.isHideOwnEffectsFirstPerson();
        // Compact icon button: eye icon with toggle state
        int btnSize = 12;
        int bx = this.leftPos + this.imageWidth - btnSize - 4;
        int by = this.topPos + this.imageHeight + 3;
        boolean hovered = mouseX >= bx && mouseX <= bx + btnSize && mouseY >= by && mouseY <= by + btnSize;

        // Background
        graphics.fill(bx, by, bx + btnSize, by + btnSize, hovered ? 0xDD1E1A24 : 0xAA141018);
        ArcadiaTheme.drawBorder(graphics, bx, by, btnSize, btnSize,
                hovered ? ArcadiaTheme.COPPER : ArcadiaTheme.BORDER_IDLE);

        // Eye icon (crossed out when hidden)
        String icon = hidden ? "\uD83D\uDC41" : "\uD83D\uDC41";
        graphics.pose().pushPose();
        graphics.pose().translate(bx + 2, by + 2, 0);
        graphics.pose().scale(0.7f, 0.7f, 1f);
        graphics.drawString(this.font, icon, 0, 0,
                hidden ? 0xFF55CC55 : ArcadiaTheme.TEXT_DIM, false);
        graphics.pose().popPose();

        // Strikethrough line when NOT hidden (effects visible = eye open, no line)
        if (!hidden) {
            // no line needed — eye open = effects visible
        } else {
            // small green dot to indicate "hidden" state
            graphics.fill(bx + btnSize - 4, by + btnSize - 4, bx + btnSize - 1, by + btnSize - 1, 0xFF55CC55);
        }

        // Tooltip on hover
        if (hovered) {
            Component tooltip = Component.translatable(hidden
                    ? "arcadia_prestige.gui.cosmetics.toggle_on"
                    : "arcadia_prestige.gui.cosmetics.toggle_off");
            graphics.renderTooltip(this.font, tooltip, (int) mouseX, (int) mouseY);
        }
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
            java.util.List<ArcadiaModCard> allCards = ArcadiaModRegistry.getCards();
            int currentIdx = -1;
            for (int i = 0; i < allCards.size(); i++) {
                if (allCards.get(i).sortOrder() == currentTab) { currentIdx = i; break; }
            }

            int cardY = this.topPos + (int)(this.imageHeight * 0.30f) - MINI_H / 2;
            int leftX  = this.leftPos - MINI_GAP - MINI_W;
            int rightX = this.leftPos + this.imageWidth + MINI_GAP;

            // Left mini card
            if (currentIdx >= 0 && leftX >= 0
                    && mouseX >= leftX && mouseX < leftX + MINI_W
                    && mouseY >= cardY && mouseY < cardY + MINI_H) {
                playClick();
                int prevIdx = (currentIdx + allCards.size() - 1) % allCards.size();
                int prevTab = allCards.get(prevIdx).sortOrder();
                PacketDistributor.sendToServer(new C2SDashboardAction(C2SDashboardAction.SWITCH_TAB, String.valueOf(prevTab)));
                return true;
            }

            // Right mini card
            if (currentIdx >= 0 && rightX + MINI_W <= this.width
                    && mouseX >= rightX && mouseX < rightX + MINI_W
                    && mouseY >= cardY && mouseY < cardY + MINI_H) {
                playClick();
                int nextIdx = (currentIdx + 1) % allCards.size();
                int nextTab = allCards.get(nextIdx).sortOrder();
                PacketDistributor.sendToServer(new C2SDashboardAction(C2SDashboardAction.SWITCH_TAB, String.valueOf(nextTab)));
                return true;
            }

            // First-person toggle (compact icon button, bottom-right)
            if (currentTab == 0 || currentTab == 1) {
                int btnSize = 12;
                int bx = this.leftPos + this.imageWidth - btnSize - 4;
                int by = this.topPos + this.imageHeight + 3;
                if (mouseX >= bx && mouseX <= bx + btnSize && mouseY >= by && mouseY <= by + btnSize) {
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
                    com.arcadia.lib.ArcadiaModRegistry.executeClientAction("pets.open_guide");
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
                    com.arcadia.lib.ArcadiaModRegistry.executeClientAction("pets.open_hud_settings");
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
