package com.arcadia.pets.client;

import com.arcadia.lib.client.ArcadiaTheme;
import com.arcadia.pets.client.HudSettings;
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
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wheel-of-fortune style pet reveal screen.
 * Displays a scrolling strip of pet cards that decelerates to land on the result,
 * then reveals stats one by one for a premium unboxing experience.
 */
public class PetRevealScreen extends Screen {

    private static final int CARD_WIDTH = 60;
    private static final int CARD_HEIGHT = 70;
    private static final int CARD_GAP = 4;
    private static final int STRIP_HEIGHT = 80;

    private final PetData result;
    private final PetRarity minimumRarity;
    private final List<PetData> candidates;

    /** Which candidate index holds the real result (randomised each open). */
    private final int resultIndex;
    /** Pixel position the strip must stop at so the result card is centred. */
    private final float scrollTarget;
    /** Physics decay rate — randomised to vary feel. */
    private final float decay;
    /** Minimum velocity floor: strip never slows below this, then snaps at target. */
    private static final float MIN_VELOCITY = 2.0f;

    private float scrollVelocity;
    private float scrollPosition;
    private float prevScrollPosition;
    private int phase; // 0 = spinning, 1 = revealing stats, 2 = done
    private int ticksInPhase;
    private int currentStatRevealing;
    /** Last card index that was centred under the arrow — used for click sounds. */
    private int lastCardUnderArrow = -1;

    private int stripYCenter;

    public PetRevealScreen(PetData result, PetRarity minimumRarity) {
        super(Component.translatable("arcadia_prestige.gui.reveal.title"));
        this.result = result;
        this.minimumRarity = minimumRarity;
        this.candidates = new ArrayList<>();

        ThreadLocalRandom rand = ThreadLocalRandom.current();

        // Random strip length: 50–70 cards before the result — long enough that the
        // player genuinely cannot predict which card will land (CSGO-style)
        this.resultIndex = 50 + rand.nextInt(21);

        for (int i = 0; i <= resultIndex; i++) {
            candidates.add(makeFiller(minimumRarity, rand));
        }
        candidates.set(resultIndex, result);

        // 8–12 tail cards so it still feels like it could go further at the end
        int tailCount = 8 + rand.nextInt(5);
        for (int i = 0; i < tailCount; i++) {
            candidates.add(makeFiller(minimumRarity, rand));
        }

        // Decay controls deceleration rate. Range 0.965–0.978 gives roughly 10–18 s spins.
        // Lower bound (0.965) = higher min velocity = faster short spins.
        // Upper bound (0.978) prevents the strip from crawling for 30+ seconds.
        this.decay = 0.965f + rand.nextFloat() * 0.013f;

        // Target pixel offset that centres the result card in the strip.
        this.scrollTarget = resultIndex * (CARD_WIDTH + CARD_GAP) + CARD_WIDTH / 2.0f;

        // v₀ = scrollTarget * (1-r) → geometric series converges to scrollTarget exactly.
        // Initial velocity is intentionally low so the wheel starts at a believable speed.
        this.scrollVelocity = scrollTarget * (1f - decay);

        this.scrollPosition = 0;
        this.prevScrollPosition = 0;
        this.phase = 0;
        this.ticksInPhase = 0;
        this.currentStatRevealing = 0;
    }

    /**
     * Generates a filler card whose rarity distribution matches the bag's loot table
     * (i.e., only rarities >= minimumRarity appear, weighted correctly).
     */
    private static PetData makeFiller(PetRarity minimumRarity, ThreadLocalRandom rand) {
        PetRarity rarity = PetRarity.rollRarity(minimumRarity);
        List<String> pool = PetPoolConfig.getPool(rarity);
        String mob = pool.get(rand.nextInt(pool.size()));
        return new PetData(UUID.randomUUID(), mob, rarity, PetRoller.rollStats(rarity), false, null, 100, 100, Collections.emptyList());
    }

