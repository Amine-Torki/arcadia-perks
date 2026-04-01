package com.arcadia.pets.client;

import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetStat;
import com.arcadia.pets.network.C2SDuelAction;
import com.arcadia.pets.network.S2CDuelState;
import com.arcadia.pets.skill.PetSkill;
import com.arcadia.pets.skill.SkillInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Main combat screen for the Pet Duel system.
 *
 * <h3>Layout</h3>
 * <pre>
 * ┌────────── ENEMY TEAM ────────────────────────── YOUR TEAM ──────────────┐
 * │  [Pet 0] [Pet 1] [Pet 2]              [Pet 0] [Pet 1] [Pet 2]          │
 * │   HP▓▓▓   HP▓▓░   ✗ KO               HP▓▓▓   HP░░░   HP▓░░            │
 * │  [fx][fx]  [fx]                       [fx]    [fx][fx]                 │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │                       COMBAT LOG (scrollable)                           │
 * │  > Chicken used Levitate! +28% EVA for 2 turns.                        │
 * │  > Zombie Wolf attacks Pig! 3 damage.                                  │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │  ACTING: [Pet Name] ● ● ○  AP remaining                                │
 * │  [⚔ Attack 1AP] [🛡 Defend 1AP] [Pass Turn]                            │
 * │  [Skill1 Lv7 1AP|CD:0] [Skill2 Lv3 2AP|CD:2] ...                     │
 * │  TARGET: [← Enemy Pet0] [Enemy Pet1 →] (when selecting target)         │
 * └─────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>When it is NOT the local player's turn, the action area shows a
 * "Waiting for opponent…" spinner instead of buttons.</p>
 */
public class DuelScreen extends Screen {

    // ── Layout constants ──────────────────────────────────────────────────────

    private static final int PET_CARD_W  = 68;
    private static final int PET_CARD_H  = 58;
    private static final int PET_GAP     = 6;

    private static final int LOG_LINES   = 5;
    private static final int LOG_LINE_H  = 9;

    private static final int BTN_H       = 13;
    private static final int BTN_GAP     = 4;

    // Targeting state
    private String pendingSkillId   = null;  // null = not in targeting mode
    private boolean targetingAlly   = false; // true=ally target, false=enemy target
    private int     hoveredTarget   = -1;    // 0-2, which target pet is hovered

    // Log scroll offset (0 = bottom = most recent)
    private int logScroll = 0;

