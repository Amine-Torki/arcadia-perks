package com.arcadia.pets.client;

import com.arcadia.pets.config.PetPoolConfig;
import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetRarity;
import com.arcadia.pets.item.PetRoller;
import com.arcadia.pets.item.PetStat;
import com.arcadia.pets.skill.SkillInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Multi-spin reveal screen for opening 2–4 pet crates simultaneously
 * (Shift + Right-click consumes the whole stack).
 *
 * <p>Phase 0: N roulette strips spin in parallel and land with a ~0.5 s stagger.</p>
 * <p>Phase 1: N minicards shown in a row at the bottom. Hover a card to inspect its
 * full stat/skill detail floating above. The detail card persists until another card
 * is hovered. Click anywhere (or press ESC) to close.</p>
 */
public class PetMultiRevealScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int CARD_W    = 60;
    private static final int CARD_H    = 70;
    private static final int CARD_GAP  = 4;
    private static final int STRIP_GAP = 6;
    private static final float MIN_VEL = 2.0f;
    /** Max total height of the roulette area — kept constant for 1-3 strips;
     *  for 4 strips the per-strip height is reduced to fit in the same space. */
    private static final int MAX_STRIPS_H = 3 * (CARD_H + 10) + 2 * STRIP_GAP; // 252px

    private static final int MINI_W   = 82;
    private static final int MINI_H   = 100;
    private static final int MINI_GAP = 14;
    private static final int DETAIL_W = 180;

    // ── Fields ────────────────────────────────────────────────────────────────
    private final List<PetData> results;
    private final PetRarity minimumRarity;
    private final int count;

    // Per-strip physics
    private final float[]            scrollTarget;
    private final float[]            scrollPos;
    private final float[]            prevScrollPos;
    private final float[]            scrollVel;
    private final float[]            decay;
    private final boolean[]          landed;
    private final int[]              resultIdx;
    private final List<List<PetData>> strips;
    private final int[]              lastCardUnder;

    private int phase;       // 0 = spinning, 1 = minicards
    private int landedCount;
    private int ticksPhase1;

    /** Last card whose detail card is shown (sticky until another is hovered). */
    private int currentDetailCard = -1;

    // Stars pop animation (phase 1)
    private final int[]     starsRevealed;   // how many stars shown so far per card
    private final boolean[] specialTriggered; // whether we've fired the 25+ special event per card

    // ── Constructor ───────────────────────────────────────────────────────────

    public PetMultiRevealScreen(List<PetData> results, PetRarity minimumRarity) {
        super(Component.translatable("arcadia_prestige.gui.multi_reveal.title"));
        this.results = results;
        this.minimumRarity = minimumRarity;
        this.count = results.size();

        scrollTarget     = new float[count];
        scrollPos        = new float[count];
        prevScrollPos    = new float[count];
        scrollVel        = new float[count];
        decay            = new float[count];
        landed           = new boolean[count];
        resultIdx        = new int[count];
        strips           = new ArrayList<>(count);
        lastCardUnder    = new int[count];
        starsRevealed    = new int[count];
        specialTriggered = new boolean[count];
        Arrays.fill(lastCardUnder, -1);

        ThreadLocalRandom rand = ThreadLocalRandom.current();

        // Each strip has ~20 extra filler cards vs the previous → ~0.5 s stagger.
        for (int i = 0; i < count; i++) {
            int ri = 55 + i * 20 + rand.nextInt(6);
            resultIdx[i] = ri;

            List<PetData> strip = new ArrayList<>(ri + 14);
            for (int j = 0; j < ri; j++) strip.add(makeFiller(minimumRarity, rand));
            strip.add(results.get(i));
            for (int j = 0; j < 8 + rand.nextInt(5); j++) strip.add(makeFiller(minimumRarity, rand));
            strips.add(strip);

            float target = ri * (CARD_W + CARD_GAP) + CARD_W / 2.0f;
            float d = 0.965f + rand.nextFloat() * 0.013f;
            scrollTarget[i] = target;
            decay[i] = d;
            scrollVel[i] = target * (1f - d);
        }
    }

    private static PetData makeFiller(PetRarity min, ThreadLocalRandom rand) {
        PetRarity rarity = PetRarity.rollRarity(min);
        List<String> pool = PetPoolConfig.getPool(rarity);
        String mob = pool.get(rand.nextInt(pool.size()));
        return new PetData(UUID.randomUUID(), mob, rarity,
                PetRoller.rollStats(rarity), false, null, 100, 100, Collections.emptyList());
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        HudSettings.ensureLoaded();
        if (HudSettings.reducedMotion) {
            for (int i = 0; i < count; i++) {
                scrollPos[i] = scrollTarget[i];
                prevScrollPos[i] = scrollTarget[i];
                scrollVel[i] = 0;
                landed[i] = true;
                starsRevealed[i] = results.get(i).totalStars(); // skip animation
            }
            landedCount = count;
            phase = 1;
        }
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (phase == 0) {
            boolean clickedThisTick = false;
            for (int i = 0; i < count; i++) {
                if (landed[i]) continue;
                prevScrollPos[i] = scrollPos[i];
                scrollPos[i] += scrollVel[i];
                scrollVel[i] = Math.max(MIN_VEL, scrollVel[i] * decay[i]);

                // Roulette tick sound — one sound per tick across all strips (MASTER source)
                int cardUnder = (int) ((scrollPos[i] - CARD_W / 2.0f) / (CARD_W + CARD_GAP));
                if (cardUnder != lastCardUnder[i] && cardUnder >= 0) {
                    lastCardUnder[i] = cardUnder;
                    if (!clickedThisTick && minecraft != null) {
                        clickedThisTick = true;
                        float speed = Math.min(1f, (scrollVel[i] - MIN_VEL)
                                / (scrollTarget[i] * (1f - decay[i]) - MIN_VEL + 0.001f));
                        minecraft.getSoundManager().play(
                                PetRevealScreen.uiSound(SoundEvents.UI_BUTTON_CLICK, 0.3f, 1.3f - speed * 0.8f));
                    }
                }

                // Snap when target reached; play a rarity-appropriate landing sound
                if (scrollPos[i] >= scrollTarget[i]) {
                    scrollPos[i] = scrollTarget[i];
                    prevScrollPos[i] = scrollTarget[i];
                    scrollVel[i] = 0;
                    landed[i] = true;
                    landedCount++;
                    if (minecraft != null) {
                        PetRevealScreen.playLandingSound(minecraft, results.get(i).rarity());
                    }
                    if (landedCount == count) phase = 1;
                }
            }
        } else {
            ticksPhase1++;
            // Stars pop animation: reveal 1 star every 2 ticks per card, staggered by card index
            if (minecraft != null) {
                for (int i = 0; i < count; i++) {
                    int total = results.get(i).totalStars();
                    if (starsRevealed[i] >= total) continue;
                    int startTick = i * 8; // 8-tick stagger between cards
                    int elapsed = ticksPhase1 - startTick;
                    if (elapsed < 0) continue;
                    int targetRevealed = Math.min(total, elapsed / 2 + 1);
                    if (targetRevealed > starsRevealed[i]) {
                        starsRevealed[i] = targetRevealed;
                        // XP ding per star
                        minecraft.getSoundManager().play(
                                PetRevealScreen.uiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.25f, 0.9f + starsRevealed[i] * 0.04f));
                    }
                    // 25+ stars special event when fully revealed
                    if (starsRevealed[i] >= total && total >= 25 && !specialTriggered[i]) {
                        specialTriggered[i] = true;
                        minecraft.getSoundManager().play(
                                PetRevealScreen.uiSound(SoundEvents.TOTEM_USE, 0.7f, 1.1f));
                    }
                }
            }
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        if (phase == 0) {
            renderStrips(g, partialTick);
        } else {
            g.fill(0, 0, width, height, 0x99000000);
            renderMiniCards(g, mouseX, mouseY);
            if (currentDetailCard >= 0) renderDetailCard(g, currentDetailCard);
            if (ticksPhase1 > 30) {
                int alpha = 120 + (int) (60 * Math.sin(ticksPhase1 * 0.15));
                g.drawCenteredString(font,
                        Component.translatable("arcadia_prestige.gui.multi_reveal.close"),
                        width / 2, height - 14, (alpha << 24) | 0xFFFFFF);
            }
        }
    }

    // Phase 0 ─────────────────────────────────────────────────────────────────

    private void renderStrips(GuiGraphics g, float partialTick) {
        // Keep total height constant regardless of strip count: cap at 3-strip height
        int slotH  = count <= 3 ? CARD_H + 10 : (MAX_STRIPS_H - (count - 1) * STRIP_GAP) / count;
        int totalH = count * slotH + (count - 1) * STRIP_GAP;
        int areaTop = height / 2 - totalH / 2;
        int cx      = width / 2;

        g.drawCenteredString(font,
                Component.translatable("arcadia_prestige.gui.multi_reveal.rolling", count)
                        .withStyle(s -> s.withBold(true)),
                cx, areaTop - 22, 0xFFFFD700);

        for (int i = 0; i < count; i++) {
            int stripTop = areaTop + i * (slotH + STRIP_GAP);
            int cardTopY = stripTop + 5;
            float scroll = prevScrollPos[i] + (scrollPos[i] - prevScrollPos[i]) * partialTick;

            g.fill(0, stripTop, width, stripTop + slotH, 0xCC000000);

            int effCardH = slotH - 10; // actual card height = slot minus top+bottom padding
            List<PetData> strip = strips.get(i);
            for (int j = 0; j < strip.size(); j++) {
                int cardCX = (int) (j * (CARD_W + CARD_GAP) + CARD_W / 2.0f - scroll);
                int sx = cx + cardCX;
                if (sx < -CARD_W || sx > width + CARD_W) continue;
                drawRouletteCard(g, sx - CARD_W / 2, cardTopY, strip.get(j),
                        j == resultIdx[i] && landed[i], effCardH);
            }

            // Golden selector frame
            int sl = cx - CARD_W / 2 - 2, st = stripTop - 1;
            int sr = cx + CARD_W / 2 + 2, sb = stripTop + slotH + 1;
            g.fill(sl, st, sr, st + 2, 0xFFFFD700);
            g.fill(sl, sb - 2, sr, sb, 0xFFFFD700);
            g.fill(sl, st, sl + 2, sb, 0xFFFFD700);
            g.fill(sr - 2, st, sr, sb, 0xFFFFD700);
        }
    }

    private void drawRouletteCard(GuiGraphics g, int x, int y, PetData data, boolean selected, int cardH) {
        int rc = rarityColor(data.rarity());
        g.fill(x, y, x + CARD_W, y + cardH, 0xCC000000 | (rc & 0x00FFFFFF));
        g.fill(x + 2, y + 2, x + CARD_W - 2, y + cardH - 2, 0xCC101020);
        if (selected) {
            g.fill(x, y, x + CARD_W, y + 2, 0xFFFFD700);
            g.fill(x, y + cardH - 2, x + CARD_W, y + cardH, 0xFFFFD700);
            g.fill(x, y, x + 2, y + cardH, 0xFFFFD700);
            g.fill(x + CARD_W - 2, y, x + CARD_W, y + cardH, 0xFFFFD700);
        }
        g.drawCenteredString(font, data.rarity().getDisplayName(),
                x + CARD_W / 2, y + 8, chatColor(data.rarity()));
        g.drawCenteredString(font, mobName(data.mobType(), 8),
                x + CARD_W / 2, y + 26, 0xFFFFFF);
        g.drawCenteredString(font, data.totalStars() + "\u2605",
                x + CARD_W / 2, y + cardH - 18, 0xFFD700);
    }

    // Phase 1 minicards ───────────────────────────────────────────────────────

    private void renderMiniCards(GuiGraphics g, int mouseX, int mouseY) {
        int totalW = count * MINI_W + (count - 1) * MINI_GAP;
        int startX = width / 2 - totalW / 2;
        int cardY  = height - MINI_H - 26;

        boolean anyHovered = false;
        for (int i = 0; i < count; i++) {
            int cx = startX + i * (MINI_W + MINI_GAP);
            boolean hover = mouseX >= cx && mouseX < cx + MINI_W
                    && mouseY >= cardY && mouseY < cardY + MINI_H;
            if (hover) { currentDetailCard = i; anyHovered = true; }
            drawMiniCard(g, cx, cardY, results.get(i), hover || i == currentDetailCard, i);
        }

        if (!anyHovered && currentDetailCard < 0 && ticksPhase1 > 10) {
            g.drawCenteredString(font,
                    Component.translatable("arcadia_prestige.gui.multi_reveal.hint"),
                    width / 2, cardY - 14, 0x88FFFFFF);
        }
    }

    private void drawMiniCard(GuiGraphics g, int x, int y, PetData data, boolean active, int cardIdx) {
        int rc = rarityColor(data.rarity());
        int revealed = starsRevealed[cardIdx];
        int total = data.totalStars();
        boolean fullyRevealed = revealed >= total;
        boolean special = fullyRevealed && total >= 25;

        // Gold glow for 25+ stars once fully revealed
        if (special) {
            int ga = 50 + (int) (30 * Math.sin(ticksPhase1 * 0.2));
            g.fill(x - 4, y - 4, x + MINI_W + 4, y + MINI_H + 4, (ga << 24) | 0xFFD700);
        }

        int border = active ? rc : (rc & 0x00FFFFFF) | 0xAA000000;
        g.fill(x, y, x + MINI_W, y + MINI_H, active ? 0xE8181828 : 0xCC101020);
        g.fill(x, y, x + MINI_W, y + 2, border);
        g.fill(x, y + MINI_H - 2, x + MINI_W, y + MINI_H, border);
        g.fill(x, y, x + 2, y + MINI_H, border);
        g.fill(x + MINI_W - 2, y, x + MINI_W, y + MINI_H, border);

        g.drawCenteredString(font, data.rarity().getDisplayName(),
                x + MINI_W / 2, y + 8, chatColor(data.rarity()));
        g.drawCenteredString(font,
                Component.literal(mobName(data.mobType(), 9)).withStyle(s -> s.withBold(true)),
                x + MINI_W / 2, y + 26, 0xFFFFFF);
        // Stars pop in one at a time
        g.drawCenteredString(font, revealed + " \u2605",
                x + MINI_W / 2, y + MINI_H - 22, special ? 0xFFD700 : 0xCCAA00);
        if (active) {
            g.drawCenteredString(font,
                    Component.translatable("arcadia_prestige.gui.multi_reveal.inspect"),
                    x + MINI_W / 2, y + MINI_H - 10, 0x88FFFFFF);
        }
    }

    // Detail card (floats above minicard row) ─────────────────────────────────

    private void renderDetailCard(GuiGraphics g, int idx) {
        PetData data = results.get(idx);
        int skillCount = data.skills().size();
        int cardH = 155 + (skillCount > 0 ? 14 + skillCount * 14 : 0);

        // Position: just above the minicard row, clamped to screen top
        int miniCardY = height - MINI_H - 26;
        int cardX = width / 2 - DETAIL_W / 2;
        int cardY = Math.max(6, miniCardY - cardH - 10);

        int rc  = rarityColor(data.rarity());
        int cx  = width / 2;

        // Glow for legendary / mythic
        if (data.rarity() == PetRarity.LEGENDARY || data.rarity() == PetRarity.MYTHIC) {
            int ga = 40 + (int) (20 * Math.sin(ticksPhase1 * 0.1));
            int glow = (ga << 24) | (rc & 0x00FFFFFF);
            g.fill(cardX - 8, cardY - 8, cardX + DETAIL_W + 8, cardY + cardH + 8, glow);
            g.fill(cardX - 4, cardY - 4, cardX + DETAIL_W + 4, cardY + cardH + 4, glow);
        }

        g.fill(cardX, cardY, cardX + DETAIL_W, cardY + cardH, 0xF0101020);
        g.fill(cardX, cardY, cardX + DETAIL_W, cardY + 3, rc);
        g.fill(cardX, cardY + cardH - 2, cardX + DETAIL_W, cardY + cardH, rc);
        g.fill(cardX, cardY, cardX + 2, cardY + cardH, rc);
        g.fill(cardX + DETAIL_W - 2, cardY, cardX + DETAIL_W, cardY + cardH, rc);

        int ty = cardY + 12;

        // Rarity + mob name
        g.drawCenteredString(font,
                Component.literal(data.rarity().getDisplayName()).withStyle(s -> s.withBold(true)),
                cx, ty, chatColor(data.rarity()));
        ty += 14;
        g.drawCenteredString(font,
                Component.literal(mobName(data.mobType(), 50)).withStyle(s -> s.withBold(true)),
                cx, ty, 0xFFFFFF);
        ty += 18;

        // Stars — two rows of 15
        int total = data.totalStars();
        String emptyRow = "\u2606".repeat(15);
        int rowW = font.width(emptyRow);
        int rowX = cx - rowW / 2;
        g.drawString(font, emptyRow, rowX, ty, 0x444444, false);
        int f1 = Math.min(total, 15);
        if (f1 > 0) g.drawString(font, "\u2605".repeat(f1), rowX, ty, 0xFFD700, false);
        ty += 10;
        g.drawString(font, emptyRow, rowX, ty, 0x444444, false);
        int f2 = Math.max(0, total - 15);
        if (f2 > 0) g.drawString(font, "\u2605".repeat(f2), rowX, ty, 0xFFD700, false);
        ty += 10;
        g.drawCenteredString(font,
                Component.translatable("arcadia_prestige.gui.reveal.total_stars", total),
                cx, ty, 0xAAAAAA);
        ty += 14;

        // Divider
        g.fill(cardX + 10, ty, cardX + DETAIL_W - 10, ty + 1, 0x60FFFFFF);
        ty += 7;

        // Stats — 2 columns × 3 rows
        PetStat[] allStats = PetStat.values();
        int col0 = cardX + 10, col1 = cardX + DETAIL_W / 2 + 2;
        for (int i = 0; i < allStats.length; i++) {
            PetStat stat = allStats[i];
            int sx   = (i / 3 == 0) ? col0 : col1;
            int sy   = ty + (i % 3) * 13;
            int stars = data.stats().getOrDefault(stat, 0);
            String lbl = stat.getIcon() + " ";
            g.drawString(font, lbl, sx, sy, 0xAAAAAA, false);
            int lw = font.width(lbl);
            g.drawString(font, "\u2606\u2606\u2606\u2606\u2606", sx + lw, sy, 0x444444, false);
            if (stars > 0) g.drawString(font, "\u2605".repeat(stars), sx + lw, sy, 0xFFD700, false);
        }

        // Skills
        List<SkillInstance> skills = data.skills();
        if (!skills.isEmpty()) {
            int sy = ty + 3 * 13 + 5;
            g.fill(cardX + 10, sy, cardX + DETAIL_W - 10, sy + 1, 0x40FFFFFF);
            sy += 5;
            g.drawCenteredString(font,
                    Component.translatable("arcadia_prestige.gui.pet.skills_label")
                            .withStyle(ChatFormatting.GOLD),
                    cx, sy, 0xFFAA00);
            sy += 9;
            for (SkillInstance inst : skills) {
                if (inst.level() == 0) {
                    g.drawString(font, "\uD83D\uDD12 ???  [Locked]", cardX + 14, sy, 0x555566, false);
                } else {
                    String name  = inst.skill().getDisplayName().getString();
                    String badge = " [Lv " + inst.level() + "]";
                    g.drawString(font, "\u2605 " + name, cardX + 14, sy, 0xDDDDDD, false);
                    int lvColor = inst.level() >= 10 ? 0xFFD700 : inst.level() >= 7 ? 0xAA55FF : 0x5588FF;
                    g.drawString(font, badge, cardX + 14 + font.width("\u2605 " + name), sy, lvColor, false);
                }
                sy += 14;
            }
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { // ESC
            if (phase == 0) {
                for (int i = 0; i < count; i++) {
                    scrollPos[i] = scrollTarget[i];
                    prevScrollPos[i] = scrollTarget[i];
                    scrollVel[i] = 0;
                    landed[i] = true;
                    starsRevealed[i] = results.get(i).totalStars(); // skip star animation
                }
                landedCount = count;
                phase = 1;
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (phase == 1 && ticksPhase1 > 20) { onClose(); return true; }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Static helpers ────────────────────────────────────────────────────────

    private static String mobName(String mobType, int maxLen) {
        String n = mobType.contains(":") ? mobType.substring(mobType.indexOf(':') + 1) : mobType;
        if (n.length() > maxLen) n = n.substring(0, maxLen - 1) + ".";
        return n.isEmpty() ? n : Character.toUpperCase(n.charAt(0)) + n.substring(1).replace('_', ' ');
    }

    private static int rarityColor(PetRarity r) {
        return switch (r) {
            case COMMON    -> 0xFF808080;
            case UNCOMMON  -> 0xFF2ecc71;
            case RARE      -> 0xFF3498db;
            case EPIC      -> 0xFF9b59b6;
            case LEGENDARY -> 0xFFf39c12;
            case MYTHIC    -> 0xFFe74c3c;
        };
    }

    private static int chatColor(PetRarity r) {
        Integer c = r.getColor().getColor();
        return c != null ? c : 0xFFFFFF;
    }
}