    @Override
    protected void init() {
        super.init();
        stripYCenter = this.height / 2 - 20;
        // Accessibility: skip the spinning strip entirely and jump to card reveal
        HudSettings.ensureLoaded();
        if (HudSettings.reducedMotion) {
            scrollPosition = scrollTarget;
            prevScrollPosition = scrollTarget;
            scrollVelocity = 0;
            phase = 1;
            ticksInPhase = 0;
            currentStatRevealing = 0;
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (phase == 0) {
            prevScrollPosition = scrollPosition;
            scrollPosition += scrollVelocity;
            // Decay normally, then clamp to MIN_VELOCITY floor —
            // the strip creeps at steady speed rather than asymptoting to zero.
            scrollVelocity = Math.max(MIN_VELOCITY, scrollVelocity * decay);
            ticksInPhase++;

            // Click sound each time a new card passes under the arrow
            int cardUnder = (int) ((scrollPosition - CARD_WIDTH / 2.0f) / (CARD_WIDTH + CARD_GAP));
            if (cardUnder != lastCardUnderArrow && cardUnder >= 0) {
                lastCardUnderArrow = cardUnder;
                if (minecraft != null) {
                    float speedRatio = Math.min(1f, (scrollVelocity - MIN_VELOCITY)
                            / (scrollTarget * (1f - decay) - MIN_VELOCITY + 0.001f));
                    float pitch = 1.3f - speedRatio * 0.8f;
                    minecraft.getSoundManager().play(uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, pitch));
                }
            }

            // Snap the moment we reach (or pass) the target — jump is at most MIN_VELOCITY px
            if (scrollPosition >= scrollTarget) {
                prevScrollPosition = scrollTarget;
                scrollPosition = scrollTarget;
                phase = 1;
                ticksInPhase = 0;
                currentStatRevealing = 0;
                if (minecraft != null) {
                    playLandingSound(minecraft, result.rarity());
                }
            }

        } else if (phase == 1) {
            ticksInPhase++;
            int totalItems = 6 + result.skills().size();
            if (ticksInPhase % 12 == 0 && currentStatRevealing < totalItems) {
                currentStatRevealing++;
                if (minecraft != null) {
                    if (currentStatRevealing <= 6) {
                        // Stat reveal sound
                        minecraft.getSoundManager().play(uiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 0.8f + currentStatRevealing * 0.1f));
                    } else {
                        // Skill reveal sound (distinct)
                        minecraft.getSoundManager().play(uiSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.5f, 0.9f + (currentStatRevealing - 6) * 0.15f));
                    }
                }
            }
            if (currentStatRevealing >= totalItems && ticksInPhase > 90) {
                phase = 2;
                ticksInPhase = 0;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        if (phase == 0) {
            renderSpinningStrip(graphics, partialTick);
        }
        if (phase == 1 || phase == 2) {
            renderReveal(graphics, mouseX, mouseY);
        }
        if (phase == 2) {
            // Pulsing "click to continue" text
            int alpha = (int) (180 + 75 * Math.sin(ticksInPhase * 0.15));
            int color = (alpha << 24) | 0xFFFFFF;
            graphics.drawCenteredString(this.font, Component.translatable("arcadia_prestige.gui.reveal.continue"),
                    this.width / 2, this.height - 30, color);
        }
    }

