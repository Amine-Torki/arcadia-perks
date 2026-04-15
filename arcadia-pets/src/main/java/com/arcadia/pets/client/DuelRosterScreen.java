package com.arcadia.pets.client;

import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetRarity;
import com.arcadia.pets.item.PetStat;
import com.arcadia.pets.network.C2SDuelRosterReady;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pre-duel screen where the player selects up to 3 pets from their collection
 * to form their battle roster.
 *
 * <p>Pets are displayed as compact cards. Selected pets are highlighted with a gold border.
 * A [Confirm] button appears once at least 1 pet is selected.</p>
 */
public class DuelRosterScreen extends Screen {

    private static final int CARD_W    = 70;
    private static final int CARD_H    = 82;
    private static final int CARD_GAP  = 6;
    private static final int CARDS_PER_ROW = 5;

    private final UUID          duelId;
    private final String        opponentName;
    private final List<PetData> collection = new ArrayList<>();
    private final List<UUID>    selected   = new ArrayList<>();

    // Scroll offset for large collections
    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 2;

    public DuelRosterScreen(UUID duelId, String opponentName, List<CompoundTag> petTags) {
        super(Component.translatable("arcadia_pets.gui.duel.roster"));
        this.duelId       = duelId;
        this.opponentName = opponentName;
        for (CompoundTag tag : petTags) {
            PetData pd = PetData.fromTag(tag);
            if (pd != null) collection.add(pd);
        }
    }

    @Override
    protected void init() {
        // No widget buttons — all interaction is handled via mouse clicks in render
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);

        int cx = width / 2;
        int cy = height / 2;

        // Title
        g.drawCenteredString(font, "§6⚔ Duel vs. " + opponentName, cx, 12, 0xFFFFDD);
        g.drawCenteredString(font, "§7Select 1–3 pets for your roster", cx, 23, 0x888888);

        // Roster slots (selected pet mini-cards at the top-right)
        renderRosterPreview(g, cx, 12);

        // Collection grid
        int gridStartX = cx - (CARDS_PER_ROW * (CARD_W + CARD_GAP) - CARD_GAP) / 2;
        int gridStartY = 48;

        int rowStart  = scrollOffset;
        int rowEnd    = Math.min(rowStart + VISIBLE_ROWS,
                (collection.size() + CARDS_PER_ROW - 1) / CARDS_PER_ROW);

        for (int row = rowStart; row < rowEnd; row++) {
            for (int col = 0; col < CARDS_PER_ROW; col++) {
                int idx = row * CARDS_PER_ROW + col;
                if (idx >= collection.size()) break;
                int cardX = gridStartX + col * (CARD_W + CARD_GAP);
                int cardY = gridStartY + (row - rowStart) * (CARD_H + CARD_GAP);
                renderPetCard(g, collection.get(idx), cardX, cardY, mouseX, mouseY);
            }
        }

