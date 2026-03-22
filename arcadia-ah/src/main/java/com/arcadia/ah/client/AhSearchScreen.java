package com.arcadia.ah.client;

import com.arcadia.ah.network.C2SAhSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class AhSearchScreen extends Screen {

    private EditBox searchBox;
    private final String initialQuery;

    public AhSearchScreen(String initialQuery) {
        super(Component.literal("Search Auction House"));
        this.initialQuery = initialQuery;
    }

    public static void open(String currentQuery) {
        Minecraft.getInstance().setScreen(new AhSearchScreen(currentQuery));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        searchBox = new EditBox(this.font, cx - 100, cy - 10, 200, 20, Component.literal("Search..."));
        searchBox.setMaxLength(64);
        searchBox.setValue(initialQuery);
        searchBox.setFocused(true);
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.literal("Search"), btn -> confirm())
                .bounds(cx - 51, cy + 16, 50, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Clear"), btn -> {
            searchBox.setValue("");
            confirm();
        }).bounds(cx + 1, cy + 16, 50, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> cancel())
                .bounds(cx - 25, cy + 40, 50, 20).build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            confirm();
            return true;
        }
        if (keyCode == 256) { // Escape
            cancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirm() {
        String query = searchBox.getValue().trim();
        PacketDistributor.sendToServer(new C2SAhSearch(query));
        this.onClose();
    }

    private void cancel() {
        // Reopen dashboard at AH tab without changing the search
        PacketDistributor.sendToServer(new C2SAhSearch(initialQuery));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.fill(cx - 110, cy - 30, cx + 110, cy + 65, 0xCC000000);
        graphics.drawCenteredString(this.font, Component.literal("§6Search Auction House"),
                cx, cy - 24, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