    private void renderSpinningStrip(GuiGraphics graphics, float partialTick) {
        float interpolatedScroll = prevScrollPosition + (scrollPosition - prevScrollPosition) * partialTick;
        int centerX = this.width / 2;
        int stripY = stripYCenter;

        // Themed background bar with subtle gradient
        graphics.fill(0, stripY - 8, this.width, stripY + CARD_HEIGHT + 8, 0xDD0E0B14);
        graphics.fill(0, stripY - 8, this.width, stripY - 6, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x55));
        graphics.fill(0, stripY + CARD_HEIGHT + 6, this.width, stripY + CARD_HEIGHT + 8, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x55));

        // Draw cards
        for (int i = 0; i < candidates.size(); i++) {
            int cardCenterX = (int) (i * (CARD_WIDTH + CARD_GAP) + CARD_WIDTH / 2.0f - interpolatedScroll);
            int screenX = centerX + cardCenterX;
            if (screenX < -CARD_WIDTH || screenX > this.width + CARD_WIDTH) continue;
            drawCard(graphics, screenX - CARD_WIDTH / 2, stripY, candidates.get(i), i == resultIndex && phase > 0);
        }

        // Selection indicator: copper/brass frame
        int sL = centerX - CARD_WIDTH / 2 - 3;
        int sT = stripY - 8;
        int sR = centerX + CARD_WIDTH / 2 + 3;
        int sB = stripY + CARD_HEIGHT + 8;
        ArcadiaTheme.drawGlow(graphics, sL, sT, sR - sL, sB - sT, ArcadiaTheme.BRASS);
        ArcadiaTheme.drawBorder(graphics, sL, sT, sR - sL, sB - sT, ArcadiaTheme.BRASS);
        ArcadiaTheme.drawBorder(graphics, sL + 1, sT + 1, sR - sL - 2, sB - sT - 2, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0xAA));

        // Arrow indicator
        int arrowX = centerX;
        int arrowY = stripY - 14;
        graphics.fill(arrowX - 4, arrowY, arrowX + 4, arrowY + 5, ArcadiaTheme.BRASS);
        graphics.fill(arrowX - 2, arrowY + 5, arrowX + 2, arrowY + 8, ArcadiaTheme.BRASS);

        // Title
        Component title = Component.translatable("arcadia_prestige.gui.reveal.rolling").withStyle(s -> s.withBold(true));
        ArcadiaTheme.drawCenteredText(graphics, title, centerX, stripY - 28, ArcadiaTheme.BRASS);
    }

    private void renderReveal(GuiGraphics graphics, int mouseX, int mouseY) {
        int centerX = this.width / 2;
        int cardW = 180;
        int skillCount = result.skills().size();
        // Base height reduced: stats now 2-col 3-row (48px) instead of 6-row (108px)
        int cardH = 155 + (skillCount > 0 ? 14 + skillCount * 14 : 0);
        int cardLeft = centerX - cardW / 2;
        int cardTop = this.height / 2 - cardH / 2;

        int rarityColor = getRarityColor(result.rarity());

        // Glow effect for legendary / mythic
        if (result.rarity() == PetRarity.LEGENDARY || result.rarity() == PetRarity.MYTHIC) {
            int glowAlpha = (int) (40 + 20 * Math.sin(ticksInPhase * 0.1));
            int glowColor = (glowAlpha << 24) | (rarityColor & 0x00FFFFFF);
            graphics.fill(cardLeft - 8, cardTop - 8, cardLeft + cardW + 8, cardTop + cardH + 8, glowColor);
            graphics.fill(cardLeft - 4, cardTop - 4, cardLeft + cardW + 4, cardTop + cardH + 4, glowColor);
        }

        // Themed card panel with rarity accent
        ArcadiaTheme.drawPanel(graphics, cardLeft, cardTop, cardW, cardH, false, rarityColor);

        int textY = cardTop + 12;

        // Rarity name in rarity color, bold
        int chatColor = result.rarity().getColor().getColor() != null ? result.rarity().getColor().getColor() : 0xFFFFFF;
        graphics.drawCenteredString(this.font,
                Component.literal(result.rarity().getDisplayName()).withStyle(s -> s.withBold(true)),
                centerX, textY, chatColor);
        textY += 16;

        // Mob type name
        String mobName = result.mobType();
        if (mobName.contains(":")) {
            mobName = mobName.substring(mobName.indexOf(':') + 1);
        }
        mobName = mobName.substring(0, 1).toUpperCase() + mobName.substring(1).replace('_', ' ');
        graphics.drawCenteredString(this.font,
                Component.literal(mobName).withStyle(s -> s.withBold(true)),
                centerX, textY, 0xFFFFFF);
        textY += 20;

        // Total stars — 2 rows of 15 (gray background, yellow fill)
        int totalStars = result.totalStars();
        String emptyRow  = "\u2606\u2606\u2606\u2606\u2606\u2606\u2606\u2606\u2606\u2606\u2606\u2606\u2606\u2606\u2606"; // 15 ☆
        int rowW = this.font.width(emptyRow);
        int rowStartX = centerX - rowW / 2;
        // Row 1: stars 1-15
        graphics.drawString(this.font, emptyRow, rowStartX, textY, 0x444444, false);
        int filled1 = Math.min(totalStars, 15);
        if (filled1 > 0) {
            String f1 = "\u2605".repeat(filled1);
            graphics.drawString(this.font, f1, rowStartX, textY, 0xFFD700, false);
        }
        textY += 10;
        // Row 2: stars 16-30
        graphics.drawString(this.font, emptyRow, rowStartX, textY, 0x444444, false);
        int filled2 = Math.max(0, totalStars - 15);
        if (filled2 > 0) {
            String f2 = "\u2605".repeat(filled2);
            graphics.drawString(this.font, f2, rowStartX, textY, 0xFFD700, false);
        }
        textY += 10;
        graphics.drawCenteredString(this.font, Component.translatable("arcadia_prestige.gui.reveal.total_stars", totalStars), centerX, textY, ArcadiaTheme.TEXT_SECONDARY);
        textY += 16;

        // Divider
        ArcadiaTheme.drawSeparator(graphics, cardLeft, textY, cardW, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x55));
        textY += 8;

        // Stats revealed one by one — 2 columns × 3 rows
        // Format: "POW ★★★☆☆" — trigram + 5-star bar, no icon prefix
        PetStat[] allStats = PetStat.values();
        int statRowH = 14;
        int col0X = cardLeft + 10;
        int col1X = cardLeft + cardW / 2 + 2;
        for (int i = 0; i < allStats.length; i++) {
            PetStat stat = allStats[i];
            int col = i / 3;
            int row = i % 3;
            int statX = col == 0 ? col0X : col1X;
            int statY = textY + row * statRowH;

            if (i < currentStatRevealing) {
                int stars = result.stats().getOrDefault(stat, 0);
                // Trigram label
                String label = stat.getIcon() + " "; // e.g. "POW "
                graphics.drawString(this.font, label, statX, statY, 0xAAAAAA, false);
                int labelW = this.font.width(label);
                // Gray empty bar first, then gold filled on top
                graphics.drawString(this.font, "\u2606\u2606\u2606\u2606\u2606", statX + labelW, statY, 0x444444, false);
                if (stars > 0) {
                    graphics.drawString(this.font, "\u2605".repeat(stars), statX + labelW, statY, 0xFFD700, false);
                }
            } else {
                // Not yet revealed
                String label = stat.getIcon() + " ";
                graphics.drawString(this.font, label, statX, statY, 0x444444, false);
                graphics.drawString(this.font, "?????", statX + this.font.width(label), statY, 0x333333, false);
            }
        }

        // Skills section (revealed after all 6 stats)
        List<SkillInstance> skills = result.skills();
        if (!skills.isEmpty()) {
            int skillsY = textY + 3 * 14 + 6;
            // Divider
            ArcadiaTheme.drawSeparator(graphics, cardLeft, skillsY, cardW, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x44));
            skillsY += 5;
            // "Skills" label
            graphics.drawCenteredString(this.font,
                    Component.translatable("arcadia_prestige.gui.pet.skills_label").withStyle(ChatFormatting.GOLD),
                    centerX, skillsY, 0xFFAA00);
            skillsY += 9;

            for (int i = 0; i < skills.size(); i++) {
                SkillInstance inst = skills.get(i);
                int skillY = skillsY + i * 14;
                int revealIndex = 6 + i; // this skill's position in the reveal sequence

                if (currentStatRevealing > revealIndex) {
                    // Revealed
                    if (inst.level() == 0) {
                        // Locked skill
                        graphics.drawString(this.font, "\uD83D\uDD12 ???  [Locked]", cardLeft + 14, skillY, 0x555566, false);
                    } else {
                        String name = inst.skill().getDisplayName().getString();
                        String lvBadge = " [Lv " + inst.level() + "]";
                        graphics.drawString(this.font, "\u2605 " + name, cardLeft + 14, skillY, 0xDDDDDD, false);
                        int nameW = this.font.width("\u2605 " + name);
                        int lvColor = inst.level() >= 10 ? 0xFFD700 : inst.level() >= 7 ? 0xAA55FF : 0x5588FF;
                        graphics.drawString(this.font, lvBadge, cardLeft + 14 + nameW, skillY, lvColor, false);
                    }
                } else {
                    // Not yet revealed
                    graphics.drawString(this.font, "\u2605 ???",
                            cardLeft + 14, skillY, 0x444444, false);
                }
            }
        }
    }

    private void drawCard(GuiGraphics graphics, int x, int y, PetData data, boolean selected) {
        int rarityCol = getRarityColor(data.rarity());

        // Card panel with rarity accent
        ArcadiaTheme.drawPanel(graphics, x, y, CARD_WIDTH, CARD_HEIGHT, selected, rarityCol);

        if (selected) {
            ArcadiaTheme.drawGlow(graphics, x, y, CARD_WIDTH, CARD_HEIGHT, ArcadiaTheme.BRASS);
        }

        // Mob name
        String mobName = data.mobType();
        if (mobName.contains(":")) mobName = mobName.substring(mobName.indexOf(':') + 1);
        if (mobName.length() > 8) mobName = mobName.substring(0, 7) + ".";
        mobName = mobName.substring(0, 1).toUpperCase() + mobName.substring(1).replace('_', ' ');

        int chatColor = data.rarity().getColor().getColor() != null ? data.rarity().getColor().getColor() : 0xFFFFFF;
        int cx = x + CARD_WIDTH / 2;

        graphics.drawCenteredString(this.font, data.rarity().getDisplayName(), cx, y + 10, chatColor);
        graphics.drawCenteredString(this.font, mobName, cx, y + 28, ArcadiaTheme.TEXT_PRIMARY);

        // Star count
        graphics.drawCenteredString(this.font, data.totalStars() + "\u2605", cx, y + CARD_HEIGHT - 18, ArcadiaTheme.BRASS);
    }

    private static int getRarityColor(PetRarity rarity) {
        return switch (rarity) {
            case COMMON -> 0xFF808080;
            case UNCOMMON -> 0xFF2ecc71;
            case RARE -> 0xFF3498db;
            case EPIC -> 0xFF9b59b6;
            case LEGENDARY -> 0xFFf39c12;
            case MYTHIC -> 0xFFe74c3c;
        };
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape during spin or reveal: skip to result instead of closing abruptly.
        // Phase 0 → jump to reveal. Phase 1 → complete reveal instantly.
        // Phase 2 (done) → close normally.
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            if (phase == 0) {
                prevScrollPosition = scrollTarget;
                scrollPosition    = scrollTarget;
                scrollVelocity    = 0;
                phase             = 1;
                ticksInPhase      = 0;
                currentStatRevealing = 0;
                return true; // consumed — don't close
            } else if (phase == 1) {
                currentStatRevealing = 6 + result.skills().size();
                ticksInPhase = 91; // push past the 90-tick wait so it moves to phase 2
                return true;
            }
            // phase == 2 → fall through to super → closes the screen
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (phase == 2) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Sound helpers ─────────────────────────────────────────────────────────

    /** Plays a rarity-appropriate landing sound via MASTER source (respects GUI mute). */
    static void playLandingSound(Minecraft mc, PetRarity rarity) {
        switch (rarity) {
            case LEGENDARY -> {
                mc.getSoundManager().play(uiSound(SoundEvents.TOTEM_USE, 0.6f, 0.9f));
            }
            case MYTHIC -> {
                mc.getSoundManager().play(uiSound(SoundEvents.TOTEM_USE, 0.8f, 0.7f));
                mc.getSoundManager().play(uiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 1.8f));
            }
            case EPIC -> {
                mc.getSoundManager().play(uiSound(SoundEvents.ANVIL_LAND, 0.5f, 1.4f));
                mc.getSoundManager().play(uiSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.3f, 1.5f));
            }
            default -> mc.getSoundManager().play(uiSound(SoundEvents.ANVIL_LAND, 0.5f, 1.2f));
        }
    }

    static SimpleSoundInstance uiSound(net.minecraft.sounds.SoundEvent se, float volume, float pitch) {
        return new SimpleSoundInstance(se.getLocation(), SoundSource.MASTER, volume, pitch,
                net.minecraft.util.RandomSource.create(), false, 0,
                net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE, 0, 0, 0, true);
    }
}
