package com.arcadia.pets.client;


import com.arcadia.lib.client.ArcadiaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import com.arcadia.pets.network.C2SPetAction;

/**
 * A 3-page in-game guide explaining the pet system:
 * genes, skill types, feeding, and movement modes.
 */
public class PetGuideScreen extends Screen {

    private static final int W = 255, H = 200;
    private static final int PAGE_COUNT = 3;
    private int page = 0;
    private int left, top;

    public PetGuideScreen() {
        super(Component.translatable("arcadia_pets.guide.title"));
    }

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top  = (this.height - H) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);

        // Themed background panel
        ArcadiaTheme.drawPanel(g, left, top, W, H, false);

        // Title with copper theme
        Component title = Component.translatable("arcadia_pets.guide.page" + page + ".title");
        g.drawCenteredString(this.font, title, left + W / 2 + 1, top + 7, 0x22000000);
        g.drawCenteredString(this.font, title, left + W / 2, top + 6, ArcadiaTheme.BRASS);

        // Content lines
        int y = top + 22;
        int x = left + 8;
        int lineH = 10;
        int maxLines = 14;
        for (int i = 0; i < maxLines; i++) {
            String key = "arcadia_pets.guide.page" + page + ".line" + i;
            Component line = Component.translatable(key);
            String text = line.getString();
            // Skip lines whose translation key wasn't found (returns key itself)
            if (text.equals(key)) continue;
            // Color coding: lines starting with special markers
            int color = 0xFFCCCCCC;
            if (text.startsWith("!r")) { color = 0xFFFF5555; text = text.substring(2); }
            else if (text.startsWith("!w")) { color = 0xFFFFAA55; text = text.substring(2); }
            else if (text.startsWith("!g")) { color = 0xFF77DD77; text = text.substring(2); }
            else if (text.startsWith("!b")) { color = 0xFF77BBFF; text = text.substring(2); }
            else if (text.startsWith("!p")) { color = 0xFFBB77FF; text = text.substring(2); }
            else if (text.startsWith("!y")) { color = 0xFFFFFF77; text = text.substring(2); }
            else if (text.startsWith("!o")) { color = 0xFFFFBB77; text = text.substring(2); }
            else if (text.startsWith("!l")) { color = 0xFF77FFBB; text = text.substring(2); }
            else if (text.startsWith("!t")) { color = 0xFFFFD700; text = text.substring(2); }
            else if (text.startsWith("!h")) { color = 0xFFFFFFFF; text = text.substring(2); }
            else if (text.startsWith("!d")) { color = 0xFFAAAAAA; text = text.substring(2); }
            else if (text.startsWith("!s")) { color = 0xFF99BB99; text = text.substring(2); }
            g.drawString(this.font, text, x, y, color, false);
            y += lineH;
            // Extra spacing after blank-ish lines
            if (text.isEmpty()) y += 2;
        }

        // Navigation
        int navY = top + H - 36;
        if (page > 0) {
            Component prev = Component.translatable("arcadia_pets.guide.prev");
            g.drawString(this.font, prev, left + 8, navY, 0xFFAAAAFF, false);
        }
        String pageStr = (page + 1) + " / " + PAGE_COUNT;
        g.drawCenteredString(this.font, pageStr, left + W / 2, navY, 0xFF888888);
        if (page < PAGE_COUNT - 1) {
            Component next = Component.translatable("arcadia_pets.guide.next");
            g.drawString(this.font, next, left + W - 8 - this.font.width(next), navY, 0xFFAAAAFF, false);
        }

        // Back button box
        int btnX = left + 8;
        int btnY = top + H - 18;
        int btnW = W - 16;
        int btnH = 12;
        boolean btnHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHovered ? 0x90382818 : 0x70201510);
        Component back = Component.translatable("arcadia_pets.guide.back");
        g.drawCenteredString(this.font, back, left + W / 2, btnY + 2, ArcadiaTheme.TEXT_PRIMARY);
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int navY = top + H - 36;
        if (page > 0 && mx >= left + 8 && mx < left + 60 && my >= navY && my < navY + 10) {
            playClick();
            page--;
            return true;
        }
        if (page < PAGE_COUNT - 1 && mx >= left + W - 60 && mx < left + W - 8 && my >= navY && my < navY + 10) {
            playClick();
            page++;
            return true;
        }
        // Back button box
        int btnX = left + 8;
        int btnY = top + H - 18;
        int btnW = W - 16;
        if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + 12) {
            playClick();
            PacketDistributor.sendToServer(new C2SPetAction(C2SPetAction.OPEN_PANEL, new java.util.UUID(0, 0)));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
