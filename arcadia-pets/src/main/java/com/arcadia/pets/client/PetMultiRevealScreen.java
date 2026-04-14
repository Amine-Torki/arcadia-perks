package com.arcadia.pets.client;

import com.arcadia.lib.client.ArcadiaTheme;
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

    /** Effective card width — shrunk to 80% of CARD_W when count == 4 so all strips fit. */
    private final int effCardW;

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
    private final int[]     melodyStartTick; // ticksPhase1 when 25+ melody started (-1 = not started)

    /** Zelda-chest-style noteblock pitches played 4 ticks apart on 25+ stars. */
    private static final float[] MELODY_PITCHES = { 0.89f, 1.0f, 1.26f, 1.587f };

    // ── Constructor ───────────────────────────────────────────────────────────

    public PetMultiRevealScreen(List<PetData> results, PetRarity minimumRarity) {
        super(Component.translatable("arcadia_pets.gui.multi_reveal.title"));
        this.results = results;
        this.minimumRarity = minimumRarity;
        this.count = results.size();
        this.effCardW = count <= 3 ? CARD_W : (int) (CARD_W * 0.8f);

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
        melodyStartTick  = new int[count];
        Arrays.fill(lastCardUnder, -1);
        Arrays.fill(melodyStartTick, -1);

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

            float target = ri * (effCardW + CARD_GAP) + effCardW / 2.0f;
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
                int cardUnder = (int) ((scrollPos[i] - effCardW / 2.0f) / (effCardW + CARD_GAP));
                if (cardUnder != lastCardUnder[i] && cardUnder >= 0) {
                    lastCardUnder[i] = cardUnder;
                    if (!clickedThisTick && minecraft != null) {
                        clickedThisTick = true;
                        float speed = Math.min(1f, (scrollVel[i] - MIN_VEL)
                                / (scrollTarget[i] * (1f - decay[i]) - MIN_VEL + 0.001f));
                        minecraft.getSoundManager().play(
                                PetRevealScreen.uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1.3f - speed * 0.8f));
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
                    // 25+ stars special event when fully revealed — start zelda melody
                    if (starsRevealed[i] >= total && total >= 25 && !specialTriggered[i]) {
                        specialTriggered[i] = true;
                        melodyStartTick[i] = ticksPhase1;
                    }
                }
            }
            // Zelda-chest melody: 4 notes, 4 ticks apart
            for (int i = 0; i < count; i++) {
                if (melodyStartTick[i] < 0) continue;
                int elapsed = ticksPhase1 - melodyStartTick[i];
                int noteIdx = elapsed / 4;
                if (elapsed % 4 == 0 && noteIdx < MELODY_PITCHES.length) {
                    // First 3 notes: harp; last note: pling for the "ding" finish
                    net.minecraft.sounds.SoundEvent se = noteIdx < MELODY_PITCHES.length - 1
                            ? SoundEvents.NOTE_BLOCK_HARP.value()
                            : SoundEvents.NOTE_BLOCK_PLING.value();
                    minecraft.getSoundManager().play(
                            PetRevealScreen.uiSound(se, 0.8f, MELODY_PITCHES[noteIdx]));
                }
                if (noteIdx >= MELODY_PITCHES.length) melodyStartTick[i] = -1; // done
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
            g.fill(0, 0, width, height, ArcadiaTheme.OVERLAY_BG);
            renderMiniCards(g, mouseX, mouseY);
            if (currentDetailCard >= 0) renderDetailCard(g, currentDetailCard);
            if (ticksPhase1 > 30) {
                int alpha = 120 + (int) (60 * Math.sin(ticksPhase1 * 0.15));
                g.drawCenteredString(font,
                        Component.translatable("arcadia_pets.gui.multi_reveal.close"),
                        width / 2, height - 14, (alpha << 24) | 0xFFFFFF);
            }
        }
    }

    // Phase 0 ─────────────────────────────────────────────────────────────────

    private void renderStrips(GuiGraphics g, float partialTick) {
        // Keep total height constant regardless of strip count: cap at 3-strip height
        int slotH    = count <= 3 ? CARD_H + 10 : (MAX_STRIPS_H - (count - 1) * STRIP_GAP) / count;
        int totalH   = count * slotH + (count - 1) * STRIP_GAP;
        int areaTop  = height / 2 - totalH / 2;
        int cx       = width / 2;

        // For 4 strips, narrow the cards and shrink card text to 0.8× so they all fit comfortably.
        float fontScale = count <= 3 ? 1.0f : 0.8f;

        ArcadiaTheme.drawTitleBar(g,
                Component.translatable("arcadia_pets.gui.multi_reveal.rolling", count)
                        .withStyle(s -> s.withBold(true)),
                cx, areaTop - 22, 160);

        for (int i = 0; i < count; i++) {
            int stripTop = areaTop + i * (slotH + STRIP_GAP);
            int cardTopY = stripTop + 5;
            float scroll = prevScrollPos[i] + (scrollPos[i] - prevScrollPos[i]) * partialTick;

            // Steampunk strip background
            g.fill(0, stripTop, width, stripTop + slotH, 0xDD0E0B14);
            g.fill(0, stripTop, width, stripTop + 2, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x55));
            g.fill(0, stripTop + slotH - 2, width, stripTop + slotH, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x55));

            int effCardH = slotH - 10;
            List<PetData> strip = strips.get(i);
            for (int j = 0; j < strip.size(); j++) {
                int cardCX = (int) (j * (effCardW + CARD_GAP) + effCardW / 2.0f - scroll);
                int sx = cx + cardCX;
                if (sx < -effCardW || sx > width + effCardW) continue;
                drawRouletteCard(g, sx - effCardW / 2, cardTopY, strip.get(j),
                        j == resultIdx[i] && landed[i], effCardH, effCardW, fontScale);
            }

            // Steampunk selector frame
            int sl = cx - effCardW / 2 - 2, st = stripTop - 1;
            int sw = effCardW + 4, sh = slotH + 2;
            ArcadiaTheme.drawGlow(g, sl, st, sw, sh, ArcadiaTheme.BRASS);
            ArcadiaTheme.drawBorder(g, sl, st, sw, sh, ArcadiaTheme.COPPER);
        }
    }

    private void drawRouletteCard(GuiGraphics g, int x, int y, PetData data, boolean selected,
                                  int cardH, int cardW, float fontScale) {
        int rc = rarityColor(data.rarity());
        if (selected) ArcadiaTheme.drawGlow(g, x, y, cardW, cardH, rc);
        ArcadiaTheme.drawPanel(g, x, y, cardW, cardH, selected, rc);

        // Scale text for narrow cards (4-strip mode)
        if (fontScale != 1.0f) {
            float icx = x + cardW / 2f;
            drawScaledCentered(g, data.rarity().getDisplayName(), icx, y + 8,  chatColor(data.rarity()), fontScale);
            drawScaledCentered(g, mobName(data.mobType(), 8),     icx, y + 20, ArcadiaTheme.TEXT_PRIMARY,  fontScale);
            drawScaledCentered(g, data.totalStars() + "\u2605",   icx, y + cardH - 10, ArcadiaTheme.BRASS, fontScale);
        } else {
            int icx = x + cardW / 2;
            g.drawCenteredString(font, data.rarity().getDisplayName(), icx, y + 8,  chatColor(data.rarity()));
            g.drawCenteredString(font, mobName(data.mobType(), 8),     icx, y + 26, ArcadiaTheme.TEXT_PRIMARY);
            g.drawCenteredString(font, data.totalStars() + "\u2605",   icx, y + cardH - 18, ArcadiaTheme.BRASS);
        }
    }

    /** Draws a centered string at {@code (cx, y)} scaled by {@code scale} around that point. */
    private void drawScaledCentered(GuiGraphics g, String text, float cx, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawCenteredString(font, text, 0, 0, color);
        g.pose().popPose();
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
                    Component.translatable("arcadia_pets.gui.multi_reveal.hint"),
                    width / 2, cardY - 14, 0x88FFFFFF);
        }
    }

    private void drawMiniCard(GuiGraphics g, int x, int y, PetData data, boolean active, int cardIdx) {
        int rc = rarityColor(data.rarity());
        int revealed = starsRevealed[cardIdx];
        int total = data.totalStars();
        boolean fullyRevealed = revealed >= total;
        boolean special = fullyRevealed && total >= 25;

        // Amber glow for 25+ stars once fully revealed
        if (special) {
            int ga = 50 + (int) (30 * Math.sin(ticksPhase1 * 0.2));
            g.fill(x - 4, y - 4, x + MINI_W + 4, y + MINI_H + 4, ArcadiaTheme.withAlpha(ArcadiaTheme.AMBER, ga));
        }

        ArcadiaTheme.drawPanel(g, x, y, MINI_W, MINI_H, active, rc);

        g.drawCenteredString(font, data.rarity().getDisplayName(),
                x + MINI_W / 2, y + 8, chatColor(data.rarity()));
        g.drawCenteredString(font,
                Component.literal(mobName(data.mobType(), 9)).withStyle(s -> s.withBold(true)),
                x + MINI_W / 2, y + 26, ArcadiaTheme.TEXT_PRIMARY);
        // Stars pop in one at a time
        g.drawCenteredString(font, revealed + " \u2605",
                x + MINI_W / 2, y + MINI_H - 22, special ? ArcadiaTheme.BRASS : ArcadiaTheme.TEXT_SECONDARY);
        if (active) {
            g.drawCenteredString(font,
                    Component.translatable("arcadia_pets.gui.multi_reveal.inspect"),
                    x + MINI_W / 2, y + MINI_H - 10, ArcadiaTheme.TEXT_DIM);
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
            g.fill(cardX - 8, cardY - 8, cardX + DETAIL_W + 8, cardY + cardH + 8, ArcadiaTheme.withAlpha(rc, ga));
            g.fill(cardX - 4, cardY - 4, cardX + DETAIL_W + 4, cardY + cardH + 4, ArcadiaTheme.withAlpha(rc, ga + 16));
        }

        ArcadiaTheme.drawPanel(g, cardX, cardY, DETAIL_W, cardH, false, rc);

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
                Component.translatable("arcadia_pets.gui.reveal.total_stars", total),
                cx, ty, ArcadiaTheme.TEXT_SECONDARY);
        ty += 14;

        // Divider
        ArcadiaTheme.drawSeparator(g, cardX, ty, DETAIL_W, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x60));
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
            g.drawString(font, lbl, sx, sy, ArcadiaTheme.TEXT_SECONDARY, false);
            int lw = font.width(lbl);
            g.drawString(font, "\u2606\u2606\u2606\u2606\u2606", sx + lw, sy, 0x444444, false);
            if (stars > 0) g.drawString(font, "\u2605".repeat(stars), sx + lw, sy, 0xFFD700, false);
        }

        // Skills
        List<SkillInstance> skills = data.skills();
        if (!skills.isEmpty()) {
            int sy = ty + 3 * 13 + 5;
            ArcadiaTheme.drawSeparator(g, cardX, sy, DETAIL_W, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x40));
            sy += 5;
            g.drawCenteredString(font,
                    Component.translatable("arcadia_pets.gui.pet.skills_label")
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
