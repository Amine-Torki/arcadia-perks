package com.arcadia.pets.client;

import com.arcadia.lib.client.ArcadiaTheme;
import com.arcadia.pets.item.DerivedPetStats;
import com.arcadia.pets.item.PetBehaviourMode;
import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetMovementMode;
import com.arcadia.pets.item.PetRarity;
import com.arcadia.pets.item.PetStat;
import com.arcadia.pets.network.C2SPetAction;
import com.arcadia.pets.network.C2SRenamePet;
import com.arcadia.pets.skill.PetSkill;
import com.arcadia.pets.skill.SkillInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Tamagotchi-style pet panel screen. Opened server-side via right-clicking a PetItem.
 *
 * Layout (top → bottom):
 *   Name + rarity label
 *   Mob type
 *   ── divider ──
 *   Stats (left) | Hunger & Happiness bars (right)
 *   ── divider ──
 *   [Summon/Recall]  [🦴 Feed]
 *   Movement row: [Follow] [Wander] [Sit]
 *   Behaviour row: [Idle] [Defend] [Attack]
 */
public class PetScreen extends Screen {

    private static final int CARD_W = 297;
    private static final int CARD_H = 252;

    private static final int BTN_H  = 13;

    // Sub-panel layout
    private static final int LEFT_PANEL_W    = 165;  // = 3 × GENE_CELL_W
    private static final int PANEL_GAP       = 8;    // gap between left and right sub-panels
    // Right panel X = cardLeft + 8 + LEFT_PANEL_W + PANEL_GAP = cardLeft + 181

    // Gene / combat column grid (shared by both sections for alignment)
    private static final int GENE_CELL_W     = 55;   // 3 cols × 55 = 165 = LEFT_PANEL_W

    // Bar dimensions (right sub-panel)
    private static final int BAR_W           = 80;
    private static final int BAR_H           = 4;
    private static final int BAR_LABEL_GAP   = 8;
    private static final int BAR_SECTION_GAP = 11;

    // Portrait box (right sub-panel) — smaller than before, same entity scale
    private static final int PORTRAIT_W      = 82;   // slightly wider than BAR_W for visual breathing room
    private static final int PORTRAIT_H      = 46;   // was 72 — cuts dead black space
    private static final int HP_STRIP_H      = 10;

    private PetData petData;
    private int cooldownTicks;
    private boolean petActive;
    private float currentHp;
    private float maxHp;

    private PetMovementMode  selectedMovement;
    private PetBehaviourMode selectedBehaviour;

    // Rename field (computed in init)
    private EditBox nameInput;
    private int renameBtnX, renameBtnY;
    private static final int RENAME_BTN_W = 45;

    // Bottom row: 2 cycling buttons (status/summon, behaviour)
    // Layout: [cardLeft+8] [btn0] gap [btn1] [cardLeft+CARD_W-8]
    private static final int CYCLE_BTN_W   = 125;
    private static final int CYCLE_BTN_GAP = 4;
    private int statusBtnX, statusBtnY;   // cycles: Recall → Follow → Pocket → Sit
    private int behavBtnX,  behavBtnY;   // cycles: Idle → Defend → Attack

    // Gear button (opens HUD settings)
    private int gearBtnX, gearBtnY;
    private static final int GEAR_BTN_SIZE = 10;

    // For blink on cooldown
    private int blinkTick = 0;

    // Skill section state
    private java.util.Map<String, Long> skillCooldownEnds = new java.util.HashMap<>();
    private java.util.Map<String, Boolean> skillToggles = new java.util.HashMap<>();
    private int selectedSkillIdx = -1;
    /** Y position of the first skill row, stored during render for use in mouseClicked. */
    private int computedSkillsTopY = -1;

    public PetScreen(PetData petData, int cooldownTicks, boolean petActive,
                     PetMovementMode movement, PetBehaviourMode behaviour,
                     float currentHp, float maxHp,
                     java.util.Map<String, Long> cdEnds,
                     java.util.Map<String, Boolean> toggles) {
        super(Component.translatable("arcadia_pets.gui.pet_panel.title"));
        this.petData = petData;
        this.cooldownTicks = cooldownTicks;
        this.petActive = petActive;
        this.selectedMovement  = movement;
        this.selectedBehaviour = behaviour;
        this.currentHp = currentHp;
        this.maxHp     = maxHp;
        if (cdEnds != null) this.skillCooldownEnds = cdEnds;
        if (toggles != null) this.skillToggles = toggles;
    }

    public UUID getPetId() { return petData.petId(); }

    public void updateData(PetData data, int cooldownTicks, boolean petActive,
                           PetMovementMode movement, PetBehaviourMode behaviour,
                           float currentHp, float maxHp,
                           java.util.Map<String, Long> cdEnds,
                           java.util.Map<String, Boolean> toggles) {
        this.petData = data;
        this.cooldownTicks = cooldownTicks;
        this.petActive = petActive;
        this.selectedMovement  = movement;
        this.selectedBehaviour = behaviour;
        this.currentHp = currentHp;
        this.maxHp     = maxHp;
        if (cdEnds != null) this.skillCooldownEnds = cdEnds;
        if (toggles != null) this.skillToggles = toggles;
        if (nameInput != null) {
            nameInput.setValue(data.customName() != null ? data.customName() : "");
        }
    }

