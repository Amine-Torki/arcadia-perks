package com.arcadia.pets.duel;

import com.arcadia.pets.item.DerivedPetStats;
import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetStat;
import com.arcadia.pets.skill.SkillInstance;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Full mutable state of a running duel.
 *
 * <h3>Turn model (player-based)</h3>
 * <ul>
 *   <li>Players alternate full turns: p1 acts with ALL alive pets, then p2, then p1, …</li>
 *   <li>{@link #currentTurnPlayer} is whose turn it is.</li>
 *   <li>{@link #pendingPetActions} holds the indices (0–2) of pets that still need to act this turn.</li>
 *   <li>A player calls {@link #endPetAction(int)} after each pet acts; when the set empties,
 *       {@link #endPlayerTurn()} fires automatically.</li>
 * </ul>
 *
 * <h3>Spirit Points (SP)</h3>
 * Each player's team shares a single SP pool. At the start of your turn you gain
 * {@link #SP_PER_TURN} SP (carried over from last turn, capped at {@link #SP_MAX}).
 * Basic attack and defend are free; skills cost 1–4 SP.
 *
 * <h3>Guard (Defend action)</h3>
 * Defending applies {@link DuelStatusType#GUARD} to that pet.
 * During the opponent's turn, each incoming attack checks all alive allies of the
 * target for a GUARD token — the first one that passes a % roll intercepts the hit
 * at −40 % damage. Remaining GUARD tokens are cleared at the end of the attacking
 * player's turn.
 */
public final class DuelSession {

    // ── Constants ─────────────────────────────────────────────────────────────

    public static final int SP_MAX          = 5;
    public static final int SP_PER_TURN     = 1;
    public static final int TURN_TIMEOUT_MS = 45_000; // 45 s for a full team turn
    private static final int MAX_LOG        = 24;

    // ── Identity ─────────────────────────────────────────────────────────────

    public final UUID duelId = UUID.randomUUID();
    public final UUID p1;   // challenger
    public final UUID p2;   // challengee

    // ── Rosters ──────────────────────────────────────────────────────────────

    public final PetData[] p1Roster        = new PetData[3];
    public final PetData[] p2Roster        = new PetData[3];
    public final int[]     p1Hp            = new int[3];
    public final int[]     p2Hp            = new int[3];
    public final int[]     p1MaxHp         = new int[3];
    public final int[]     p2MaxHp         = new int[3];
    public final boolean[] p1SecondLifeUsed = new boolean[3];
    public final boolean[] p2SecondLifeUsed = new boolean[3];

    // ── Status effects & cooldowns ────────────────────────────────────────────

    private final Map<String, List<ActiveEffect>> effects       = new HashMap<>();
    private final Map<String, Integer>            skillCooldowns = new HashMap<>();

    // ── Player-based turn state ───────────────────────────────────────────────

    /** UUID of the player whose turn it currently is. */
    public UUID currentTurnPlayer;

    /**
     * Indices (0–2) of the acting player's pets that have not yet submitted an
     * action this turn. Populated by {@link #openPlayerTurn(UUID)}, drained by
     * {@link #endPetAction(int)}.
     */
    public final LinkedHashSet<Integer> pendingPetActions = new LinkedHashSet<>();

    // ── Spirit Points ─────────────────────────────────────────────────────────

    /** SP available for the current acting team this turn. Shared across all 3 pets. */
    public int currentSP = 0;

    private int p1StoredSP = 0; // SP that carries over to p1's next turn
    private int p2StoredSP = 0; // SP that carries over to p2's next turn

    // ── Phase ─────────────────────────────────────────────────────────────────

    public DuelPhase phase        = DuelPhase.ROSTER_PICK;
    public boolean   p1Confirmed  = false;
    public boolean   p2Confirmed  = false;

    // ── Combat log ───────────────────────────────────────────────────────────

    private final ArrayDeque<String> combatLog = new ArrayDeque<>();

    // ── Timing ───────────────────────────────────────────────────────────────

    public long actionDeadline = 0L;
    public long botActAt       = 0L;

    // ── Result ────────────────────────────────────────────────────────────────

    public UUID winner     = null;
    public int  roundNumber = 0;

    // ── Bot metadata ──────────────────────────────────────────────────────────

    public BotDifficulty botDifficulty = null;

    // ── Constructor ──────────────────────────────────────────────────────────

    public DuelSession(UUID challenger, UUID challengee) {
        this.p1 = challenger;
        this.p2 = challengee;
    }

    // =========================================================================
    // Roster helpers
    // =========================================================================

    public UUID     opponentOf(UUID player)         { return player.equals(p1) ? p2  : p1; }
    public PetData[] rosterFor(UUID player)         { return player.equals(p1) ? p1Roster : p2Roster; }
    public int[]    hpFor(UUID player)              { return player.equals(p1) ? p1Hp     : p2Hp; }
    public int[]    maxHpFor(UUID player)           { return player.equals(p1) ? p1MaxHp  : p2MaxHp; }
    public boolean[] secondLifeUsedFor(UUID pl)     { return pl.equals(p1) ? p1SecondLifeUsed : p2SecondLifeUsed; }

    public boolean isDefeated(UUID player) {
        int[] hp = hpFor(player);
        return hp[0] <= 0 && hp[1] <= 0 && hp[2] <= 0;
    }

    public boolean isAlive(UUID player, int petIdx) {
        return hpFor(player)[petIdx] > 0;
    }

    public int firstAlivePet(UUID player) {
        int[] hp = hpFor(player);
        for (int i = 0; i < 3; i++) if (hp[i] > 0) return i;
        return -1;
    }

    // =========================================================================
    // Combat initialisation
    // =========================================================================

    public void startCombat() {
        for (int side = 0; side < 2; side++) {
            UUID player = side == 0 ? p1 : p2;
            PetData[] roster = rosterFor(player);
            int[] hp    = hpFor(player);
            int[] maxHp = maxHpFor(player);
            for (int i = 0; i < 3; i++) {
                if (roster[i] == null) { maxHp[i] = 1; hp[i] = 0; continue; }
                DerivedPetStats ds = new DerivedPetStats(roster[i]);
                maxHp[i] = Math.max(ds.hp, 10);
                hp[i]    = maxHp[i];
            }
        }
        phase = DuelPhase.ACTIVE;
        openPlayerTurn(p1); // p1 (challenger) goes first
    }

    // =========================================================================
    // Turn management
    // =========================================================================

    /**
     * Opens a new turn for the given player. Populates {@link #pendingPetActions}
     * with all alive, non-stunned pets. Stunned pets have their stun consumed and
     * are logged as skipped. If no pets can act, skips immediately to the opponent.
     */
    public void openPlayerTurn(UUID player) {
        currentTurnPlayer = player;
        pendingPetActions.clear();

        for (int i = 0; i < 3; i++) {
            if (!isAlive(player, i)) continue;
            List<ActiveEffect> fx = effectsFor(player, i);
            ActiveEffect stun = fx.stream()
                    .filter(e -> e.type == DuelStatusType.STUN)
                    .findFirst().orElse(null);
            if (stun != null) {
                fx.remove(stun);
                addLog(petName(player, i) + " is stunned and cannot act this turn!");
            } else {
                pendingPetActions.add(i);
            }
        }

        // SP: carry-over + 1 per turn
        int stored = player.equals(p1) ? p1StoredSP : p2StoredSP;
        currentSP = Math.min(SP_MAX, stored + SP_PER_TURN);

        actionDeadline = System.currentTimeMillis() + TURN_TIMEOUT_MS;
        botActAt = 0L;

        if (pendingPetActions.isEmpty()) {
            // All alive pets were stunned — skip silently (stun already consumed)
            addLog((player.equals(p1) ? "Team 1" : "Team 2") + " has no pets that can act!");
            // Save SP (nothing spent), switch without full effect-tick
            saveSP(player);
            UUID next = opponentOf(player);
            if (player.equals(p2)) roundNumber++;
            actionDeadline = 0L;
            openPlayerTurn(next);
        }
    }

    /**
     * Called by DuelManager after a pet finishes its action.
     * Removes {@code petIdx} from {@link #pendingPetActions}; when the set is empty,
     * ends the player's turn automatically.
     */
    public void endPetAction(int petIdx) {
        pendingPetActions.remove(petIdx);
        if (pendingPetActions.isEmpty()) {
            endPlayerTurn();
        }
    }

    public void endPlayerTurn() {
        saveSP(currentTurnPlayer);

        // Tick DOT and duration effects for the acting player's pets
        for (int i = 0; i < 3; i++) {
            tickDotEffects(currentTurnPlayer, i);
            tickDurationEffects(currentTurnPlayer, i);
        }

        // Tick all skill cooldowns once per player turn
        tickSkillCooldowns();

        // Clear GUARD tokens from the OPPONENT's team — their guard window just ended
        // (this player just finished attacking, so the opponent's guards expire)
        UUID opponent = opponentOf(currentTurnPlayer);
        for (int i = 0; i < 3; i++) {
            effectsFor(opponent, i).removeIf(e -> e.type == DuelStatusType.GUARD);
        }

        // Round increments after p2 completes their turn (full p1+p2 cycle = 1 round)
        if (currentTurnPlayer.equals(p2)) roundNumber++;

        openPlayerTurn(opponent);
    }

    private void saveSP(UUID player) {
        if (player.equals(p1)) p1StoredSP = currentSP;
        else                   p2StoredSP = currentSP;
    }

    // =========================================================================
    // Skill cooldowns
    // =========================================================================

    public String cdKey(UUID player, int petIdx, String skillId) {
        return petKey(player, petIdx) + "_" + skillId;
    }

    public int getSkillCooldown(UUID player, int petIdx, String skillId) {
        return skillCooldowns.getOrDefault(cdKey(player, petIdx, skillId), 0);
    }

    public void setSkillCooldown(UUID player, int petIdx, String skillId, int turns) {
        if (turns > 0) skillCooldowns.put(cdKey(player, petIdx, skillId), turns);
    }

    private void tickSkillCooldowns() {
        skillCooldowns.replaceAll((k, v) -> v - 1);
        skillCooldowns.entrySet().removeIf(e -> e.getValue() <= 0);
    }

    // =========================================================================
    // Status effects
    // =========================================================================

    public String petKey(UUID player, int petIdx) {
        return (player.equals(p1) ? "1" : "2") + "_" + petIdx;
    }

    public List<ActiveEffect> effectsFor(UUID player, int petIdx) {
        return effects.computeIfAbsent(petKey(player, petIdx), k -> new ArrayList<>());
    }

    public void applyEffect(UUID player, int petIdx, ActiveEffect effect) {
        List<ActiveEffect> fx = effectsFor(player, petIdx);
        if (effect.type == DuelStatusType.POISON || effect.type == DuelStatusType.BURN) {
            fx.add(effect);
        } else {
            fx.removeIf(e -> e.type == effect.type);
            fx.add(effect);
        }
        if (effect.type == DuelStatusType.WITHER) {
            int[] maxHp = maxHpFor(player);
            int[] hp    = hpFor(player);
            int reduction = Math.max(1, (int) effect.magnitude);
            maxHp[petIdx] = Math.max(1, maxHp[petIdx] - reduction);
            if (hp[petIdx] > maxHp[petIdx]) hp[petIdx] = maxHp[petIdx];
        }
    }

    private void tickDotEffects(UUID player, int petIdx) {
        List<ActiveEffect> fx = effectsFor(player, petIdx);
        List<ActiveEffect> toRemove = new ArrayList<>();
        for (ActiveEffect e : fx) {
            if (e.type == DuelStatusType.POISON || e.type == DuelStatusType.BURN) {
                int dmg = Math.max(1, (int) e.magnitude);
                applyRawDamage(player, petIdx, dmg);
                addLog(petName(player, petIdx) + " takes " + dmg
                        + (e.type == DuelStatusType.POISON ? " poison" : " burn") + " damage!");
                e.magnitude = Math.max(1, e.magnitude - 1); // fading DoT: -1 per tick
                e.remainingTurns--;
                if (e.remainingTurns <= 0) toRemove.add(e);
            }
        }
        fx.removeAll(toRemove);
    }

    private void tickDurationEffects(UUID player, int petIdx) {
        List<ActiveEffect> fx = effectsFor(player, petIdx);
        fx.removeIf(e -> {
            if (e.remainingTurns > 0
                    && e.type != DuelStatusType.POISON
                    && e.type != DuelStatusType.BURN
                    && e.type != DuelStatusType.GUARD) { // GUARD managed separately
                e.remainingTurns--;
                return e.remainingTurns <= 0;
            }
            return false;
        });
    }

    // =========================================================================
    // Damage resolution
    // =========================================================================

    /**
     * Full damage pipeline with GUARD intercept support.
     * Order: FATIGUE → FOCUSED → CRIT → GUARD intercept → PHASED → EVA → REFLECT → SHIELD → FORTIFY.
     *
     * @return actual HP removed (0 on dodge/GUARD intercept of original target)
     */
    public int resolveDamage(UUID attacker, int attackerPet,
                             UUID target, int targetPet,
                             int baseDamage) {
        DerivedPetStats targetStats = new DerivedPetStats(rosterFor(target)[targetPet]);
        List<ActiveEffect> atkFx = effectsFor(attacker, attackerPet);
        List<ActiveEffect> tgtFx = effectsFor(target, targetPet);

        float damage = baseDamage;

        // FATIGUE
        for (ActiveEffect e : atkFx) {
            if (e.type == DuelStatusType.FATIGUE) { damage *= (1.0f - e.magnitude); break; }
        }

        // FOCUSED (consumed)
        ActiveEffect focused = atkFx.stream().filter(e -> e.type == DuelStatusType.FOCUSED).findFirst().orElse(null);
        if (focused != null) { damage *= focused.magnitude; atkFx.remove(focused); }

        // CRIT
        DerivedPetStats atkStats = new DerivedPetStats(rosterFor(attacker)[attackerPet]);
        int critPct = atkStats.critPct;
        ActiveEffect lucky = atkFx.stream().filter(e -> e.type == DuelStatusType.LUCKY_BONUS).findFirst().orElse(null);
        if (lucky != null) { critPct += (int) lucky.magnitude; atkFx.remove(lucky); }
        if (ThreadLocalRandom.current().nextInt(100) < critPct) {
            damage *= 1.5f;
            addLog("Critical hit!");
        }

        // ── GUARD intercept: any alive ally of the target (not the target itself) ──
        for (int guardPet = 0; guardPet < 3; guardPet++) {
            if (guardPet == targetPet) continue;
            if (!isAlive(target, guardPet)) continue;
            List<ActiveEffect> guardFx = effectsFor(target, guardPet);
            ActiveEffect guard = guardFx.stream()
                    .filter(e -> e.type == DuelStatusType.GUARD)
                    .findFirst().orElse(null);
            if (guard != null && ThreadLocalRandom.current().nextInt(100) < (int) guard.magnitude) {
                guardFx.remove(guard);
                int interceptDmg = Math.max(0, (int)(damage * 0.6f));
                addLog("🛡 " + petName(target, guardPet) + " intercepts for "
                        + petName(target, targetPet) + "! (" + interceptDmg + " dmg, -40%)");
                applyRawDamage(target, guardPet, interceptDmg);
                return 0; // original target takes no damage
            }
        }

        // PHASED (consumed, full immunity)
        ActiveEffect phased = tgtFx.stream().filter(e -> e.type == DuelStatusType.PHASED).findFirst().orElse(null);
        if (phased != null) {
            tgtFx.remove(phased);
            addLog(petName(target, targetPet) + " phased through the attack!");
            return 0;
        }

        // EVA dodge
        int evaPct = targetStats.evaPct;
        for (ActiveEffect e : tgtFx) {
            if (e.type == DuelStatusType.EVA_BOOST) { evaPct += (int) e.magnitude; break; }
        }
        if (ThreadLocalRandom.current().nextInt(100) < evaPct) {
            addLog(petName(target, targetPet) + " dodged the attack!");
            return 0;
        }

        // REFLECT (consumed)
        ActiveEffect reflect = tgtFx.stream().filter(e -> e.type == DuelStatusType.REFLECT).findFirst().orElse(null);
        if (reflect != null) {
            tgtFx.remove(reflect);
            int reflectDmg = Math.max(1, (int)(damage * reflect.magnitude));
            applyRawDamage(attacker, attackerPet, reflectDmg);
            addLog(petName(attacker, attackerPet) + " takes " + reflectDmg + " reflected damage!");
        }

        // SHIELD (consumed)
        ActiveEffect shield = tgtFx.stream().filter(e -> e.type == DuelStatusType.SHIELD).findFirst().orElse(null);
        if (shield != null) { tgtFx.remove(shield); damage *= (1.0f - shield.magnitude); }

        // FORTIFY (persistent)
        for (ActiveEffect e : tgtFx) {
            if (e.type == DuelStatusType.FORTIFY) { damage *= (1.0f - e.magnitude); break; }
        }

        int finalDmg = Math.max(0, (int) damage);
        if (finalDmg > 0) applyRawDamage(target, targetPet, finalDmg);
        return finalDmg;
    }

    public void applyRawDamage(UUID player, int petIdx, int damage) {
        int[] hp    = hpFor(player);
        int[] maxHp = maxHpFor(player);
        hp[petIdx] = Math.max(0, hp[petIdx] - damage);
        if (hp[petIdx] == 0 && hasSecondLifeSkill(player, petIdx)) {
            boolean[] used = secondLifeUsedFor(player);
            if (!used[petIdx]) {
                used[petIdx] = true;
                hp[petIdx] = Math.min(2, maxHp[petIdx]);
                addLog("★ " + petName(player, petIdx) + "'s Second Life activates! Revived at 2 HP!");
            }
        }
    }

    public void addStoredSP(UUID player, int petIdx, int amount) {
        if (player.equals(p1)) p1StoredSP = Math.min(SP_MAX, p1StoredSP + amount);
        else                   p2StoredSP = Math.min(SP_MAX, p2StoredSP + amount);
    }

    public void heal(UUID player, int petIdx, int amount) {
        int[] hp    = hpFor(player);
        int[] maxHp = maxHpFor(player);
        hp[petIdx] = Math.min(maxHp[petIdx], hp[petIdx] + amount);
    }

    // =========================================================================
    // Win condition
    // =========================================================================

    public boolean checkWinCondition() {
        if (isDefeated(p2)) { winner = p1; phase = DuelPhase.FINISHED; return true; }
        if (isDefeated(p1)) { winner = p2; phase = DuelPhase.FINISHED; return true; }
        return false;
    }

    // =========================================================================
    // Combat log
    // =========================================================================

    public void addLog(String line) {
        combatLog.addLast(line);
        while (combatLog.size() > MAX_LOG) combatLog.removeFirst();
    }

    public List<String> getLog() { return List.copyOf(combatLog); }

    // =========================================================================
    // Utility
    // =========================================================================

    public String petName(UUID player, int petIdx) {
        PetData[] roster = rosterFor(player);
        if (petIdx < 0 || petIdx >= 3 || roster[petIdx] == null) return "???";
        PetData pet = roster[petIdx];
        if (pet.customName() != null && !pet.customName().isEmpty()) return pet.customName();
        String mob = pet.mobType();
        int colon = mob.indexOf(':');
        if (colon >= 0) mob = mob.substring(colon + 1);
        return Character.toUpperCase(mob.charAt(0)) + mob.substring(1).replace('_', ' ');
    }

    private boolean hasSecondLifeSkill(UUID player, int petIdx) {
        PetData pet = rosterFor(player)[petIdx];
        if (pet == null) return false;
        for (SkillInstance si : pet.skills()) {
            if ("second_life".equals(si.skill().getId()) && si.level() > 0) return true;
        }
        return false;
    }

    public int getAtk(UUID player, int petIdx) {
        return new DerivedPetStats(rosterFor(player)[petIdx]).atk;
    }

    public Map<String, List<ActiveEffect>> getAllEffects() { return Collections.unmodifiableMap(effects); }
    public Map<String, Integer> getAllCooldowns()          { return Collections.unmodifiableMap(skillCooldowns); }

    // ── Legacy stubs (kept so S2CDuelState.from() compiles with minimal changes) ──
    /** @deprecated turn order is now player-based; returns empty list */
    @Deprecated public List<TurnSlot> getTurnOrder()  { return Collections.emptyList(); }
    /** @deprecated always returns 0 */
    @Deprecated public int getTurnOrderIndex()        { return 0; }
    /** @deprecated use {@link #currentTurnPlayer} and {@link #pendingPetActions} */
    @Deprecated public TurnSlot currentSlot()         { return null; }
}
