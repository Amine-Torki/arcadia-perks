package com.arcadia.pets.client;

import com.arcadia.lib.client.ArcadiaTheme;
import com.arcadia.pets.duel.DuelSession;
import com.arcadia.pets.item.PetData;
import com.arcadia.pets.network.C2SDuelAction;
import com.arcadia.pets.network.C2SDuelHint;
import com.arcadia.pets.network.S2CDuelState;
import com.arcadia.pets.skill.SkillInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/**
 * Main combat screen — steampunk themed, player-based turns.
 *
 * <p>Reduced background opacity so the game world remains visible.
 * Real-time opponent activity hints are shown on their pet cards.
 * Target selection dims non-targetable cards and adds a clear "▶ TARGET" label.</p>
 */
public class DuelScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int PET_CARD_W = 68;
    private static final int PET_CARD_H = 62;
    private static final int PET_GAP    = 6;
    private static final int LOG_LINES  = 5;
    private static final int LOG_LINE_H = 9;
    private static final int BTN_H      = 14;
    private static final int BTN_GAP    = 4;

    // ── Turn state ────────────────────────────────────────────────────────────
    private int     selectedPetIdx  = -1;
    private boolean attackTargeting = false;
    private boolean defendTargeting = false;
    private String  pendingSkillId  = null;
    private boolean targetingAlly   = false;

    // ── UI state ──────────────────────────────────────────────────────────────
    private boolean forfeitConfirm  = false;
    private int     logScroll       = 0;

    // ── Hint dedup (avoid spamming the server with identical hints) ───────────
    private int lastSentHintType = -1;
    private int lastSentHintPet  = -1;

    public DuelScreen() {
        super(Component.translatable("arcadia_pets.gui.duel.title"));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() {
        S2CDuelState s = DuelClientState.get();
        return s != null && s.phaseOrdinal() == com.arcadia.pets.duel.DuelPhase.FINISHED.ordinal();
    }

    // =========================================================================
    // Render
    // =========================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Reduced opacity — game world still visible through the UI
        g.fill(0, 0, width, height, 0x990A0810);

        S2CDuelState state = DuelClientState.get();
        if (state == null) { super.render(g, mouseX, mouseY, partial); return; }

        Minecraft mc = Minecraft.getInstance();
        UUID localUuid = mc.player != null ? mc.player.getUUID() : null;
        if (localUuid == null) { super.render(g, mouseX, mouseY, partial); return; }

        int mySide  = localUuid.equals(state.p1()) ? 0 : 1;
        int oppSide = 1 - mySide;

        PetData[][] rosters = DuelClientState.getRosters();
        if (rosters == null) { super.render(g, mouseX, mouseY, partial); return; }

        int cx = width / 2;

        // ── End-of-duel overlay ───────────────────────────────────────────────
        if (state.phaseOrdinal() == com.arcadia.pets.duel.DuelPhase.FINISHED.ordinal()) {
            renderTeamPanel(g, rosters[oppSide], oppSide, mySide, state,
                    false, localUuid, 8, 10, mouseX, mouseY);
            renderTeamPanel(g, rosters[mySide],  mySide,  mySide, state,
                    true,  localUuid, cx + 8, 10, mouseX, mouseY);
            renderVsDivider(g, cx);
            renderDuelEndOverlay(g, state, localUuid, cx);
            super.render(g, mouseX, mouseY, partial);
            return;
        }

        // ── Active duel ───────────────────────────────────────────────────────
        int teamY = 10;
        renderTeamPanel(g, rosters[oppSide], oppSide, mySide, state,
                false, localUuid, 8, teamY, mouseX, mouseY);
        renderTeamPanel(g, rosters[mySide],  mySide,  mySide, state,
                true,  localUuid, cx + 8, teamY, mouseX, mouseY);
        renderVsDivider(g, cx);

        // Opponent activity hint line — sits just below opponent's team cards
        renderOpponentHintLine(g, rosters[oppSide], oppSide, state, localUuid, teamY);

        int barY = teamY + PET_CARD_H + 6;
        renderTurnInfoBar(g, state, mySide, localUuid, cx, barY);

        int logY = barY + 12;
        renderCombatLog(g, state.combatLog(), logY);

        int actionY = logY + LOG_LINES * LOG_LINE_H + 8;

        boolean deadlinePassed = state.actionDeadline() > 0
                && System.currentTimeMillis() > state.actionDeadline();
        boolean myTurn = localUuid.equals(state.actorUuid()) && !deadlinePassed;

        if (!myTurn) {
            selectedPetIdx  = -1;
            attackTargeting = false;
            defendTargeting = false;
            pendingSkillId  = null;
        }

        if (myTurn) {
            renderActionArea(g, state, mySide, rosters[mySide], cx, actionY, mouseX, mouseY);
            renderEndTurnButton(g, mouseX, mouseY);
        } else if (deadlinePassed) {
            renderDeadlinePassedArea(g, cx, actionY);
        } else {
            renderWaitingArea(g, state, cx, actionY);
        }

        renderForfeitButton(g, mouseX, mouseY);
        if (forfeitConfirm) renderForfeitConfirmOverlay(g, cx, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partial);
    }

    // ── VS divider ────────────────────────────────────────────────────────────

    private void renderVsDivider(GuiGraphics g, int cx) {
        ArcadiaTheme.drawCenteredText(g,
                Component.literal("VS").withStyle(s -> s.withBold(true)),
                cx, 10 + PET_CARD_H / 2 - 4, ArcadiaTheme.BRASS);
        g.fill(cx - 1, 10, cx, 10 + PET_CARD_H,
                ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x44));
    }

    // ── Team panel ────────────────────────────────────────────────────────────

    private void renderTeamPanel(GuiGraphics g, PetData[] roster,
                                  int side, int mySide,
                                  S2CDuelState state, boolean isMine, UUID localUuid,
                                  int startX, int y, int mouseX, int mouseY) {
        List<Integer> pending = state.pendingPetActions();
        boolean myTurn = localUuid.equals(state.actorUuid());
        int oppHintType = DuelClientState.opponentHintType();
        int oppHintPet  = DuelClientState.opponentHintPet();

        for (int i = 0; i < 3; i++) {
            int cx = startX + i * (PET_CARD_W + PET_GAP);
            boolean isPending  = myTurn && isMine && pending.contains(i);
            boolean isSelected = isPending && selectedPetIdx == i;

            // Opponent hint flags for this card
            boolean oppSelectedThis = !isMine && oppHintType == C2SDuelHint.PET_SELECTED
                    && oppHintPet == i;
            boolean oppTargetingMe  = isMine && (oppHintType == C2SDuelHint.ATTACK_TARGETING
                    || oppHintType == C2SDuelHint.SKILL_TARGETING_E);

            renderPetCard(g, roster[i], side, i, state, isMine,
                    isPending, isSelected, oppSelectedThis, oppTargetingMe,
                    cx, y, mouseX, mouseY);
        }
    }

    /**
     * Renders a single pet card with all visual states:
     * pending/selected (my turn), opponent-selected hint, opponent-targeting warning,
     * attack/skill targeting highlights, KO overlay, HP bar, status effects.
     */
    private void renderPetCard(GuiGraphics g, PetData pd, int side, int petIdx,
                                S2CDuelState state, boolean isMine,
                                boolean isPending, boolean isSelected,
                                boolean oppSelectedThis, boolean oppTargetingMe,
                                int x, int y, int mouseX, int mouseY) {
        int hp    = DuelClientState.hp(side, petIdx);
        int maxHp = DuelClientState.maxHp(side, petIdx);
        boolean alive = hp > 0;

        boolean inTargetingMode = attackTargeting || pendingSkillId != null || defendTargeting;
        boolean isTargetable = alive && (
                (attackTargeting && !isMine) ||
                (pendingSkillId != null && (targetingAlly == isMine)) ||
                (defendTargeting && isMine));
        boolean isDimmed = inTargetingMode && !isTargetable;

        // Accent colour — team-based (mine=teal, opp=rust); AMBER when selected/pending
        int teamColor   = isMine ? 0xFF2299BB : 0xFF993322;
        int accentColor;
        if (isSelected)       accentColor = ArcadiaTheme.AMBER;
        else if (isPending)   accentColor = ArcadiaTheme.withAlpha(ArcadiaTheme.AMBER, 0xCC);
        else                  accentColor = ArcadiaTheme.withAlpha(teamColor, 0x99);

        if (isSelected) ArcadiaTheme.drawGlow(g, x, y, PET_CARD_W, PET_CARD_H, accentColor);
        ArcadiaTheme.drawPanel(g, x, y, PET_CARD_W, PET_CARD_H, isSelected, accentColor);

        // Hover highlight on pending cards (shows they're clickable)
        if (isPending && !isSelected) {
            boolean pendHov = mouseX >= x && mouseX <= x + PET_CARD_W
                    && mouseY >= y && mouseY <= y + PET_CARD_H;
            if (pendHov) g.fill(x + 1, y + 1, x + PET_CARD_W - 1, y + PET_CARD_H - 1, 0x22FFDD44);
        }

        // KO overlay
        if (!alive) {
            g.fill(x + 1, y + 1, x + PET_CARD_W - 1, y + PET_CARD_H - 1, 0xAA000000);
            g.drawCenteredString(font, "§4✗ KO", x + PET_CARD_W / 2, y + PET_CARD_H / 2 - 4, 0xFF4444);
            return;
        }
        if (pd == null) return;

        // Pet name
        String name = pd.customName() != null ? pd.customName() : mobShortName(pd.mobType());
        g.drawString(font, truncate(name, 8), x + 4, y + 4, ArcadiaTheme.TEXT_PRIMARY, false);

        // Pending / acted badge (top-right corner)
        if (isPending) {
            String badge = isSelected ? "▶" : "·";
            int badgeColor = isSelected ? ArcadiaTheme.AMBER
                    : ArcadiaTheme.withAlpha(ArcadiaTheme.AMBER, 0xBB);
            g.drawString(font, badge, x + PET_CARD_W - font.width(badge) - 3, y + 4,
                    badgeColor, false);
        }

        // HP bar
        int barW = PET_CARD_W - 8;
        int barH = 4;
        int barX = x + 4;
        int barY = y + 15;
        g.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1,
                ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x55));
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF1A1010);
        float hpFrac = maxHp > 0 ? (float) hp / maxHp : 0f;
        int fillW   = (int)(barW * hpFrac);
        int hpColor = hpFrac > 0.5f ? 0xFF44CC44 : hpFrac > 0.25f ? 0xFFCCAA00 : 0xFFCC2222;
        if (fillW > 0) g.fill(barX, barY, barX + fillW, barY + barH, hpColor);
        g.drawString(font, hp + "/" + maxHp, barX, barY + barH + 2, ArcadiaTheme.TEXT_DIM, false);

        // Status effects (compact, up to 4)
        List<String> effects = DuelClientState.effectLabels(side, petIdx);
        int fxY = barY + barH + 12;
        for (int e = 0; e < Math.min(effects.size(), 4); e++) {
            g.drawString(font, effects.get(e),
                    x + 4 + (e % 2) * (PET_CARD_W / 2), fxY + (e / 2) * 8,
                    ArcadiaTheme.withAlpha(ArcadiaTheme.PATINA, 0xCC), false);
        }

        // ── Opponent hint overlays ─────────────────────────────────────────────

        // Opponent selected this pet — teal border + small eye icon
        if (oppSelectedThis) {
            int teal = 0xFF44DDCC;
            ArcadiaTheme.drawBorder(g, x, y, PET_CARD_W, PET_CARD_H, teal);
            g.drawString(font, "👁", x + PET_CARD_W - font.width("👁") - 3,
                    y + PET_CARD_H - 11,
                    ArcadiaTheme.withAlpha(teal, 0xCC), false);
        }

        // Opponent is targeting my pets — pulsing red warning
        if (oppTargetingMe) {
            long t = System.currentTimeMillis();
            int alpha = (int)(((Math.sin(t / 300.0) + 1.0) / 2.0) * 0x55) + 0x22;
            g.fill(x, y, x + PET_CARD_W, y + PET_CARD_H,
                    (alpha << 24) | 0x00CC2222);
            if ((t / 500) % 2 == 0)
                ArcadiaTheme.drawBorder(g, x, y, PET_CARD_W, PET_CARD_H, 0xFFCC2222);
            g.drawCenteredString(font, "⚠",
                    x + PET_CARD_W / 2, y + PET_CARD_H - 11, 0xFFFF4444);
        }

        // ── Targeting mode overlays ────────────────────────────────────────────

        if (isDimmed) {
            // Dim non-targetable cards so targetable ones stand out
            g.fill(x + 1, y + 1, x + PET_CARD_W - 1, y + PET_CARD_H - 1, 0xAA000000);
        }

        if (isTargetable) {
            boolean hov = mouseX >= x && mouseX <= x + PET_CARD_W
                    && mouseY >= y && mouseY <= y + PET_CARD_H;

            // Color-coded by action intent: red = hostile, green = friendly
            boolean hostile = attackTargeting || (pendingSkillId != null && !targetingAlly);
            int tgtColor  = hostile ? 0xFFDD3333 : 0xFF44CC66;
            int hovFill   = hostile ? 0x33FF4444 : 0x3344FF88;
            String tLabel = hov ? "▶ SELECT" : (hostile ? "▶ TARGET" : "▶ BUFF");

            // Always-on border
            ArcadiaTheme.drawBorder(g, x, y, PET_CARD_W, PET_CARD_H, tgtColor);

            // Pulsing glow every 500 ms
            if ((System.currentTimeMillis() / 500) % 2 == 0)
                ArcadiaTheme.drawGlow(g, x, y, PET_CARD_W, PET_CARD_H, tgtColor);

            // Hover fill
            if (hov) g.fill(x, y, x + PET_CARD_W, y + PET_CARD_H, hovFill);

            // Label at card bottom
            int tColor = hov ? tgtColor : ArcadiaTheme.withAlpha(tgtColor, 0xCC);
            g.drawCenteredString(font, tLabel, x + PET_CARD_W / 2, y + PET_CARD_H - 10, tColor);
        }
    }

    // ── Opponent hint line ────────────────────────────────────────────────────

    /**
     * Shows a one-line activity indicator just below the opponent's team panel,
     * visible only when the opponent is doing something interesting on their turn.
     */
    private void renderOpponentHintLine(GuiGraphics g, PetData[] oppRoster, int oppSide,
                                         S2CDuelState state, UUID localUuid, int teamY) {
        // Only show when it's the opponent's turn (or just after they started acting)
        if (localUuid.equals(state.actorUuid())) return; // it's my turn, no hint needed

        int hintType = DuelClientState.opponentHintType();
        int hintPet  = DuelClientState.opponentHintPet();
        if (hintType < 0) return;

        String petName = "";
        if (hintPet >= 0 && hintPet < 3 && oppRoster[hintPet] != null) {
            PetData pd = oppRoster[hintPet];
            petName = pd.customName() != null ? pd.customName() : mobShortName(pd.mobType());
        }

        String msg = switch (hintType) {
            case C2SDuelHint.PET_SELECTED      -> "👁  Opponent is acting with " + petName + "…";
            case C2SDuelHint.ATTACK_TARGETING  -> "⚔  Opponent's " + petName + " is picking an attack target…";
            case C2SDuelHint.SKILL_TARGETING_E -> "🔮  Opponent's " + petName + " is targeting your team…";
            case C2SDuelHint.SKILL_TARGETING_A -> "✨  Opponent's " + petName + " is using a skill on their pets…";
            default -> "";
        };
        if (msg.isEmpty()) return;

        int lineY = teamY + PET_CARD_H + 1;
        g.drawString(font, msg, 8, lineY,
                ArcadiaTheme.withAlpha(0xFF44DDCC, 0xBB), false);
    }

    // ── Turn info bar ─────────────────────────────────────────────────────────

    private void renderTurnInfoBar(GuiGraphics g, S2CDuelState state,
                                    int mySide, UUID localUuid, int cx, int y) {
        int round = state.roundNumber() + 1;
        g.drawString(font,
                Component.literal("Round ").append(
                        Component.literal(String.valueOf(round)).withStyle(ChatFormatting.YELLOW)),
                10, y + 1, ArcadiaTheme.TEXT_SECONDARY, false);

        boolean myTurn = localUuid.equals(state.actorUuid());
        String  label  = myTurn ? "★ Your Turn" : "⟳ Opponent's Turn";
        int     color  = myTurn ? ArcadiaTheme.AMBER : ArcadiaTheme.TEXT_DIM;
        ArcadiaTheme.drawCenteredText(g, Component.literal(label), cx, y + 1, color);

        // Pending pet indicators
        List<Integer> pending = state.pendingPetActions();
        int slotW = 14, slotH = 10, gap = 2;
        int slotsX = cx + 55;
        for (int i = 0; i < 3; i++) {
            boolean pend = pending.contains(i);
            int sx = slotsX + i * (slotW + gap);
            int bg = pend
                    ? (myTurn ? ArcadiaTheme.withAlpha(ArcadiaTheme.AMBER, 0xBB)
                               : ArcadiaTheme.withAlpha(0xFFCC2222, 0x77))
                    : ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x22);
            g.fill(sx, y, sx + slotW, y + slotH, bg);
            ArcadiaTheme.drawBorder(g, sx, y, slotW, slotH,
                    pend ? (myTurn ? ArcadiaTheme.BRASS
                                   : ArcadiaTheme.withAlpha(0xFFCC2222, 0x66))
                         : ArcadiaTheme.withAlpha(ArcadiaTheme.BORDER_IDLE, 0x33));
            g.drawCenteredString(font, "P" + (i + 1), sx + slotW / 2, y + 1,
                    pend ? 0xFF0A0810 : ArcadiaTheme.TEXT_DIM);
        }
    }

    // ── Combat log ────────────────────────────────────────────────────────────

    private void renderCombatLog(GuiGraphics g, List<String> log, int y) {
        int logW = width - 20;
        int logX = 10;
        int logH = LOG_LINES * LOG_LINE_H + 6;

        g.fill(logX + 2, y + 2, logX + logW + 2, y + logH + 2, 0x22000000); // shadow
        g.fill(logX, y, logX + logW, y + logH, 0xBB0E0B14);
        g.fill(logX, y, logX + logW, y + 2, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x88));
        ArcadiaTheme.drawBorder(g, logX, y, logW, logH,
                ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x55));

        int total   = log.size();
        int visible = Math.min(LOG_LINES, total);
        int start   = Math.max(0, total - LOG_LINES - logScroll);
        for (int i = 0; i < visible; i++) {
            int idx = start + i;
            if (idx >= total) break;
            g.drawString(font, "§8›§r " + log.get(idx),
                    logX + 4, y + 3 + i * LOG_LINE_H, ArcadiaTheme.TEXT_SECONDARY, false);
        }
    }

    // ── Action area ───────────────────────────────────────────────────────────

    private void renderActionArea(GuiGraphics g, S2CDuelState state, int mySide,
                                   PetData[] roster, int cx, int y, int mx, int my) {
        int sp  = state.currentSP();
        int spX = 10, spY = y;

        // SP pips
        g.drawString(font, "SP", spX, spY, ArcadiaTheme.TEXT_SECONDARY, false);
        spX += font.width("SP") + 3;
        for (int i = 0; i < DuelSession.SP_MAX; i++) {
            boolean filled = i < sp;
            int pip = filled ? ArcadiaTheme.AMBER : ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x55);
            g.fill(spX + i * 9, spY, spX + i * 9 + 7, spY + 7, pip);
            if (filled) ArcadiaTheme.drawBorder(g, spX + i * 9, spY, 7, 7, ArcadiaTheme.BRASS);
            else        ArcadiaTheme.drawBorder(g, spX + i * 9, spY, 7, 7,
                            ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x44));
        }

        // Countdown timer (right-aligned)
        if (state.actionDeadline() > 0) {
            long remaining = (state.actionDeadline() - System.currentTimeMillis()) / 1000;
            if (remaining <= 15 && remaining >= 0) {
                int col = remaining <= 5  ? 0xFFFF4444
                        : remaining <= 10 ? ArcadiaTheme.AMBER
                        : ArcadiaTheme.TEXT_SECONDARY;
                String ts = "⏰ " + remaining + "s";
                g.drawString(font, ts, width - font.width(ts) - 6, y, col, false);
            }
        }

        int actY  = y + 11;
        List<Integer> pending = state.pendingPetActions();

        if (attackTargeting) {
            renderTargetingBanner(g, cx, actY, "⚔  Select an enemy pet to attack — ESC to cancel", 0xFFDD3333);

        } else if (defendTargeting) {
            renderTargetingBanner(g, cx, actY, "🛡  Select an ally pet to protect — ESC to cancel", 0xFF44CC66);

        } else if (pendingSkillId != null) {
            boolean hostile = !targetingAlly;
            String direction = targetingAlly ? "an ally pet" : "an enemy pet";
            int bannerColor  = hostile ? 0xFFDD3333 : 0xFF44CC66;
            renderTargetingBanner(g, cx, actY, "🔮  Select " + direction + " — ESC to cancel", bannerColor);

        } else if (selectedPetIdx >= 0 && pending.contains(selectedPetIdx)) {
            PetData actor = roster[selectedPetIdx];
            if (actor == null) return;

            String actorName = actor.customName() != null
                    ? actor.customName() : mobShortName(actor.mobType());
            g.drawString(font,
                    Component.literal("Acting: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(actorName).withStyle(ChatFormatting.WHITE)),
                    10, actY, ArcadiaTheme.TEXT_SECONDARY, false);

            int btnX  = 10;
            int btnY2 = actY + 11;
            btnX = renderActionButton(g, "← Back",    "",     btnX, btnY2, true, mx, my) + BTN_GAP;
            btnX = renderActionButton(g, "⚔ Attack",  "FREE", btnX, btnY2, true, mx, my) + BTN_GAP;
            btnX = renderActionButton(g, "🛡 Defend",  "FREE", btnX, btnY2, true, mx, my) + BTN_GAP;
            renderActionButton(g, "↩ Skip", "", btnX, btnY2, true, mx, my);

            // Skill buttons row
            int skillRowY = btnY2 + BTN_H + 4;
            int skillBtnX = 10;
            String hovSkillDesc = "";
            for (SkillInstance si : actor.skills()) {
                if (si.level() <= 0) continue;
                int cd = DuelClientState.skillCooldown(mySide, selectedPetIdx, si.skill().getId());
                com.arcadia.pets.duel.DuelSkillDef def =
                        com.arcadia.pets.duel.DuelSkillAdapter.get(si.skill().getId());
                if (def == null) continue;
                boolean ready = cd == 0 && sp >= def.spCost;
                String label  = si.skill().getDisplayName().getString();
                String badge  = def.spCost + "SP" + (cd > 0 ? " CD:" + cd : "");
                int bwSkill = font.width(label + " (" + badge + ")") + 12;
                if (mx >= skillBtnX && mx <= skillBtnX + bwSkill
                        && my >= skillRowY && my <= skillRowY + BTN_H) {
                    hovSkillDesc = com.arcadia.pets.duel.DuelSkillAdapter.getDescription(si.skill().getId());
                }
                skillBtnX = renderActionButton(g, label, badge, skillBtnX, skillRowY, ready, mx, my) + BTN_GAP;
                if (skillBtnX > width - 80) { skillBtnX = 10; skillRowY += BTN_H + 3; }
            }
            if (!hovSkillDesc.isEmpty())
                g.drawString(font, hovSkillDesc, 10, skillRowY + BTN_H + 3, ArcadiaTheme.TEXT_DIM, false);

        } else {
            String prompt = pending.isEmpty()
                    ? "All pets have acted.  End Turn or wait for timeout."
                    : "▶  Click one of your pending pets (card above) to choose its action";
            ArcadiaTheme.drawCenteredText(g, Component.literal(prompt), cx, actY + 3,
                    pending.isEmpty() ? ArcadiaTheme.TEXT_DIM : ArcadiaTheme.AMBER);
        }
    }

    /** Renders a full-width highlighted instruction banner for targeting mode. */
    private void renderTargetingBanner(GuiGraphics g, int cx, int y, String text, int color) {
        int bw = width - 40, bh = BTN_H + 4;
        int bx = 20;
        g.fill(bx, y - 1, bx + bw, y + bh - 1, ArcadiaTheme.withAlpha(color, 0x1A));
        g.fill(bx, y - 1, bx + bw, y,           ArcadiaTheme.withAlpha(color, 0xBB));
        ArcadiaTheme.drawBorder(g, bx, y - 1, bw, bh, ArcadiaTheme.withAlpha(color, 0x88));
        ArcadiaTheme.drawCenteredText(g, Component.literal(text), cx, y + 3, color);
    }

    private int renderActionButton(GuiGraphics g, String label, String badge,
                                    int x, int y, boolean enabled, int mx, int my) {
        String full = label + (badge.isEmpty() ? "" : " (" + badge + ")");
        int btnW = font.width(full) + 12;
        boolean hov = enabled && mx >= x && mx <= x + btnW && my >= y && my <= y + BTN_H;

        g.fill(x + 1, y + 1, x + btnW + 1, y + BTN_H + 1, 0x33000000);
        int bg = !enabled ? 0x881A1620 : hov ? ArcadiaTheme.withAlpha(ArcadiaTheme.BRONZE, 0xDD) : 0xCC1A1620;
        g.fill(x, y, x + btnW, y + BTN_H, bg);
        if (enabled)
            g.fill(x, y, x + btnW, y + 1,
                    hov ? ArcadiaTheme.AMBER : ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0xAA));
        ArcadiaTheme.drawBorder(g, x, y, btnW, BTN_H,
                !enabled ? ArcadiaTheme.withAlpha(ArcadiaTheme.BORDER_IDLE, 0x55)
                : hov    ? ArcadiaTheme.BRASS
                         : ArcadiaTheme.BORDER_IDLE);
        int textColor = !enabled ? ArcadiaTheme.TEXT_DIM
                : hov ? 0xFF0A0810 : ArcadiaTheme.TEXT_PRIMARY;
        g.drawString(font, label, x + 5, y + 3, textColor, false);
        if (!badge.isEmpty())
            g.drawString(font, " (" + badge + ")", x + 5 + font.width(label), y + 3,
                    !enabled ? ArcadiaTheme.TEXT_DIM : hov ? 0x88000000 : ArcadiaTheme.TEXT_DIM, false);
        return x + btnW;
    }

    private void renderWaitingArea(GuiGraphics g, S2CDuelState state, int cx, int y) {
        long remaining = state.actionDeadline() > 0
                ? (state.actionDeadline() - System.currentTimeMillis()) / 1000 : 0;
        String msg = "Waiting for opponent… (" + Math.max(0, remaining) + "s)";
        int panelW = font.width(msg) + 24;
        int panelX = cx - panelW / 2;
        g.fill(panelX, y + 4, panelX + panelW, y + 4 + BTN_H + 2, 0x880E0B14);
        ArcadiaTheme.drawBorder(g, panelX, y + 4, panelW, BTN_H + 2,
                ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x55));
        g.drawCenteredString(font, msg, cx, y + 8, ArcadiaTheme.TEXT_DIM);
    }

    private void renderDeadlinePassedArea(GuiGraphics g, int cx, int y) {
        String msg = "⏰ Time up — auto-passing turn…";
        int panelW = font.width(msg) + 24;
        int panelX = cx - panelW / 2;
        g.fill(panelX, y + 4, panelX + panelW, y + 4 + BTN_H + 2, 0x880E0B14);
        ArcadiaTheme.drawBorder(g, panelX, y + 4, panelW, BTN_H + 2,
                ArcadiaTheme.withAlpha(0xFFCC2222, 0x66));
        g.drawCenteredString(font, msg, cx, y + 8, 0xFFCC6644);
    }

    // ── End Turn button (bottom right) ────────────────────────────────────────

    private void renderEndTurnButton(GuiGraphics g, int mx, int my) {
        String label = "⏭ End Turn";
        int bw = font.width(label) + 12;
        int bx = width - bw - 4, by = height - BTN_H - 4;
        boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + BTN_H;
        g.fill(bx, by, bx + bw, by + BTN_H, hov ? ArcadiaTheme.withAlpha(ArcadiaTheme.PATINA, 0xDD) : 0x991A2020);
        if (hov) g.fill(bx, by, bx + bw, by + 1, ArcadiaTheme.PATINA);
        ArcadiaTheme.drawBorder(g, bx, by, bw, BTN_H,
                hov ? ArcadiaTheme.PATINA : ArcadiaTheme.withAlpha(ArcadiaTheme.PATINA, 0x66));
        g.drawString(font, label, bx + 5, by + 3,
                hov ? 0xFF0A0810 : ArcadiaTheme.TEXT_SECONDARY, false);
    }

    // ── Forfeit ───────────────────────────────────────────────────────────────

    private void renderForfeitButton(GuiGraphics g, int mx, int my) {
        int bx = 4, by = height - BTN_H - 4;
        int bw = font.width("🏳 Forfeit") + 12;
        boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + BTN_H;
        g.fill(bx, by, bx + bw, by + BTN_H, hov ? 0xDD4A1010 : 0x882A1010);
        ArcadiaTheme.drawBorder(g, bx, by, bw, BTN_H,
                hov ? 0xFFCC3333 : ArcadiaTheme.withAlpha(0xFFCC3333, 0x66));
        g.drawString(font, "🏳 Forfeit", bx + 5, by + 3,
                hov ? 0xFFFF6666 : 0xFFAA4444, false);
    }

    private void renderForfeitConfirmOverlay(GuiGraphics g, int cx, int mx, int my) {
        g.fill(0, 0, width, height, 0x77000000);
        int pw = 180, ph = 54;
        int px = cx - pw / 2, py = height / 2 - ph / 2;
        ArcadiaTheme.drawPanel(g, px, py, pw, ph, false, 0xFFCC3333);
        ArcadiaTheme.drawCenteredText(g, Component.translatable("arcadia_pets.gui.duel.abandon"),
                cx, py + 8, 0xFFFF8888);

        int yBtns = py + ph - BTN_H - 8;
        int yesBtnW = font.width("✔ Yes, forfeit") + 12;
        int yesX    = cx - yesBtnW - 4;
        boolean hovYes = mx >= yesX && mx <= yesX + yesBtnW && my >= yBtns && my <= yBtns + BTN_H;
        g.fill(yesX, yBtns, yesX + yesBtnW, yBtns + BTN_H, hovYes ? 0xFF4A1010 : 0xDD2A1010);
        ArcadiaTheme.drawBorder(g, yesX, yBtns, yesBtnW, BTN_H, 0xFFCC3333);
        g.drawString(font, "✔ Yes, forfeit", yesX + 5, yBtns + 3, 0xFFFF6666, false);

        int noBtnW = font.width("✖ Cancel") + 12;
        int noX    = cx + 4;
        boolean hovNo = mx >= noX && mx <= noX + noBtnW && my >= yBtns && my <= yBtns + BTN_H;
        g.fill(noX, yBtns, noX + noBtnW, yBtns + BTN_H,
                hovNo ? ArcadiaTheme.withAlpha(ArcadiaTheme.BRONZE, 0xDD) : 0xDD1A1620);
        ArcadiaTheme.drawBorder(g, noX, yBtns, noBtnW, BTN_H,
                hovNo ? ArcadiaTheme.BRASS : ArcadiaTheme.BORDER_IDLE);
        g.drawString(font, "✖ Cancel", noX + 5, yBtns + 3,
                hovNo ? ArcadiaTheme.TEXT_PRIMARY : ArcadiaTheme.TEXT_SECONDARY, false);
    }

    // ── End-of-duel overlay ───────────────────────────────────────────────────

    private void renderDuelEndOverlay(GuiGraphics g, S2CDuelState state,
                                       UUID localUuid, int cx) {
        boolean won = localUuid.equals(state.winner());
        g.fill(0, 0, width, height, 0xAA000000);
        int bw = 180, bh = 68;
        int bx = cx - bw / 2, by = height / 2 - bh / 2 - 20;
        int accent = won ? ArcadiaTheme.PATINA : 0xFFCC2222;
        ArcadiaTheme.drawPanel(g, bx, by, bw, bh, false, accent);
        if (won) ArcadiaTheme.drawGlow(g, bx, by, bw, bh, ArcadiaTheme.PATINA);

        ArcadiaTheme.drawCenteredText(g,
                Component.literal(won ? "★ VICTORY!" : "✗ DEFEAT").withStyle(s -> s.withBold(true)),
                cx, by + 12, won ? ArcadiaTheme.PATINA : 0xFFFF4444);
        String sub = won ? "Triumphed in " + state.roundNumber() + " round(s)!"
                : "Lasted " + state.roundNumber() + " round(s). Better luck next time.";
        g.drawCenteredString(font, sub, cx, by + 28, ArcadiaTheme.TEXT_SECONDARY);
        ArcadiaTheme.drawSeparator(g, bx, by + 42, bw, ArcadiaTheme.withAlpha(accent, 0x66));
        g.drawCenteredString(font, "Press ESC to close", cx, by + bh - 13, ArcadiaTheme.TEXT_DIM);

        List<String> log = state.combatLog();
        int show = Math.min(4, log.size());
        for (int i = 0; i < show; i++)
            g.drawCenteredString(font, "§8" + log.get(log.size() - show + i),
                    cx, by + bh + 8 + i * 9, ArcadiaTheme.TEXT_DIM);
    }

    // =========================================================================
    // Input
    // =========================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);

        S2CDuelState state = DuelClientState.get();
        if (state == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        UUID local = mc.player.getUUID();

        // ── Forfeit confirm overlay ───────────────────────────────────────────
        if (forfeitConfirm) {
            int cx = width / 2;
            int pw = 180, ph = 54;
            int px = cx - pw / 2, py = height / 2 - ph / 2;
            int yBtns = py + ph - BTN_H - 8;
            int yesBtnW = font.width("✔ Yes, forfeit") + 12;
            int yesX    = cx - yesBtnW - 4;
            if (mx >= yesX && mx <= yesX + yesBtnW && my >= yBtns && my <= yBtns + BTN_H) {
                playSound(SoundEvents.ANVIL_LAND);
                PacketDistributor.sendToServer(new C2SDuelAction(C2SDuelAction.FORFEIT, "", 0, 0));
                sendHint(C2SDuelHint.CLEARED, -1);
                forfeitConfirm = false;
                return true;
            }
            int noBtnW = font.width("✖ Cancel") + 12;
            int noX    = cx + 4;
            if (mx >= noX && mx <= noX + noBtnW && my >= yBtns && my <= yBtns + BTN_H) {
                playSound(SoundEvents.UI_BUTTON_CLICK);
                forfeitConfirm = false;
                return true;
            }
            playSound(SoundEvents.UI_BUTTON_CLICK);
            forfeitConfirm = false;
            return true;
        }

        // ── Forfeit button ────────────────────────────────────────────────────
        {
            int fbx = 4, fby = height - BTN_H - 4;
            int fbw = font.width("🏳 Forfeit") + 12;
            if (mx >= fbx && mx <= fbx + fbw && my >= fby && my <= fby + BTN_H) {
                playSound(SoundEvents.UI_BUTTON_CLICK);
                forfeitConfirm = true;
                return true;
            }
        }

        // ── End Turn button ───────────────────────────────────────────────────
        {
            String endLabel = "⏭ End Turn";
            int ebw = font.width(endLabel) + 12;
            int ebx = width - ebw - 4, eby = height - BTN_H - 4;
            if (mx >= ebx && mx <= ebx + ebw && my >= eby && my <= eby + BTN_H) {
                boolean dp = state.actionDeadline() > 0
                        && System.currentTimeMillis() > state.actionDeadline();
                if (local.equals(state.actorUuid()) && !dp) {
                    playSound(SoundEvents.UI_BUTTON_CLICK);
                    PacketDistributor.sendToServer(new C2SDuelAction(C2SDuelAction.PASS, "", 0, 0));
                    sendHint(C2SDuelHint.CLEARED, -1);
                    selectedPetIdx = -1; attackTargeting = false; defendTargeting = false; pendingSkillId = null;
                    return true;
                }
            }
        }

        // ── Turn / deadline guard ─────────────────────────────────────────────
        boolean deadlinePassed = state.actionDeadline() > 0
                && System.currentTimeMillis() > state.actionDeadline();
        if (!local.equals(state.actorUuid()) || deadlinePassed) return false;

        int mySide  = local.equals(state.p1()) ? 0 : 1;
        int oppSide = 1 - mySide;
        int cx      = width / 2;
        int cardY   = 10;

        List<Integer> pending = state.pendingPetActions();
        PetData[][] rosters = DuelClientState.getRosters();
        if (rosters == null) return false;

        // ── Attack targeting: click enemy pet ─────────────────────────────────
        if (attackTargeting) {
            for (int i = 0; i < 3; i++) {
                int cardX = 8 + i * (PET_CARD_W + PET_GAP);
                if (mx >= cardX && mx <= cardX + PET_CARD_W
                        && my >= cardY && my <= cardY + PET_CARD_H
                        && DuelClientState.hp(oppSide, i) > 0) {
                    playSound(SoundEvents.UI_BUTTON_CLICK);
                    PacketDistributor.sendToServer(
                            new C2SDuelAction(C2SDuelAction.ATTACK, "", selectedPetIdx, i));
                    sendHint(C2SDuelHint.CLEARED, -1);
                    attackTargeting = false;
                    selectedPetIdx  = -1;
                    return true;
                }
            }
            // My card clicked → ignore (stay in attack targeting mode)
            for (int i = 0; i < 3; i++) {
                int myCardX = cx + 8 + i * (PET_CARD_W + PET_GAP);
                if (mx >= myCardX && mx <= myCardX + PET_CARD_W
                        && my >= cardY && my <= cardY + PET_CARD_H) return true;
            }
            // Click elsewhere → cancel
            playSound(SoundEvents.UI_BUTTON_CLICK);
            attackTargeting = false;
            sendHint(C2SDuelHint.PET_SELECTED, selectedPetIdx); // still on same pet
            return true;
        }

        // ── Skill targeting: click target pet ─────────────────────────────────
        if (pendingSkillId != null) {
            int targetSide = targetingAlly ? mySide : oppSide;
            int startX     = targetingAlly ? (cx + 8) : 8;
            for (int i = 0; i < 3; i++) {
                int cardX = startX + i * (PET_CARD_W + PET_GAP);
                if (mx >= cardX && mx <= cardX + PET_CARD_W
                        && my >= cardY && my <= cardY + PET_CARD_H
                        && DuelClientState.hp(targetSide, i) > 0) {
                    playSound(SoundEvents.UI_BUTTON_CLICK);
                    PacketDistributor.sendToServer(
                            new C2SDuelAction(C2SDuelAction.SKILL, pendingSkillId, selectedPetIdx, i));
                    sendHint(C2SDuelHint.CLEARED, -1);
                    pendingSkillId  = null;
                    selectedPetIdx  = -1;
                    return true;
                }
            }
            // Wrong-side card clicked → ignore (stay in skill targeting mode)
            int wrongSkillX = targetingAlly ? 8 : (cx + 8);
            for (int i = 0; i < 3; i++) {
                int cardX = wrongSkillX + i * (PET_CARD_W + PET_GAP);
                if (mx >= cardX && mx <= cardX + PET_CARD_W
                        && my >= cardY && my <= cardY + PET_CARD_H) return true;
            }
            // Click elsewhere → cancel
            playSound(SoundEvents.UI_BUTTON_CLICK);
            pendingSkillId = null;
            sendHint(C2SDuelHint.PET_SELECTED, selectedPetIdx); // still on same pet
            return true;
        }

        // ── Defend targeting: click ally pet ─────────────────────────────────
        if (defendTargeting) {
            for (int i = 0; i < 3; i++) {
                int cardX = cx + 8 + i * (PET_CARD_W + PET_GAP);
                if (mx >= cardX && mx <= cardX + PET_CARD_W
                        && my >= cardY && my <= cardY + PET_CARD_H
                        && DuelClientState.hp(mySide, i) > 0) {
                    playSound(SoundEvents.UI_BUTTON_CLICK);
                    PacketDistributor.sendToServer(
                            new C2SDuelAction(C2SDuelAction.DEFEND, "", selectedPetIdx, i));
                    sendHint(C2SDuelHint.CLEARED, -1);
                    defendTargeting = false;
                    selectedPetIdx  = -1;
                    return true;
                }
            }
            // Opponent card clicked → ignore
            for (int i = 0; i < 3; i++) {
                int cardX = 8 + i * (PET_CARD_W + PET_GAP);
                if (mx >= cardX && mx <= cardX + PET_CARD_W
                        && my >= cardY && my <= cardY + PET_CARD_H) return true;
            }
            // Click elsewhere → cancel
            playSound(SoundEvents.UI_BUTTON_CLICK);
            defendTargeting = false;
            sendHint(C2SDuelHint.PET_SELECTED, selectedPetIdx);
            return true;
        }

        // ── My pet card: select / switch pending pet ──────────────────────────
        for (int i = 0; i < 3; i++) {
            int cardX = cx + 8 + i * (PET_CARD_W + PET_GAP);
            if (mx >= cardX && mx <= cardX + PET_CARD_W
                    && my >= cardY && my <= cardY + PET_CARD_H
                    && pending.contains(i)
                    && DuelClientState.hp(mySide, i) > 0
                    && i != selectedPetIdx) {
                playSoundPitched(SoundEvents.UI_BUTTON_CLICK, 1.3f);
                selectedPetIdx = i;
                sendHint(C2SDuelHint.PET_SELECTED, i);
                return true;
            }
        }

        // ── Action buttons (pet selected) ─────────────────────────────────────
        if (selectedPetIdx >= 0 && pending.contains(selectedPetIdx)) {
            PetData actor = rosters[mySide][selectedPetIdx];
            if (actor == null) return false;

            int actY  = computeActionAreaY() + 11;
            int btnY2 = actY + 11;
            int sp    = state.currentSP();

            // Back
            int backW = font.width("← Back") + 12;
            if (inBtnBounds(mx, my, 10, btnY2, backW)) {
                playSound(SoundEvents.UI_BUTTON_CLICK);
                selectedPetIdx = -1;
                sendHint(C2SDuelHint.CLEARED, -1);
                return true;
            }

            // Attack
            int attackX = 10 + backW + BTN_GAP;
            int attackW = font.width("⚔ Attack (FREE)") + 12;
            if (inBtnBounds(mx, my, attackX, btnY2, attackW)) {
                playSound(SoundEvents.UI_BUTTON_CLICK);
                attackTargeting = true;
                sendHint(C2SDuelHint.ATTACK_TARGETING, selectedPetIdx);
                return true;
            }

            // Defend
            int defendX = attackX + attackW + BTN_GAP;
            int defendW = font.width("🛡 Defend (FREE)") + 12;
            if (inBtnBounds(mx, my, defendX, btnY2, defendW)) {
                playSound(SoundEvents.UI_BUTTON_CLICK);
                defendTargeting = true;
                sendHint(C2SDuelHint.SKILL_TARGETING_A, selectedPetIdx);
                return true;
            }

            // Skip
            int skipX = defendX + defendW + BTN_GAP;
            int skipW = font.width("↩ Skip") + 12;
            if (inBtnBounds(mx, my, skipX, btnY2, skipW)) {
                playSound(SoundEvents.UI_BUTTON_CLICK);
                PacketDistributor.sendToServer(
                        new C2SDuelAction(C2SDuelAction.SKIP_PET, "", selectedPetIdx, 0));
                sendHint(C2SDuelHint.CLEARED, -1);
                selectedPetIdx = -1;
                return true;
            }

            // Skills
            int skillRowY = btnY2 + BTN_H + 4;
            int skillBtnX = 10;
            for (SkillInstance si : actor.skills()) {
                if (si.level() <= 0) continue;
                com.arcadia.pets.duel.DuelSkillDef def =
                        com.arcadia.pets.duel.DuelSkillAdapter.get(si.skill().getId());
                if (def == null) continue;
                int cd = DuelClientState.skillCooldown(mySide, selectedPetIdx, si.skill().getId());
                boolean ready = cd == 0 && sp >= def.spCost;
                String label  = si.skill().getDisplayName().getString();
                String badge  = def.spCost + "SP" + (cd > 0 ? " CD:" + cd : "");
                int bw = font.width(label + " (" + badge + ")") + 12;
                if (ready && inBtnBounds(mx, my, skillBtnX, skillRowY, bw)) {
                    playSound(SoundEvents.UI_BUTTON_CLICK);
                    handleSkillClick(def, si.skill().getId());
                    return true;
                }
                skillBtnX += bw + BTN_GAP;
                if (skillBtnX > width - 80) { skillBtnX = 10; skillRowY += BTN_H + 3; }
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 70) { // F = forfeit toggle
            S2CDuelState s = DuelClientState.get();
            if (s != null && s.phaseOrdinal() != com.arcadia.pets.duel.DuelPhase.FINISHED.ordinal()) {
                forfeitConfirm = !forfeitConfirm;
                return true;
            }
        }
        if (key == 256) { // ESC — step back through selection states
            if (forfeitConfirm)         { playSound(SoundEvents.UI_BUTTON_CLICK); forfeitConfirm = false; return true; }
            if (attackTargeting)        {
                playSound(SoundEvents.UI_BUTTON_CLICK);
                attackTargeting = false;
                sendHint(C2SDuelHint.PET_SELECTED, selectedPetIdx);
                return true;
            }
            if (defendTargeting) {
                playSound(SoundEvents.UI_BUTTON_CLICK);
                defendTargeting = false;
                sendHint(C2SDuelHint.PET_SELECTED, selectedPetIdx);
                return true;
            }
            if (pendingSkillId != null) {
                playSound(SoundEvents.UI_BUTTON_CLICK);
                pendingSkillId = null;
                sendHint(C2SDuelHint.PET_SELECTED, selectedPetIdx);
                return true;
            }
            if (selectedPetIdx >= 0)    {
                playSound(SoundEvents.UI_BUTTON_CLICK);
                selectedPetIdx = -1;
                sendHint(C2SDuelHint.CLEARED, -1);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        S2CDuelState s = DuelClientState.get();
        if (s == null) return false;
        int maxScroll = Math.max(0, s.combatLog().size() - LOG_LINES);
        if (dy < 0 && logScroll < maxScroll) logScroll++;
        if (dy > 0 && logScroll > 0) logScroll--;
        return true;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Plays a UI sound through the master sound channel (respects vanilla Master Volume bar). */
    private static void playSound(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.value(), 1.0f));
    }

    private static void playSound(net.minecraft.sounds.SoundEvent sound) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f));
    }

    private static void playSoundPitched(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.value(), pitch));
    }

    /** Sends a hint packet, skipping duplicates to avoid network spam. */
    private void sendHint(int type, int petIdx) {
        if (type == lastSentHintType && petIdx == lastSentHintPet) return;
        lastSentHintType = type;
        lastSentHintPet  = petIdx;
        PacketDistributor.sendToServer(new C2SDuelHint(type, petIdx));
    }

    private void handleSkillClick(com.arcadia.pets.duel.DuelSkillDef def, String skillId) {
        switch (def.targetType) {
            case SELF, ALL_ENEMIES, ALL_ALLIES, RANDOM_ENEMY -> {
                PacketDistributor.sendToServer(
                        new C2SDuelAction(C2SDuelAction.SKILL, skillId, selectedPetIdx, 0));
                sendHint(C2SDuelHint.CLEARED, -1);
                selectedPetIdx = -1;
            }
            case ALLY_SINGLE -> {
                pendingSkillId = skillId; targetingAlly = true;
                sendHint(C2SDuelHint.SKILL_TARGETING_A, selectedPetIdx);
            }
            case ENEMY_SINGLE -> {
                pendingSkillId = skillId; targetingAlly = false;
                sendHint(C2SDuelHint.SKILL_TARGETING_E, selectedPetIdx);
            }
        }
    }

    private boolean inBtnBounds(double mx, double my, int x, int y, int w) {
        return mx >= x && mx <= x + w && my >= y && my <= y + BTN_H;
    }

    private int computeActionAreaY() {
        int barY = 10 + PET_CARD_H + 6;
        int logY = barY + 12;
        return logY + LOG_LINES * LOG_LINE_H + 8;
    }

    private static String mobShortName(String mobType) {
        int colon = mobType.indexOf(':');
        String raw = colon >= 0 ? mobType.substring(colon + 1) : mobType;
        return raw.isEmpty() ? raw
                : Character.toUpperCase(raw.charAt(0)) + raw.substring(1).replace('_', ' ');
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