    @Override
    protected void init() {
        super.init();
        int cardLeft = (this.width  - CARD_W) / 2;
        int cardTop  = (this.height - CARD_H) / 2;

        // Rename field: below mob type line
        int nameFieldX = cardLeft + 8;
        int nameFieldY = cardTop + 32;
        int nameFieldW = CARD_W - 16 - RENAME_BTN_W - 4;
        nameInput = new EditBox(this.font, nameFieldX, nameFieldY, nameFieldW, 12,
                Component.translatable("arcadia_pets.gui.pet_panel.name_label"));
        nameInput.setMaxLength(20);
        nameInput.setValue(petData.customName() != null ? petData.customName() : "");
        nameInput.setHint(Component.translatable("arcadia_pets.gui.pet_panel.name_hint").withStyle(ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(nameInput);
        renameBtnX = nameFieldX + nameFieldW + 4;
        renameBtnY = nameFieldY;

        // Single bottom row: 2 cycling buttons
        int actionY  = cardTop + CARD_H - 3 - 3 - BTN_H;
        statusBtnX   = cardLeft + 8;
        statusBtnY   = actionY;
        behavBtnX    = statusBtnX + CYCLE_BTN_W + CYCLE_BTN_GAP;
        behavBtnY    = actionY;

        // Gear button: top-right corner of card (inside border)
        gearBtnX = cardLeft + CARD_W - 3 - GEAR_BTN_SIZE - 2;
        gearBtnY = cardTop + 3 + 2;
    }

    @Override
    public void tick() {
        super.tick();
        if (cooldownTicks > 0) cooldownTicks--;
        blinkTick++;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        int cardLeft = (this.width  - CARD_W) / 2;
        int cardTop  = (this.height - CARD_H) / 2;

        int rarityColor = getRarityColor(petData.rarity());
        int chatColor   = petData.rarity().getColor().getColor() != null
                ? petData.rarity().getColor().getColor() : 0xFFFFFF;

        // ── Background & border (steampunk themed) ─────────────────────────
        ArcadiaTheme.drawPanel(g, cardLeft, cardTop, CARD_W, CARD_H, false, rarityColor);

        // ── Gear button ───────────────────────────────────────────────────────
        boolean hovGear = mouseX >= gearBtnX && mouseX < gearBtnX + GEAR_BTN_SIZE
                && mouseY >= gearBtnY && mouseY < gearBtnY + GEAR_BTN_SIZE;
        g.fill(gearBtnX, gearBtnY, gearBtnX + GEAR_BTN_SIZE, gearBtnY + GEAR_BTN_SIZE,
                hovGear ? 0xFF6A5A3A : 0xFF4A3A28);
        g.drawString(this.font, "\u2699", gearBtnX + 1, gearBtnY + 1, ArcadiaTheme.BRASS, false);

        int y      = cardTop + 8;
        int centerX = cardLeft + CARD_W / 2;

        // ── Name + rarity label ───────────────────────────────────────────────
        String petName;
        if (petData.customName() != null && !petData.customName().isEmpty()) {
            petName = petData.customName();
        } else {
            String mob = petData.mobType();
            if (mob.contains(":")) mob = mob.substring(mob.indexOf(':') + 1);
            petName = mob.substring(0, 1).toUpperCase() + mob.substring(1).replace('_', ' ');
        }
        // Name with shadow
        g.drawString(this.font, Component.literal(petName).withStyle(s -> s.withBold(true)),
                cardLeft + 9, y + 1, 0x22000000, false);
        g.drawString(this.font, Component.literal(petName).withStyle(s -> s.withBold(true)),
                cardLeft + 8, y, chatColor, false);
        y += 12;

        // ── Mob type — Rarity ─────────────────────────────────────────────────
        String mobType = petData.mobType();
        if (mobType.contains(":")) mobType = mobType.substring(mobType.indexOf(':') + 1);
        mobType = mobType.substring(0, 1).toUpperCase() + mobType.substring(1).replace('_', ' ');
        String typeRarityLabel = mobType + " \u2014 " + petData.rarity().getTranslatableName().getString();
        g.drawString(this.font, typeRarityLabel, cardLeft + 8, y, ArcadiaTheme.TEXT_SECONDARY, false);
        y += 12;

        // ── Rename row: EditBox rendered by widget system; just draw "Set" button ──
        boolean hovRename = mouseX >= renameBtnX && mouseX < renameBtnX + RENAME_BTN_W
                && mouseY >= renameBtnY && mouseY < renameBtnY + 12;
        g.fill(renameBtnX, renameBtnY, renameBtnX + RENAME_BTN_W, renameBtnY + 12,
                hovRename ? 0xFF5A4A2A : 0xFF3A3020);
        Component setComp = Component.translatable("arcadia_pets.gui.pets.set_name");
        g.drawString(this.font, setComp,
                renameBtnX + (RENAME_BTN_W - this.font.width(setComp)) / 2, renameBtnY + 2,
                ArcadiaTheme.TEXT_PRIMARY, false);
        y += 18; // 12px EditBox + 6px gap

        // ── Divider (copper themed) ──────────────────────────────────────────
        ArcadiaTheme.drawSeparator(g, cardLeft, y, CARD_W, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x55));
        y += 6;

        // ── Sub-panel anchors ─────────────────────────────────────────────────
        int statsX  = cardLeft + 8;
        int statsY  = y;           // left panel starts here
        float sf    = 0.80f;
        int cellH   = 13;
        // Right panel starts at the SAME y — true side-by-side, no overlap possible
        int rightX  = cardLeft + 8 + LEFT_PANEL_W + PANEL_GAP;
        int rightY  = y;

        // ── Gene bars: compact 3×2 segmented bar visualizer ─────────────────
        // Each stat: tiny abbreviation label + 5 filled/empty rectangle segments
        PetStat[] geneOrder = PetStat.values(); // POW, END, AGI, WIT, CHM, LCK
        int segW    = 5;   // segment width px
        int segH    = 5;   // segment height px
        int segGap  = 1;   // gap between segments
        int geneRowH = 8;  // row height (leaves 1px below the bar)
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                if (idx >= geneOrder.length) continue;
                PetStat stat  = geneOrder[idx];
                int     stars = petData.stats().getOrDefault(stat, 0);
                int     cx    = statsX + col * GENE_CELL_W;
                int     cy    = statsY + row * (geneRowH + 2);
                String  abbr  = stat.getIcon();
                // Abbreviated label at 0.75× scale
                g.pose().pushPose();
                g.pose().translate(cx, cy, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(this.font, abbr, 0, 0, 0x888888, false);
                g.pose().popPose();
                int abbrPx = (int)(this.font.width(abbr) * 0.75f) + 3;
                // 5 segments
                int fillColor = starBarColor(stars);
                for (int s = 0; s < 5; s++) {
                    int sx = cx + abbrPx + s * (segW + segGap);
                    g.fill(sx, cy, sx + segW, cy + segH, s < stars ? fillColor : 0xFF252525);
                }
            }
        }
        statsY += 2 * (geneRowH + 2) - 2 + 5; // 2 rows + 1 inter-row gap + bottom padding

        // ── Combat stats with tier badges ─────────────────────────────────────
        // Columns use GENE_CELL_W so they align exactly with the gene grid above
        DerivedPetStats derived = new DerivedPetStats(petData);
        g.pose().pushPose();
        g.pose().translate(statsX, statsY, 0);
        g.pose().scale(sf, sf, 1f);
        g.drawString(this.font, Component.translatable("arcadia_pets.hud.combat_label"), 0, 0, ArcadiaTheme.TEXT_DIM, false);
        g.pose().popPose();
        statsY += 9;

        // Row 1: ATK  DEF  HP   |   Row 2: CRIT  EVA
        String[][] statNames = { {"ATK", "DEF", "HP"}, {"CRIT", "EVA"} };
        int[][]    statVals  = { {derived.atk, derived.defPct, derived.hp},
                                  {derived.critPct, derived.evaPct} };
        int[]      statMaxes = { 8, 48, 36, 35, 35 };
        int statIdx = 0;
        for (int row = 0; row < statNames.length; row++) {
            for (int col = 0; col < statNames[row].length; col++) {
                String label  = statNames[row][col];
                int    val    = statVals[row][col];
                int    max    = statMaxes[statIdx++];
                String tier   = statTier(val, max);
                int    tColor = tierColor(tier);
                int    dx     = statsX + col * GENE_CELL_W;  // aligned with gene columns

                // Stat label
                g.pose().pushPose();
                g.pose().translate(dx, statsY, 0);
                g.pose().scale(sf, sf, 1f);
                g.drawString(this.font, label, 0, 0, 0xBBBBBB, false);
                g.pose().popPose();
                int labelEndX = dx + (int)(this.font.width(label) * sf) + 2;

                // Tier badge
                int badgeW = tier.length() > 1 ? 15 : 11;
                int badgeH = 8;
                g.fill(labelEndX, statsY, labelEndX + badgeW, statsY + badgeH, tColor);
                g.pose().pushPose();
                g.pose().translate(labelEndX, statsY, 0);
                g.pose().scale(0.625f, 0.625f, 1f);
                int tw  = this.font.width(tier);
                int bws = Math.round(badgeW / 0.625f);
                g.drawString(this.font, tier, (bws - tw) / 2, 1, 0xFFFFFF, false);
                g.pose().popPose();
            }
            statsY += 12;
        }

        // ── Right sub-panel: Pet portrait (with HP bar) + Hunger + Happiness ──
        // rightY was set to the same y as statsY (start of genes)

        // ── Entity portrait box (themed) ─────────────────────────────────────
        g.fill(rightX, rightY, rightX + PORTRAIT_W, rightY + PORTRAIT_H, 0xFF0E0B14);
        ArcadiaTheme.drawBorder(g, rightX, rightY, PORTRAIT_W, PORTRAIT_H,
                ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x66));