    public DuelScreen() {
        super(Component.literal("Pet Duel"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() {
        // Allow ESC only to forfeit (via a confirmation, TODO)
        return false;
    }

    // =========================================================================
    // Render
    // =========================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);

        S2CDuelState state = DuelClientState.get();
        if (state == null) return;

        Minecraft mc        = Minecraft.getInstance();
        UUID      localUuid = mc.player != null ? mc.player.getUUID() : null;
        if (localUuid == null) return;

        // Determine which side is "mine" (0 = p1, 1 = p2)
        int mySide  = localUuid.equals(state.p1()) ? 0 : 1;
        int oppSide = 1 - mySide;

        PetData[][] rosters = DuelClientState.getRosters();
        if (rosters == null) return;

        int cx = width / 2;

        // ── Team panels ────────────────────────────────────────────────────────
        int teamPanelY = 10;

        // Enemy team (left side of screen)
        renderTeamPanel(g, rosters[oppSide], oppSide, state, false,
                10, teamPanelY, mouseX, mouseY);

        // My team (right side)
        int myPanelX = cx + 10;
        renderTeamPanel(g, rosters[mySide], mySide, state, true,
                myPanelX, teamPanelY, mouseX, mouseY);

        // VS divider
        g.drawCenteredString(font, "§6VS", cx, teamPanelY + PET_CARD_H / 2 - 4, 0xFFD700);

        // ── Turn-order bar ──────────────────────────────────────────────────────
        int barY = teamPanelY + PET_CARD_H + 6;
        renderTurnOrderBar(g, state, mySide, cx, barY);

        // ── Combat log ────────────────────────────────────────────────────────
        int logY = barY + 14;
        renderCombatLog(g, state.combatLog(), cx, logY);

        // ── Action area ───────────────────────────────────────────────────────
        int actionY = logY + LOG_LINES * LOG_LINE_H + 8;
        boolean myTurn = localUuid.equals(state.actorUuid());
        if (myTurn) {
            renderActionArea(g, state, mySide, rosters[mySide], cx, actionY, mouseX, mouseY);
        } else {
            renderWaitingArea(g, state, cx, actionY);
        }

        // Forfeit hint
        g.drawString(font, "§8[F] Forfeit", 4, height - 10, 0x555555, false);

        super.render(g, mouseX, mouseY, partial);
    }

    // ── Team panel ────────────────────────────────────────────────────────────

    private void renderTeamPanel(GuiGraphics g, PetData[] roster, int side,
                                  S2CDuelState state, boolean isMine,
                                  int startX, int y, int mouseX, int mouseY) {
        for (int i = 0; i < 3; i++) {
            int cx = startX + i * (PET_CARD_W + PET_GAP);
            renderPetCard(g, roster[i], side, i, state, isMine, cx, y, mouseX, mouseY);
        }
    }

    private void renderPetCard(GuiGraphics g, PetData pd, int side, int petIdx,
                                S2CDuelState state, boolean isMine,
                                int x, int y, int mouseX, int mouseY) {
        int hp    = DuelClientState.hp(side, petIdx);
        int maxHp = DuelClientState.maxHp(side, petIdx);
        boolean alive = hp > 0;

        // Card background
        int bg = alive ? (isMine ? 0xCC1a2e1a : 0xCC2e1a1a) : 0xAA111111;
        g.fill(x, y, x + PET_CARD_W, y + PET_CARD_H, bg);

        // Border: gold if this is the acting pet, rarity colour otherwise
        boolean isActing = petIdx == state.actorPetIdx()
                && (isMine ? state.p1().equals(state.actorUuid())
                : state.p2().equals(state.actorUuid()));
        int borderCol = isActing ? 0xFFFFD700
                : (pd != null && pd.rarity().getColor().getColor() != null
                ? pd.rarity().getColor().getColor() | 0xFF000000 : 0xFF555555);
        g.fill(x - 1, y - 1, x + PET_CARD_W + 1, y + PET_CARD_H + 1, borderCol);
        g.fill(x, y, x + PET_CARD_W, y + PET_CARD_H, bg);

        if (pd == null) return;

        // Pet name
        String name = pd.customName() != null ? pd.customName() : mobShortName(pd.mobType());
        g.drawString(font, "§f" + truncate(name, 8), x + 2, y + 2, 0xFFFFFF, false);

        if (!alive) {
            g.drawCenteredString(font, "§4✗ KO", x + PET_CARD_W / 2, y + PET_CARD_H / 2 - 4, 0xFF4444);
            return;
        }

        // HP bar
        int barW  = PET_CARD_W - 4;
        int barH  = 5;
        int barX  = x + 2;
        int barY  = y + 12;
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
        float hpFrac = maxHp > 0 ? (float) hp / maxHp : 0;
        int fillW = (int)(barW * hpFrac);
        int hpColor = hpFrac > 0.5f ? 0xFF44CC44 : hpFrac > 0.25f ? 0xFFCCAA00 : 0xFFCC2222;
        g.fill(barX, barY, barX + fillW, barY + barH, hpColor);
        g.drawString(font, "§f" + hp + "/" + maxHp, barX, barY + barH + 1, 0xAAAAAA, false);

        // Status effect labels (compact, 2 per row)
        List<String> effects = DuelClientState.effectLabels(side, petIdx);
        int fxY = barY + barH + 10;
        for (int e = 0; e < Math.min(effects.size(), 4); e++) {
            g.drawString(font, "§8" + effects.get(e),
                    x + 2 + (e % 2) * (PET_CARD_W / 2), fxY + (e / 2) * 8, 0x888888, false);
        }

        // Targeting highlight (when in targeting mode)
        boolean isTargetable = pendingSkillId != null
                && (targetingAlly == isMine)
                && alive;
        if (isTargetable) {
            boolean hov = mouseX >= x && mouseX <= x + PET_CARD_W
                    && mouseY >= y && mouseY <= y + PET_CARD_H;
            if (hov) {
                g.fill(x, y, x + PET_CARD_W, y + PET_CARD_H, 0x55FFFF00);
                hoveredTarget = petIdx;
            }
            // Flashing border when targetable
            long t = System.currentTimeMillis();
            if ((t / 400) % 2 == 0)
                g.fill(x - 1, y - 1, x + PET_CARD_W + 1, y + PET_CARD_H + 1, 0xAAFFFF00);
        }
    }

    // ── Turn-order bar ────────────────────────────────────────────────────────

    private void renderTurnOrderBar(GuiGraphics g, S2CDuelState state, int mySide,
                                     int cx, int y) {
        List<long[]> order = state.turnOrderEncoded();
        if (order.isEmpty()) return;
        int slotW = 24, slotH = 10, gap = 3;
        int totalW = order.size() * (slotW + gap) - gap;
        int startX = cx - totalW / 2;
        for (int i = 0; i < order.size(); i++) {
            long[] e     = order.get(i);
            UUID owner   = new UUID(e[0], e[1]);
            int petIdx   = (int) e[2];
            boolean mine = (owner.equals(state.p1()) && mySide == 0)
                    || (owner.equals(state.p2()) && mySide == 1);
            boolean curr = i == state.turnOrderEncoded().indexOf(order.get(0)) && i == 0;
            int sx = startX + i * (slotW + gap);
            g.fill(sx, y, sx + slotW, y + slotH,
                    i == 0 ? 0xFFFFD700 : mine ? 0xFF1a3a1a : 0xFF3a1a1a);
            g.drawCenteredString(font, (mine ? "§a" : "§c") + "P" + (petIdx + 1),
                    sx + slotW / 2, y + 1, 0xFFFFFF);
        }
    }

    // ── Combat log ────────────────────────────────────────────────────────────

    private void renderCombatLog(GuiGraphics g, List<String> log, int cx, int y) {
        int logW = width - 20;
        int logX = 10;
        g.fill(logX, y - 1, logX + logW, y + LOG_LINES * LOG_LINE_H + 1, 0x88111111);

        int total   = log.size();
        int visible = Math.min(LOG_LINES, total);
        int start   = Math.max(0, total - LOG_LINES - logScroll);
        for (int i = 0; i < visible; i++) {
            int idx = start + i;
            if (idx >= total) break;
            g.drawString(font, "§8> §7" + log.get(idx),
                    logX + 2, y + i * LOG_LINE_H, 0xAAAAAA, false);
        }
    }

    // ── Action area ───────────────────────────────────────────────────────────

    private void renderActionArea(GuiGraphics g, S2CDuelState state, int mySide,
                                   PetData[] roster, int cx, int y, int mx, int my) {
        int actorPet = state.actorPetIdx();
        PetData actor = roster[actorPet];
        if (actor == null) return;

        // Acting pet info
        String actorName = actor.customName() != null
                ? actor.customName() : mobShortName(actor.mobType());
        g.drawString(font, "§eActing: §f" + actorName, 10, y, 0xFFFFDD, false);

        // AP indicators
        int ap = state.currentAP();
        StringBuilder apBar = new StringBuilder("§7AP: ");
        for (int i = 0; i < 3; i++) apBar.append(i < ap ? "§e●" : "§8○");
        g.drawString(font, apBar.toString(), 10, y + 10, 0xFFFFFF, false);

        int btnY = y + 22;
        int btnX = 10;

        if (pendingSkillId == null) {
            // ── Normal action buttons ─────────────────────────────────────────

            // Attack (1 AP)
            btnX = renderActionButton(g, "⚔ Attack", "1AP", btnX, btnY, ap >= 1, mx, my);
            btnX += BTN_GAP;

            // Defend (1 AP)
            btnX = renderActionButton(g, "🛡 Defend", "1AP", btnX, btnY, ap >= 1, mx, my);
            btnX += BTN_GAP;

            // Pass turn
            renderActionButton(g, "↩ Pass", "", btnX, btnY, true, mx, my);

            // Skills (second row)
            int skillRowY = btnY + BTN_H + 4;
            int skillBtnX = 10;
            for (SkillInstance si : actor.skills()) {
                if (si.level() <= 0) continue;
                int cd = DuelClientState.skillCooldown(mySide, actorPet, si.skill().getId());
                com.arcadia.pets.duel.DuelSkillDef def =
                        com.arcadia.pets.duel.DuelSkillAdapter.get(si.skill().getId());
                if (def == null) continue;
                boolean ready = cd == 0 && ap >= def.apCost;
                String label  = si.skill().getDisplayName().getString();
                String badge  = def.apCost + "AP" + (cd > 0 ? "|CD:" + cd : "");
                skillBtnX = renderActionButton(g, label, badge, skillBtnX, skillRowY, ready, mx, my);
                skillBtnX += BTN_GAP;
                if (skillBtnX > width - 80) { skillBtnX = 10; skillRowY += BTN_H + 3; }
            }

        } else {
            // ── Targeting mode ────────────────────────────────────────────────
            String targetLabel = targetingAlly ? "Select an ALLY pet:" : "Select an ENEMY pet:";
            g.drawCenteredString(font, "§e" + targetLabel + " §7(click pet card)", cx, btnY, 0xFFFFDD);
            // Cancel button
            renderActionButton(g, "✖ Cancel", "", 10, btnY + BTN_H + 4, true, mx, my);
        }
    }

    private int renderActionButton(GuiGraphics g, String label, String badge,
                                    int x, int y, boolean enabled, int mx, int my) {
        int textW  = font.width(label + (badge.isEmpty() ? "" : " " + badge));
        int btnW   = textW + 10;
        boolean hov = enabled && mx >= x && mx <= x + btnW && my >= y && my <= y + BTN_H;
        int bg = !enabled ? 0xAA2a2a2a : hov ? 0xFF4a4a6a : 0xFF333355;
        g.fill(x, y, x + btnW, y + BTN_H, bg);
        g.drawString(font, (enabled ? "§f" : "§8") + label
                + (badge.isEmpty() ? "" : " §7(" + badge + ")"),
                x + 4, y + 2, 0xFFFFFF, false);
        return x + btnW;
    }

    private void renderWaitingArea(GuiGraphics g, S2CDuelState state, int cx, int y) {
        long remaining = (state.actionDeadline() - System.currentTimeMillis()) / 1000;
        g.drawCenteredString(font, "§8Waiting for opponent... (" + Math.max(0, remaining) + "s)",
                cx, y + 10, 0x666666);
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
        if (!local.equals(state.actorUuid())) return false;

        int mySide  = local.equals(state.p1()) ? 0 : 1;
        int oppSide = 1 - mySide;
        int actorPet = state.actorPetIdx();

        PetData[][] rosters = DuelClientState.getRosters();
        if (rosters == null) return false;

        // ── If in targeting mode: check pet card clicks ───────────────────────
        if (pendingSkillId != null) {
            int targetSide = targetingAlly ? mySide : oppSide;
            int startX = targetingAlly ? (width / 2 + 10) : 10;
            int cardY  = 10;
            for (int i = 0; i < 3; i++) {
                int cardX = startX + i * (PET_CARD_W + PET_GAP);
                if (mx >= cardX && mx <= cardX + PET_CARD_W
                        && my >= cardY && my <= cardY + PET_CARD_H) {
                    if (DuelClientState.hp(targetSide, i) > 0) {
                        sendSkillAction(actorPet, pendingSkillId, i);
                        pendingSkillId = null;
                        return true;
                    }
                }
            }
            // Cancel targeting on right click or clicking the cancel button
            if (pendingSkillId != null) {
                // Check cancel button click (rendered at btnY + BTN_H + 4 in action area)
                pendingSkillId = null;
            }
            return true;
        }

        // ── Normal mode: check action buttons ─────────────────────────────────
        int btnY   = computeActionAreaY();
        int btnX   = 10;

        // Attack button
        int attackW = font.width("⚔ Attack 1AP") + 10;
        if (my >= btnY + 22 && my <= btnY + 22 + BTN_H && mx >= btnX && mx <= btnX + attackW) {
            int firstEnemy = firstAlive(oppSide);
            if (firstEnemy >= 0 && state.currentAP() >= 1) {
                sendAttack(actorPet, firstEnemy);
                return true;
            }
        }

        // Skill buttons (second row)
        int skillRowY = btnY + 22 + BTN_H + 4;
        int skillBtnX = 10;
        PetData actor = rosters[mySide][actorPet];
        if (actor != null) {
            for (SkillInstance si : actor.skills()) {
                if (si.level() <= 0) continue;
                com.arcadia.pets.duel.DuelSkillDef def =
                        com.arcadia.pets.duel.DuelSkillAdapter.get(si.skill().getId());
                if (def == null) continue;
                int cd = DuelClientState.skillCooldown(mySide, actorPet, si.skill().getId());
                boolean ready = cd == 0 && state.currentAP() >= def.apCost;
                String label = si.skill().getDisplayName().getString();
                String badge = def.apCost + "AP" + (cd > 0 ? "|CD:" + cd : "");
                int bw = font.width(label + " (" + badge + ")") + 10;
                if (ready && my >= skillRowY && my <= skillRowY + BTN_H
                        && mx >= skillBtnX && mx <= skillBtnX + bw) {
                    handleSkillClick(def, si.skill().getId(), actorPet);
                    return true;
                }
                skillBtnX += bw + BTN_GAP;
                if (skillBtnX > width - 80) { skillBtnX = 10; skillRowY += BTN_H + 3; }
            }
        }

        // Defend button
        int defendX = 10 + attackW + BTN_GAP;
        int defendW = font.width("🛡 Defend 1AP") + 10;
        if (my >= btnY + 22 && my <= btnY + 22 + BTN_H
                && mx >= defendX && mx <= defendX + defendW && state.currentAP() >= 1) {
            PacketDistributor.sendToServer(new C2SDuelAction(C2SDuelAction.DEFEND, "", 0));
            return true;
        }

        // Pass button
        int passX = defendX + defendW + BTN_GAP;
        int passW = font.width("↩ Pass") + 10;
        if (my >= btnY + 22 && my <= btnY + 22 + BTN_H
                && mx >= passX && mx <= passX + passW) {
            PacketDistributor.sendToServer(new C2SDuelAction(C2SDuelAction.PASS, "", 0));
            return true;
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // F = forfeit
        if (key == 70) {
            PacketDistributor.sendToServer(new C2SDuelAction(C2SDuelAction.FORFEIT, "", 0));
            return true;
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

    private void handleSkillClick(com.arcadia.pets.duel.DuelSkillDef def,
                                   String skillId, int actorPet) {
        switch (def.targetType()) {
            case SELF, ALL_ENEMIES, ALL_ALLIES, RANDOM_ENEMY ->
                    sendSkillAction(actorPet, skillId, 0);
            case ALLY_SINGLE -> {
                pendingSkillId = skillId;
                targetingAlly = true;
            }
            case ENEMY_SINGLE -> {
                pendingSkillId = skillId;
                targetingAlly = false;
            }
        }
    }

    private void sendAttack(int actorPet, int targetIdx) {
        PacketDistributor.sendToServer(
                new C2SDuelAction(C2SDuelAction.ATTACK, "", targetIdx));
    }

    private void sendSkillAction(int actorPet, String skillId, int targetIdx) {
        PacketDistributor.sendToServer(
                new C2SDuelAction(C2SDuelAction.SKILL, skillId, targetIdx));
    }

    private int firstAlive(int side) {
        for (int i = 0; i < 3; i++) {
            if (DuelClientState.hp(side, i) > 0) return i;
        }
        return -1;
    }

    private int computeActionAreaY() {
        int teamPanelY  = 10;
        int barY        = teamPanelY + PET_CARD_H + 6;
        int logY        = barY + 14;
        return logY + LOG_LINES * LOG_LINE_H + 8;
    }

    private static String mobShortName(String mobType) {
        int colon = mobType.indexOf(':');
        String raw = colon >= 0 ? mobType.substring(colon + 1) : mobType;
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1).replace('_', ' ');
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