        // Confirm button
        if (!selected.isEmpty()) {
            int btnW = 90, btnH = 14;
            int btnX = cx - btnW / 2;
            int btnY = gridStartY + VISIBLE_ROWS * (CARD_H + CARD_GAP) + 8;
            boolean hover = mouseX >= btnX && mouseX <= btnX + btnW
                    && mouseY >= btnY && mouseY <= btnY + btnH;
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH,
                    hover ? 0xFF3a7a3a : 0xFF2a5a2a);
            g.drawCenteredString(font, "§a✔ Confirm Roster (" + selected.size() + "/3)",
                    btnX + btnW / 2, btnY + 3, 0xFFFFFF);
        }

        // Scroll hint
        int totalRows = (collection.size() + CARDS_PER_ROW - 1) / CARDS_PER_ROW;
        if (totalRows > VISIBLE_ROWS) {
            g.drawCenteredString(font, "§8Scroll to see more", cx,
                    gridStartY + VISIBLE_ROWS * (CARD_H + CARD_GAP) + 30, 0x555555);
        }

        super.render(g, mouseX, mouseY, partial);
    }

    private void renderRosterPreview(GuiGraphics g, int cx, int topY) {
        int slotW = 52, slotH = 14, slotGap = 4;
        int startX = cx + 80;
        for (int i = 0; i < 3; i++) {
            int sx = startX + i * (slotW + slotGap);
            g.fill(sx, topY, sx + slotW, topY + slotH, 0x88333333);
            if (i < selected.size()) {
                UUID petId = selected.get(i);
                PetData pd = collection.stream()
                        .filter(p -> p.petId().equals(petId))
                        .findFirst().orElse(null);
                if (pd != null) {
                    int borderCol = pd.rarity().getColor().getColor() != null
                            ? pd.rarity().getColor().getColor() | 0xFF000000 : 0xFFFFFFFF;
                    g.fill(sx - 1, topY - 1, sx + slotW + 1, topY + slotH + 1, borderCol);
                    g.fill(sx, topY, sx + slotW, topY + slotH, 0xFF222233);
                    g.drawString(font, truncate(pd.customName() != null
                            ? pd.customName() : mobDisplayName(pd.mobType()), 7),
                            sx + 2, topY + 3, 0xFFFFDD, false);
                }
            } else {
                g.drawCenteredString(font, "§8Slot " + (i + 1), sx + slotW / 2, topY + 3, 0x555555);
            }
        }
    }

    private void renderPetCard(GuiGraphics g, PetData pd, int x, int y,
                                int mouseX, int mouseY) {
        boolean isSelected = selected.contains(pd.petId());
        boolean hover      = mouseX >= x && mouseX <= x + CARD_W
                && mouseY >= y && mouseY <= y + CARD_H;

        // Border colour: rarity or gold if selected
        int borderCol = isSelected ? 0xFFFFD700
                : (pd.rarity().getColor().getColor() != null
                ? pd.rarity().getColor().getColor() | 0xFF000000 : 0xFFFFFFFF);
        g.fill(x - 1, y - 1, x + CARD_W + 1, y + CARD_H + 1, borderCol);
        g.fill(x, y, x + CARD_W, y + CARD_H, hover ? 0xFF2a2a3e : 0xFF1a1a2e);

        // Rarity label
        g.drawString(font, pd.rarity().getStyledName(), x + 3, y + 3, 0xFFFFFF, false);

        // Pet name / mob type
        String name = pd.customName() != null ? pd.customName() : mobDisplayName(pd.mobType());
        g.drawString(font, "§f" + truncate(name, 9), x + 3, y + 13, 0xFFFFFF, false);

        // Stats mini-bar (POW / END / AGI in 3 rows)
        int statY = y + 25;
        renderMiniStat(g, "POW", pd.stats().getOrDefault(PetStat.POWER, 0),
                x + 3, statY, 0xFF4444);
        renderMiniStat(g, "END", pd.stats().getOrDefault(PetStat.ENDURANCE, 0),
                x + 3, statY + 9, 0x44FF44);
        renderMiniStat(g, "AGI", pd.stats().getOrDefault(PetStat.AGILITY, 0),
                x + 3, statY + 18, 0x44AAFF);

        // Total stars
        int total = pd.totalStars();
        g.drawString(font, "§e★" + total, x + 3, y + CARD_H - 11, 0xFFFFDD, false);

        // Selected indicator
        if (isSelected) {
            int selIdx = selected.indexOf(pd.petId());
            g.drawString(font, "§a[" + (selIdx + 1) + "]", x + CARD_W - 14, y + 3, 0xFFFFDD, false);
        }
    }

    private void renderMiniStat(GuiGraphics g, String label, int stars, int x, int y, int barColor) {
        g.drawString(font, "§8" + label, x, y, 0x888888, false);
        for (int s = 0; s < 5; s++) {
            int bx = x + 18 + s * 6;
            g.fill(bx, y + 1, bx + 5, y + 6,
                    s < stars ? (barColor | 0xFF000000) : 0xFF333333);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Check confirm button
        if (!selected.isEmpty()) {
            int cx   = width / 2;
            int gridStartY = 48;
            int btnW = 90, btnH = 14;
            int btnX = cx - btnW / 2;
            int btnY = gridStartY + VISIBLE_ROWS * (CARD_H + CARD_GAP) + 8;
            if (mouseX >= btnX && mouseX <= btnX + btnW
                    && mouseY >= btnY && mouseY <= btnY + btnH) {
                confirmRoster();
                return true;
            }
        }

        // Check pet card clicks
        int gridStartX = width / 2 - (CARDS_PER_ROW * (CARD_W + CARD_GAP) - CARD_GAP) / 2;
        int gridStartY = 48;
        int rowStart   = scrollOffset;
        int rowEnd     = Math.min(rowStart + VISIBLE_ROWS,
                (collection.size() + CARDS_PER_ROW - 1) / CARDS_PER_ROW);

        for (int row = rowStart; row < rowEnd; row++) {
            for (int col = 0; col < CARDS_PER_ROW; col++) {
                int idx = row * CARDS_PER_ROW + col;
                if (idx >= collection.size()) break;
                int cardX = gridStartX + col * (CARD_W + CARD_GAP);
                int cardY = gridStartY + (row - rowStart) * (CARD_H + CARD_GAP);
                if (mouseX >= cardX && mouseX <= cardX + CARD_W
                        && mouseY >= cardY && mouseY <= cardY + CARD_H) {
                    togglePet(collection.get(idx).petId());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int totalRows = (collection.size() + CARDS_PER_ROW - 1) / CARDS_PER_ROW;
        if (dy < 0 && scrollOffset < totalRows - VISIBLE_ROWS) scrollOffset++;
        if (dy > 0 && scrollOffset > 0) scrollOffset--;
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void togglePet(UUID petId) {
        if (selected.contains(petId)) {
            selected.remove(petId);
        } else if (selected.size() < 3) {
            selected.add(petId);
        }
    }

    private void confirmRoster() {
        PacketDistributor.sendToServer(new C2SDuelRosterReady(duelId, List.copyOf(selected)));
        // Close this screen — DuelScreen opens when S2CDuelState arrives
        Minecraft.getInstance().setScreen(null);
    }

    private static String mobDisplayName(String mobType) {
        int colon = mobType.indexOf(':');
        String raw = colon >= 0 ? mobType.substring(colon + 1) : mobType;
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1).replace('_', ' ');
    }

    private static String truncate(String s, int maxChars) {
        return s.length() <= maxChars ? s : s.substring(0, maxChars - 1) + "…";
    }
}