        // Entity render (uses ClientPetCache to avoid creating entities every frame)
        LivingEntity previewEntity = ClientPetCache.getEntity(petData.mobType());
        if (previewEntity != null) {
            float entityMaxDim = Math.max(previewEntity.getBbWidth(), previewEntity.getBbHeight());
            int entityScale = Math.max(10, Math.min(40, (int)(28f / entityMaxDim)));
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, rightX + 1, rightY + 1, rightX + PORTRAIT_W - 1, rightY + PORTRAIT_H - 1,
                    entityScale, 0f, (float) mouseX, (float) mouseY, previewEntity);
        }

        rightY += PORTRAIT_H + 6;

        // Hunger — read live from ClientPetState when pet is active
        int hungerVal      = ClientPetState.isActive() ? ClientPetState.getHunger() : petData.hunger();
        int hungerBarColor = hungerVal >= 70 ? 0xFF55FF55 : hungerVal >= 30 ? 0xFFFFFF55 : 0xFFFF5555;
        g.drawString(this.font, Component.translatable("arcadia_pets.gui.pets.hunger"), rightX, rightY, ArcadiaTheme.TEXT_PRIMARY, false);
        rightY += BAR_LABEL_GAP;
        g.fill(rightX, rightY, rightX + BAR_W, rightY + BAR_H, 0x44000000);
        g.fill(rightX, rightY, rightX + (int)(BAR_W * hungerVal / 100.0f), rightY + BAR_H, hungerBarColor);
        ArcadiaTheme.drawBorder(g, rightX - 1, rightY - 1, BAR_W + 2, BAR_H + 2, 0x33FFFFFF);
        g.drawString(this.font, String.valueOf(hungerVal), rightX + BAR_W + 4, rightY - 1, ArcadiaTheme.TEXT_SECONDARY, false);
        rightY += BAR_H + BAR_SECTION_GAP;

        // HP bar — read live from ClientPetState when pet is active
        float liveHp  = ClientPetState.isActive() ? ClientPetState.getDisplayHp() : currentHp;
        float liveMax = ClientPetState.isActive() ? ClientPetState.getMaxHp()     : maxHp;
        float hpPct       = liveMax > 0 ? Math.max(0f, Math.min(1f, liveHp / liveMax)) : 0f;
        int   hpFillColor = hpPct > 0.60f ? 0xFF44AA44 : hpPct > 0.30f ? 0xFFCC8822 : 0xFFCC3333;
        g.drawString(this.font, Component.translatable("arcadia_pets.gui.pets.hp_label"), rightX, rightY, ArcadiaTheme.TEXT_PRIMARY, false);
        rightY += BAR_LABEL_GAP;
        g.fill(rightX, rightY, rightX + BAR_W, rightY + BAR_H, 0x44000000);
        g.fill(rightX, rightY, rightX + (int)(BAR_W * hpPct), rightY + BAR_H, hpFillColor);
        ArcadiaTheme.drawBorder(g, rightX - 1, rightY - 1, BAR_W + 2, BAR_H + 2, 0x33FFFFFF);
        String hpText = (int) liveHp + "/" + (int) liveMax;
        g.drawString(this.font, hpText, rightX + BAR_W + 4, rightY - 1, ArcadiaTheme.TEXT_SECONDARY, false);
        rightY += BAR_H;

        // ── Skills section — left column, directly below combat stats ─────────
        int skillsTopY = statsY + 4;
        java.util.List<SkillInstance> skills = petData.skills();
        if (!skills.isEmpty()) {
            int skillsX = cardLeft + 8;
            int rowW    = LEFT_PANEL_W;
            int rowH    = BTN_H + 1;
            int rowGap  = 2;

            // Section label
            g.pose().pushPose();
            g.pose().translate(skillsX, skillsTopY, 0);
            g.pose().scale(0.80f, 0.80f, 1f);
            g.drawString(this.font, "Skills", 0, 0, 0x777777, false);
            g.pose().popPose();
            skillsTopY += 9;
            this.computedSkillsTopY = skillsTopY; // store for mouseClicked

            int toggleW = 10; // toggle button width at the left of each row

            for (int i = 0; i < skills.size(); i++) {
                SkillInstance inst  = skills.get(i);
                PetSkill      skill = inst.skill();
                int           lv    = inst.level();
                int           rowY  = skillsTopY + i * (rowH + rowGap);
                boolean       locked = lv == 0;
                boolean       sel   = selectedSkillIdx == i;
                boolean       hov   = mouseX >= skillsX && mouseX < skillsX + rowW
                                   && mouseY >= rowY && mouseY < rowY + rowH;

                if (locked) {
                    g.fill(skillsX, rowY, skillsX + rowW, rowY + rowH, 0xFF0C0C14);
                    g.fill(skillsX, rowY, skillsX + rowW, rowY + 1, 0x22FFFFFF);
                    g.drawString(this.font, "\uD83D\uDD12", skillsX + 2, rowY + 3, 0xFF555566, false);
                    g.drawString(this.font, "???", skillsX + 12, rowY + 3, 0xFF444455, false);
                    String lockStr  = "Locked";
                    int lockPad     = 2;
                    int lockBadgeW  = this.font.width(lockStr) + lockPad * 2;
                    int lockBadgeX  = skillsX + rowW - lockBadgeW - 2;
                    int lockBadgeY  = rowY + 2;
                    g.fill(lockBadgeX, lockBadgeY, lockBadgeX + lockBadgeW, lockBadgeY + rowH - 4, 0xFF4A1010);
                    g.drawString(this.font, lockStr, lockBadgeX + lockPad, lockBadgeY + 1, 0xAA5555, false);
                    continue;
                }

                // Toggle button (ON/OFF) at the left of the row
                boolean enabled = skillToggles.getOrDefault(skill.getId(), true);
                boolean hovToggle = mouseX >= skillsX && mouseX < skillsX + toggleW
                                 && mouseY >= rowY && mouseY < rowY + rowH;
                g.fill(skillsX, rowY, skillsX + toggleW, rowY + rowH,
                        hovToggle ? 0xFF334455 : (enabled ? 0xFF1A3A1A : 0xFF3A1A1A));
                String toggleIcon = enabled ? "\u25CF" : "\u25CB"; // filled / hollow circle
                int toggleColor   = enabled ? 0xFF55CC55 : 0xFFCC5555;
                g.drawString(this.font, toggleIcon, skillsX + 1, rowY + 3, toggleColor, false);

                // Skill content starts after toggle
                int contentX = skillsX + toggleW + 1;
                int contentW = rowW - toggleW - 1;

                long endTime   = skillCooldownEnds.getOrDefault(skill.getId(), 0L);
                long totalCdMs = skill.getCooldownMs(lv);
                long remaining = Math.max(0L, endTime - System.currentTimeMillis());
                boolean onCd   = remaining > 0;
                float cdFrac   = (totalCdMs > 0 && onCd) ? (float) remaining / totalCdMs : 0f;

                int bg = sel ? 0xFF1E3A1E : hov ? 0xFF182818 : 0xFF101A10;
                g.fill(contentX, rowY, skillsX + rowW, rowY + rowH, bg);

                if (!enabled) {
                    // Dimmed overlay when disabled
                    g.fill(contentX, rowY, skillsX + rowW, rowY + rowH, 0x66000000);
                } else if (onCd) {
                    int cdOverlayW = (int)(contentW * cdFrac);
                    g.fill(skillsX + rowW - cdOverlayW, rowY, skillsX + rowW, rowY + rowH, 0x44FF3322);
                } else {
                    g.fill(contentX, rowY, skillsX + rowW, rowY + rowH, 0x1100CC00);
                }

                g.drawString(this.font, "\u2605", contentX + 2, rowY + 3, enabled ? 0xFFD700 : 0x665500, false);
                int starW = this.font.width("\u2605") + 3;

                String cdStr   = onCd ? formatCd(remaining) : "\u2713";
                int    cdColor = onCd ? 0xFFFF6655 : 0xFF55BB55;
                if (!enabled) cdColor = 0xFF666666;
                int    cdStrW  = this.font.width(cdStr);

                String lvStr    = "Lv" + lv;
                int    lvPad    = 2;
                int    lvBadgeW = this.font.width(lvStr) + lvPad * 2;
                int    lvBadgeH = rowH - 4;
                int    lvBadgeX = skillsX + rowW - cdStrW - lvBadgeW - 4;
                int    lvBadgeY = rowY + 2;
                int    lvBgColor = lv >= 10 ? 0xFFB8860B : lv >= 7 ? 0xFF6A0DAD : lv >= 4 ? 0xFF1A5276 : 0xFF1A3055;
                if (!enabled) lvBgColor = 0xFF222222;
                g.fill(lvBadgeX, lvBadgeY, lvBadgeX + lvBadgeW, lvBadgeY + lvBadgeH, lvBgColor);
                g.drawString(this.font, lvStr, lvBadgeX + lvPad, lvBadgeY + 1, enabled ? 0xFFFFFF : 0xFF888888, false);

                String skillName = skill.getDisplayName().getString();
                int maxNameW = contentW - starW - lvBadgeW - cdStrW - 14;
                // Compare at 0.8 scale: text renders 80% narrower, so effective max is maxNameW/0.8
                while (skillName.length() > 2 && this.font.width(skillName) * 0.8f > maxNameW) {
                    skillName = skillName.substring(0, skillName.length() - 1);
                }
                g.pose().pushPose();
                g.pose().translate(contentX + 2 + starW, rowY + 2, 0);
                g.pose().scale(0.8f, 0.8f, 1f);
                g.drawString(this.font, skillName, 0, 0, enabled ? 0xDDDDDD : 0xFF888888, false);
                g.pose().popPose();
                g.drawString(this.font, cdStr, skillsX + rowW - cdStrW - 1, rowY + 3, cdColor, false);
            }

            // Expanded description panel (below all skill rows) — dynamic height, 2-line wrap
            if (selectedSkillIdx >= 0 && selectedSkillIdx < skills.size()) {
                SkillInstance selInst = skills.get(selectedSkillIdx);
                if (selInst.level() > 0) {
                    int descTopY = skillsTopY + skills.size() * (rowH + rowGap);
                    PetSkill sk  = selInst.skill();

                    final float TEXT_SCALE = 0.80f;
                    final int   MAX_W      = (int) ((rowW - 6) / TEXT_SCALE);
                    final int   LINE_H     = 9;

                    // Main description — wrap to 2 lines if needed, truncate line 2 with "…"
                    String raw = sk.getDescription(selInst.level(), 1.0f).getString();
                    String descL1 = raw, descL2 = null;
                    if (this.font.width(raw) > MAX_W) {
                        int cut = raw.length();
                        while (cut > 0 && this.font.width(raw.substring(0, cut)) > MAX_W) {
                            int sp = raw.lastIndexOf(' ', cut - 1);
                            if (sp <= 0) { cut = 0; break; }
                            cut = sp;
                        }
                        if (cut > 0) {
                            descL1 = raw.substring(0, cut);
                            descL2 = raw.substring(cut + 1);
                            // Truncate second line if still too long
                            while (this.font.width(descL2) > MAX_W) {
                                int sp = descL2.lastIndexOf(' ');
                                if (sp <= 0) break;
                                descL2 = descL2.substring(0, sp) + "\u2026";
                            }
                        }
                    }

                    // Progression — split at " → " so levels and values are on separate lines
                    String prog = buildProgression(sk);
                    String progL1 = prog, progL2 = null;
                    if (this.font.width(prog) > MAX_W) {
                        int arrow = prog.indexOf(" \u2192 ");
                        if (arrow >= 0) {
                            progL1 = prog.substring(0, arrow);
                            progL2 = "\u2192 " + prog.substring(arrow + 4);
                        }
                    }

                    int lineCount = 1 + (descL2 != null ? 1 : 0) + 1 + (progL2 != null ? 1 : 0);
                    int panelH    = 6 + lineCount * LINE_H;

                    g.fill(skillsX, descTopY, skillsX + rowW, descTopY + panelH, 0xFF0D1A0D);
                    g.fill(skillsX, descTopY, skillsX + rowW, descTopY + 1, 0x44FFFFFF);

                    g.pose().pushPose();
                    g.pose().translate(skillsX + 3, descTopY + 3, 0);
                    g.pose().scale(TEXT_SCALE, TEXT_SCALE, 1f);
                    int ly = 0;
                    g.drawString(this.font, descL1, 0, ly, 0xCCCCCC, false); ly += LINE_H;
                    if (descL2 != null) { g.drawString(this.font, descL2, 0, ly, 0xCCCCCC, false); ly += LINE_H; }
                    g.drawString(this.font, progL1, 0, ly, 0x999999, false); ly += LINE_H;
                    if (progL2 != null) { g.drawString(this.font, progL2, 0, ly, 0x999999, false); }
                    g.pose().popPose();
                }
            }
        }

        // ── Second divider + vertical sub-panel divider ───────────────────────
        int div2Y = statusBtnY - 6;
        ArcadiaTheme.drawSeparator(g, cardLeft, div2Y, CARD_W, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x55));
        // Vertical divider separating the stats (left) and bars (right) sub-panels
        int panelDivX = cardLeft + 8 + LEFT_PANEL_W + PANEL_GAP / 2;
        g.fill(panelDivX, cardTop + 56, panelDivX + 1, div2Y, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x33));

        // ── Status cycling button (Recall ↔ Follow ↔ Pocket ↔ Sit) ─────────
        renderStatusBtn(g, mouseX, mouseY);

        // ── Behaviour cycling button (Idle → Defend → Attack) ────────────────
        renderBehaviourBtn(g, mouseX, mouseY);


    }

    /**
     * Mob types that cannot be used in Follow mode.
     */
    private static final java.util.Set<String> FOLLOW_BLACKLIST = java.util.Set.of(
            "minecraft:warden",
            "minecraft:ender_dragon",
            "minecraft:shulker",
            "minecraft:breeze",
            "minecraft:elder_guardian",
            "minecraft:dolphin",
            "minecraft:wither"
    );

    /** Mobs forced to pocket-only (renderer ignores SCALE, so Follow and Sit produce huge entities). */
    private static final java.util.Set<String> POCKET_ONLY = java.util.Set.of(
            "minecraft:ender_dragon"
    );

    private boolean isFollowDisabled() {
        return FOLLOW_BLACKLIST.contains(petData.mobType());
    }

    private boolean isPocketOnly() {
        return POCKET_ONLY.contains(petData.mobType());
    }

    /**
     * Cycling status button: shows current state (Recall / Follow / Pocket / Sit).
     * Click cycles to next state: inactive→first active mode, or between active modes, or→Recall.
     */
    private void renderStatusBtn(GuiGraphics g, int mouseX, int mouseY) {
        boolean hov = mouseX >= statusBtnX && mouseX < statusBtnX + CYCLE_BTN_W
                   && mouseY >= statusBtnY && mouseY < statusBtnY + BTN_H;

        if (cooldownTicks > 0) {
            // Cooldown: show timer, dim button
            int secs = cooldownTicks / 20;
            String cd = "\u23F1 " + (secs / 60) + "m " + (secs % 60) + "s";
            boolean blink = (blinkTick / 10) % 2 == 0;
            g.fill(statusBtnX, statusBtnY, statusBtnX + CYCLE_BTN_W, statusBtnY + BTN_H, 0xFF1A1010);
            g.drawString(this.font, cd,
                    statusBtnX + (CYCLE_BTN_W - this.font.width(cd)) / 2, statusBtnY + 3,
                    blink ? 0xFFFF5555 : 0xFFAA2222, false);
            return;
        }

        int bg; Component lbl;
        if (!petActive) {
            bg  = hov ? 0xFF4A3A28 : 0xFF2A2018;
            lbl = Component.translatable("arcadia_pets.pet.recalled");
        } else {
            int[] movBgs = {0xFF1A4A60, 0xFF1A4A30}; // follow=blue-steel, pocket=green-steel
            bg  = hov ? ArcadiaTheme.brighten(movBgs[selectedMovement.ordinal()], 25) : movBgs[selectedMovement.ordinal()];
            String modeName = Component.translatable(selectedMovement.getTranslationKey()).getString();
            lbl = Component.literal(modeName + " \u25B6");
        }
        g.fill(statusBtnX, statusBtnY, statusBtnX + CYCLE_BTN_W, statusBtnY + BTN_H, bg);
        ArcadiaTheme.drawBorder(g, statusBtnX, statusBtnY, CYCLE_BTN_W, BTN_H,
                hov ? ArcadiaTheme.COPPER : ArcadiaTheme.BORDER_IDLE);
        g.drawString(this.font, lbl,
                statusBtnX + (CYCLE_BTN_W - this.font.width(lbl)) / 2, statusBtnY + 3, ArcadiaTheme.TEXT_PRIMARY, false);
    }

    /** Returns the next movement mode in the cycle, or null to recall (deactivate). */
    private PetMovementMode nextMovementMode() {
        PetMovementMode[] all = PetMovementMode.values();
        for (int i = 0; i < all.length; i++) {
            if (all[i] == selectedMovement) {
                for (int j = i + 1; j < all.length; j++) {
                    if (all[j] == PetMovementMode.FOLLOW && isFollowDisabled()) continue;
                    return all[j];
                }
                return null; // wrapped around: recall
            }
        }
        return null;
    }

    /** Cycling behaviour button: Idle → Defend → Attack → Idle. */
    private void renderBehaviourBtn(GuiGraphics g, int mouseX, int mouseY) {
        boolean hov = mouseX >= behavBtnX && mouseX < behavBtnX + CYCLE_BTN_W
                   && mouseY >= behavBtnY && mouseY < behavBtnY + BTN_H;
        int[] colors = {0xFF3A2840, 0xFF1A4020, 0xFF4A1A14};
        int idx = selectedBehaviour.ordinal();
        int bg  = hov ? ArcadiaTheme.brighten(colors[idx], 25) : colors[idx];
        g.fill(behavBtnX, behavBtnY, behavBtnX + CYCLE_BTN_W, behavBtnY + BTN_H, bg);
        ArcadiaTheme.drawBorder(g, behavBtnX, behavBtnY, CYCLE_BTN_W, BTN_H,
                hov ? ArcadiaTheme.COPPER : ArcadiaTheme.BORDER_IDLE);
        String name = Component.translatable(selectedBehaviour.getTranslationKey()).getString();
        Component lbl = Component.literal(name + " \u25B6");
        g.drawString(this.font, lbl,
                behavBtnX + (CYCLE_BTN_W - this.font.width(lbl)) / 2, behavBtnY + 3, ArcadiaTheme.TEXT_PRIMARY, false);
    }

    private static int brightenColor(int color) {
        return ArcadiaTheme.brighten(color, 30);
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int cardLeft = (this.width - CARD_W) / 2;

        // Gear button → open HUD settings
        if (inBounds(mouseX, mouseY, gearBtnX, gearBtnY, GEAR_BTN_SIZE, GEAR_BTN_SIZE)) {
            playClick();
            Minecraft.getInstance().setScreen(new PetHudSettingsScreen());
            return true;
        }

        // Rename "Set" button
        if (inBounds(mouseX, mouseY, renameBtnX, renameBtnY, RENAME_BTN_W, 12)) {
            playClick();
            sendRename();
            return true;
        }

        // Status cycling button
        if (cooldownTicks <= 0 && inBounds(mouseX, mouseY, statusBtnX, statusBtnY, CYCLE_BTN_W, BTN_H)) {
            playClick();
            if (!petActive) {
                // Summon in first valid movement mode
                PetMovementMode first = (isFollowDisabled() || isPocketOnly()) ? PetMovementMode.POCKET : PetMovementMode.FOLLOW;
                selectedMovement = first;
                petActive = true; // optimistic client-side update so button immediately shows cycling state
                PacketDistributor.sendToServer(new C2SPetAction(C2SPetAction.SUMMON_RECALL, petData.petId()));
                PacketDistributor.sendToServer(new C2SPetAction(
                        C2SPetAction.SET_MOVEMENT * 256 + first.ordinal(), petData.petId()));
            } else {
                PetMovementMode next = nextMovementMode();
                if (next == null) {
                    // Pocket → Recalled: despawn and go back to inactive state
                    petActive = false; // optimistic client-side update
                    PacketDistributor.sendToServer(new C2SPetAction(C2SPetAction.SUMMON_RECALL, petData.petId()));
                } else {
                    selectedMovement = next;
                    PacketDistributor.sendToServer(new C2SPetAction(
                            C2SPetAction.SET_MOVEMENT * 256 + next.ordinal(), petData.petId()));
                }
            }
            return true;
        }

        // Behaviour cycling button
        if (inBounds(mouseX, mouseY, behavBtnX, behavBtnY, CYCLE_BTN_W, BTN_H)) {
            playClick();
            PetBehaviourMode[] modes = PetBehaviourMode.values();
            PetBehaviourMode next = modes[(selectedBehaviour.ordinal() + 1) % modes.length];
            selectedBehaviour = next;
            PacketDistributor.sendToServer(new C2SPetAction(
                    C2SPetAction.SET_BEHAVIOUR * 256 + next.ordinal(), petData.petId()));
            return true;
        }

        // Skill row clicks (toggle button or expand/collapse)
        java.util.List<SkillInstance> skillList = petData.skills();
        if (!skillList.isEmpty() && computedSkillsTopY >= 0) {
            int skillRowsY = computedSkillsTopY;
            int rowH  = BTN_H + 1;
            int rowGap = 2;
            int toggleW = 10;
            for (int i = 0; i < skillList.size(); i++) {
                int rowY = skillRowsY + i * (rowH + rowGap);
                if (skillList.get(i).level() == 0) continue;

                // Toggle button click (left portion of row)
                if (inBounds(mouseX, mouseY, cardLeft + 8, rowY, toggleW, rowH)) {
                    playClick();
                    String skillId = skillList.get(i).skill().getId();
                    Boolean current = skillToggles.getOrDefault(skillId, true);
                    skillToggles.put(skillId, !current);
                    PacketDistributor.sendToServer(new C2SPetAction(
                            C2SPetAction.TOGGLE_SKILL * 256 + i, petData.petId()));
                    return true;
                }

                // Rest of row: expand/collapse description
                if (inBounds(mouseX, mouseY, cardLeft + 8 + toggleW, rowY, LEFT_PANEL_W - toggleW, rowH)) {
                    playClick();
                    selectedSkillIdx = (selectedSkillIdx == i) ? -1 : i;
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Submit rename on Enter when the name field is focused
        if (keyCode == 257 /* Enter */ && nameInput != null && nameInput.isFocused()) {
            sendRename();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendRename() {
        String name = nameInput != null ? nameInput.getValue().strip() : "";
        PacketDistributor.sendToServer(new C2SRenamePet(petData.petId(), name));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean inBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int getRarityColor(PetRarity rarity) {
        return switch (rarity) {
            case COMMON    -> 0xFF808080;
            case UNCOMMON  -> 0xFF2ecc71;
            case RARE      -> 0xFF3498db;
            case EPIC      -> 0xFF9b59b6;
            case LEGENDARY -> 0xFFf39c12;
            case MYTHIC    -> 0xFFe74c3c;
        };
    }

    /**
     * Maps a stat value to its letter tier grade (D / C / B / A / S / S+).
     * Uses relative thresholds against the known max at Tier II with 5★ genes.
     */
    private static String statTier(int value, int max) {
        if (max <= 0) return "D";
        float pct = (float) value / max;
        if (pct >= 0.84f) return "S+";
        if (pct >= 0.68f) return "S";
        if (pct >= 0.51f) return "A";
        if (pct >= 0.34f) return "B";
        if (pct >= 0.17f) return "C";
        return "D";
    }

    /** Returns the background colour for a tier badge. D→S is a red→green gradient; S+ is purple. */
    private static int tierColor(String tier) {
        return switch (tier) {
            case "S+" -> 0xFF8E44AD; // purple (exceptional)
            case "S"  -> 0xFF27AE60; // green
            case "A"  -> 0xFF7CB342; // yellow-green
            case "B"  -> 0xFFD4AC0D; // gold
            case "C"  -> 0xFFE67E22; // orange
            default   -> 0xFFCC3333; // D = red (worst)
        };
    }

    private static int starBarColor(int stars) {
        return switch (stars) {
            case 5  -> 0xFF27AE60; // green
            case 4  -> 0xFF7CB342; // yellow-green
            case 3  -> 0xFFD4AC0D; // gold
            case 2  -> 0xFFE67E22; // orange
            default -> 0xFFCC4444; // red
        };
    }

    private static String formatCd(long ms) {
        long secs = ms / 1000;
        if (secs >= 60) return (secs / 60) + "m " + (secs % 60) + "s";
        return secs + "s";
    }

    private static String buildProgression(PetSkill skill) {
        String v1  = skill.getFormattedValue(1,  1.0f);
        String v10 = skill.getFormattedValue(10, 1.0f);
        if (v1.equals(v10)) return "Lv 1-10: " + v1; // flat skill (e.g. toggle)
        return "Lv 1\u219210: " + v1 + " \u2192 " + v10;
    }
}
