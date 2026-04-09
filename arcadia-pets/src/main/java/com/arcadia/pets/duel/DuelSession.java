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
 * <h3>Team layout</h3>
 * <ul>
 *   <li>p1 = challenger, p2 = challengee</li>
 *   <li>Each player has a roster of 3 pets (indices 0–2)</li>
 *   <li>All 3 pets are "on-field" simultaneously (Final Fantasy / Dragon Quest style)</li>
 * </ul>
 *
 * <h3>Win condition</h3>
 * A player loses when ALL 3 of their pets reach 0 HP.
 *
 * <h3>Turn order</h3>
 * Sorted by AGI (highest first) across all 6 living pets. Rebuilt at the start of
 * each new round. Dead pets are skipped; stunned pets lose their turn.
 *
 * <h3>Spirit Points (SP)</h3>
 * Each pet starts combat with 0 SP and gains {@link #SP_PER_TURN} SP when their turn opens,
 * capped at {@link #SP_MAX}. Unused SP carries over to the next round. The basic attack is
 * always free; skills cost 1–4 SP. Some skills can restore SP directly.
 */
public final class DuelSession {

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Maximum Spirit Points a pet can hold. */
    public static final int SP_MAX        = 5;
    /** SP gained at the start of each turn. */
    public static final int SP_PER_TURN   = 1;
    public static final int TURN_TIMEOUT_MS = 30_000; // 30 seconds
    private static final int MAX_LOG       = 24;

    // ── Identity ─────────────────────────────────────────────────────────────

    public final UUID duelId = UUID.randomUUID();
    /** Challenger (p1). */
    public final UUID p1;
    /** Challengee (p2). */
    public final UUID p2;

    // ── Rosters ──────────────────────────────────────────────────────────────

    public final PetData[] p1Roster    = new PetData[3];
    public final PetData[] p2Roster    = new PetData[3];
    public final int[]     p1Hp        = new int[3];
    public final int[]     p2Hp        = new int[3];
    public final int[]     p1MaxHp     = new int[3];
    public final int[]     p2MaxHp     = new int[3];
    /** Tracks whether each pet's Second Life passive has already triggered. */
    public final boolean[] p1SecondLifeUsed = new boolean[3];
    public final boolean[] p2SecondLifeUsed = new boolean[3];

    // ── Status effects ────────────────────────────────────────────────────────
    // Key format: "1_0" = p1's pet 0, "2_2" = p2's pet 2.

    private final Map<String, List<ActiveEffect>> effects = new HashMap<>();

    // ── Skill cooldowns ───────────────────────────────────────────────────────
    // Key format: "1_0_featherfall" → remaining turns.

    private final Map<String, Integer> skillCooldowns = new HashMap<>();

    // ── Turn order ────────────────────────────────────────────────────────────

    private final List<TurnSlot> turnOrder = new ArrayList<>();
    private int turnOrderIndex = 0;

    // ── Spirit Points ─────────────────────────────────────────────────────────
    // Persisted per-pet across turns so unused SP carries over.

    /** Per-pet stored SP between turns; key = petKey (e.g. "1_0"). */
    private final Map<String, Integer> petSP = new HashMap<>();
    /** SP currently available for the acting pet this turn. */
    public int currentSP = 0;

    // ── Phase ─────────────────────────────────────────────────────────────────

    public DuelPhase phase = DuelPhase.ROSTER_PICK;
    public boolean p1Confirmed = false;
    public boolean p2Confirmed = false;

    // ── Combat log ───────────────────────────────────────────────────────────

    private final ArrayDeque<String> combatLog = new ArrayDeque<>();

    // ── Timing ───────────────────────────────────────────────────────────────

    /** Epoch-ms deadline for the current turn. 0 = not started yet. */
    public long actionDeadline = 0L;

    // ── Result ────────────────────────────────────────────────────────────────

    /** Null until the duel finishes. */
    public UUID winner = null;

    /** Incremented each time a full round (all living pets have acted once) completes. */
    public int roundNumber = 0;

    // ── Constructor ──────────────────────────────────────────────────────────

    public DuelSession(UUID challenger, UUID challengee) {
        this.p1 = challenger;
        this.p2 = challengee;
    }

    // =========================================================================
    // Roster helpers
    // =========================================================================

    public UUID opponentOf(UUID player)          { return player.equals(p1) ? p2  : p1; }
    public PetData[] rosterFor(UUID player)      { return player.equals(p1) ? p1Roster : p2Roster; }
    public int[]     hpFor(UUID player)          { return player.equals(p1) ? p1Hp     : p2Hp; }
    public int[]     maxHpFor(UUID player)       { return player.equals(p1) ? p1MaxHp  : p2MaxHp; }
    public boolean[] secondLifeUsedFor(UUID pl)  { return pl.equals(p1) ? p1SecondLifeUsed : p2SecondLifeUsed; }

    /** True when all 3 pets of this player are at 0 HP. */
    public boolean isDefeated(UUID player) {
        int[] hp = hpFor(player);
        return hp[0] <= 0 && hp[1] <= 0 && hp[2] <= 0;
    }

    public boolean isAlive(UUID player, int petIdx) {
        return hpFor(player)[petIdx] > 0;
    }

    /** Returns the index of the first living pet for this player, or -1 if all dead. */
    public int firstAlivePet(UUID player) {
        int[] hp = hpFor(player);
        for (int i = 0; i < 3; i++) if (hp[i] > 0) return i;
        return -1;
    }

    // =========================================================================
    // Turn-order management
    // =========================================================================

    /**
     * Initialises combat after both rosters are confirmed.
     * Sets HP from {@link DerivedPetStats}, applies Second Life markers,
     * builds the first turn order, and opens the first turn.
     */
    public void startCombat() {
        for (int side = 0; side < 2; side++) {
            UUID player = side == 0 ? p1 : p2;
            PetData[] roster = rosterFor(player);
            int[] hp = hpFor(player);
            int[] maxHp = maxHpFor(player);
            for (int i = 0; i < 3; i++) {
                if (roster[i] == null) { maxHp[i] = 1; hp[i] = 0; continue; }
                DerivedPetStats ds = new DerivedPetStats(roster[i]);
                maxHp[i] = Math.max(ds.hp, 10);
                hp[i]    = maxHp[i];
            }
        }
        phase = DuelPhase.ACTIVE;
        rebuildTurnOrder();
        openTurn();
    }

    /**
     * Rebuilds the turn order from all currently living pets, sorted by AGI desc.
     * Ties are broken by p1 going before p2.
     */
    public void rebuildTurnOrder() {
        turnOrder.clear();
        for (int side = 0; side < 2; side++) {
            UUID player = side == 0 ? p1 : p2;
            PetData[] roster = rosterFor(player);
            for (int i = 0; i < 3; i++) {
                if (roster[i] != null && isAlive(player, i)) {
                    int agi = roster[i].stats().getOrDefault(PetStat.AGILITY, 1);
                    turnOrder.add(new TurnSlot(player, i, agi));
                }
            }
        }
        turnOrder.sort((a, b) -> {
            if (b.agility() != a.agility()) return b.agility() - a.agility();
            return a.ownerUuid().equals(p1) ? -1 : 1; // p1 goes first on ties
        });
        turnOrderIndex = 0;
    }

    /** Returns the slot that is currently acting. */
    public TurnSlot currentSlot() {
        if (turnOrder.isEmpty()) return null;
        return turnOrder.get(turnOrderIndex % turnOrder.size());
    }

    /**
     * Opens a new turn for the current slot pet: sets AP and deadline.
     * Automatically skips stunned or dead pets and loops to the next one.
     */
    public void openTurn() {
        while (true) {
            if (turnOrder.isEmpty()) return;
            TurnSlot slot = currentSlot();

            // Dead pet — advance without doing anything
            if (!isAlive(slot.ownerUuid(), slot.petIndex())) {
                advanceToNextSlot();
                continue;
            }

            // Stunned pet — consume stun, skip turn, advance
            List<ActiveEffect> fx = effectsFor(slot.ownerUuid(), slot.petIndex());
            ActiveEffect stun = fx.stream()
                    .filter(e -> e.type == DuelStatusType.STUN)
                    .findFirst().orElse(null);
            if (stun != null) {
                fx.remove(stun);
                addLog(petName(slot.ownerUuid(), slot.petIndex()) + " is stunned and loses their turn!");
                advanceToNextSlot();
                continue;
            }

            // Valid turn: restore SP (+SP_PER_TURN, carrying over unspent SP)
            String spKey = petKey(slot.ownerUuid(), slot.petIndex());
            int stored   = petSP.getOrDefault(spKey, 0);
            currentSP    = Math.min(SP_MAX, stored + SP_PER_TURN);
            actionDeadline = System.currentTimeMillis() + TURN_TIMEOUT_MS;
            return;
        }
    }

    /**
     * Called when the current pet finishes spending AP or the owner passes.
     * Ticks DOTs, decrements cooldowns, advances the turn pointer, and opens the next turn.
     */
    public void endTurn() {
        TurnSlot slot = currentSlot();
        if (slot != null) {
            // Persist remaining SP for this pet before moving on
            petSP.put(petKey(slot.ownerUuid(), slot.petIndex()), currentSP);
            tickDotEffects(slot.ownerUuid(), slot.petIndex());
            tickDurationEffects(slot.ownerUuid(), slot.petIndex());
        }
        tickSkillCooldowns();
        advanceToNextSlot();
        openTurn();
    }

    private void advanceToNextSlot() {
        turnOrderIndex++;
        if (turnOrderIndex >= turnOrder.size()) {
            roundNumber++; // completed one full rotation of all living pets
            rebuildTurnOrder();
        }
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

    /**
     * Applies an effect to a pet. DOTs (POISON, BURN) stack; all other types
     * replace an existing instance of the same type.
     */
    public void applyEffect(UUID player, int petIdx, ActiveEffect effect) {
        List<ActiveEffect> fx = effectsFor(player, petIdx);
        if (effect.type == DuelStatusType.POISON || effect.type == DuelStatusType.BURN) {
            fx.add(effect);
        } else {
            fx.removeIf(e -> e.type == effect.type);
            fx.add(effect);
        }
        // WITHER: immediately reduce maxHP
        if (effect.type == DuelStatusType.WITHER) {
            int[] maxHp = maxHpFor(player);
            int[] hp    = hpFor(player);
            int reduction = Math.max(1, (int) effect.magnitude);
            maxHp[petIdx] = Math.max(1, maxHp[petIdx] - reduction);
            if (hp[petIdx] > maxHp[petIdx]) hp[petIdx] = maxHp[petIdx];
        }
    }

    /** Ticks POISON / BURN effects on the given pet slot, dealing damage. */
    private void tickDotEffects(UUID player, int petIdx) {
        List<ActiveEffect> fx = effectsFor(player, petIdx);
        List<ActiveEffect> toRemove = new ArrayList<>();
        for (ActiveEffect e : fx) {
            if (e.type == DuelStatusType.POISON || e.type == DuelStatusType.BURN) {
                int dmg = Math.max(1, (int) e.magnitude);
                applyRawDamage(player, petIdx, dmg);
                String tag = e.type == DuelStatusType.POISON ? "poison" : "burn";
                addLog(petName(player, petIdx) + " takes " + dmg + " " + tag + " damage!");
                e.remainingTurns--;
                if (e.remainingTurns <= 0) toRemove.add(e);
            }
        }
        fx.removeAll(toRemove);
    }

    /** Decrements duration on timed effects (FATIGUE, FORTIFY, EVA_BOOST) for the given pet. */
    private void tickDurationEffects(UUID player, int petIdx) {
        List<ActiveEffect> fx = effectsFor(player, petIdx);
        fx.removeIf(e -> {
            if (e.remainingTurns > 0
                    && e.type != DuelStatusType.POISON
                    && e.type != DuelStatusType.BURN) {
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
     * Full damage pipeline: FATIGUE → FOCUSED → CRIT → EVA → PHASED → REFLECT → SHIELD → FORTIFY.
     *
     * @return actual HP removed from target (after all modifiers), or 0 on miss/dodge.
     */
    public int resolveDamage(UUID attacker, int attackerPet,
                             UUID target, int targetPet,
                             int baseDamage) {
        DerivedPetStats targetStats = new DerivedPetStats(rosterFor(target)[targetPet]);
        List<ActiveEffect> atkFx  = effectsFor(attacker, attackerPet);
        List<ActiveEffect> tgtFx  = effectsFor(target, targetPet);

        float damage = baseDamage;

        // ── Attacker modifiers ────────────────────────────────────────────────

        // FATIGUE: reduce ATK
        for (ActiveEffect e : atkFx) {
            if (e.type == DuelStatusType.FATIGUE) {
                damage *= (1.0f - e.magnitude);
                break;
            }
        }

        // FOCUSED: next-attack multiplier (consumed)
        ActiveEffect focused = atkFx.stream()
                .filter(e -> e.type == DuelStatusType.FOCUSED)
                .findFirst().orElse(null);
        if (focused != null) {
            damage *= focused.magnitude;
            atkFx.remove(focused);
        }

        // CRIT check — base chance + LUCKY_BONUS
        DerivedPetStats atkStats = new DerivedPetStats(rosterFor(attacker)[attackerPet]);
        int critPct = atkStats.critPct;
        ActiveEffect lucky = atkFx.stream()
                .filter(e -> e.type == DuelStatusType.LUCKY_BONUS)
                .findFirst().orElse(null);
        if (lucky != null) {
            critPct += (int) lucky.magnitude;
            atkFx.remove(lucky);
        }
        boolean isCrit = ThreadLocalRandom.current().nextInt(100) < critPct;
        if (isCrit) {
            damage *= 1.5f;
            addLog("Critical hit!");
        }

        // ── Target modifiers ──────────────────────────────────────────────────

        // PHASED: full immunity to next hit (consumed)
        ActiveEffect phased = tgtFx.stream()
                .filter(e -> e.type == DuelStatusType.PHASED)
                .findFirst().orElse(null);
        if (phased != null) {
            tgtFx.remove(phased);
            addLog(petName(target, targetPet) + " phased through the attack!");
            return 0;
        }

        // EVA check: base EVA + EVA_BOOST
        int evaPct = targetStats.evaPct;
        for (ActiveEffect e : tgtFx) {
            if (e.type == DuelStatusType.EVA_BOOST) {
                evaPct += (int) e.magnitude;
                break;
            }
        }
        if (ThreadLocalRandom.current().nextInt(100) < evaPct) {
            addLog(petName(target, targetPet) + " dodged the attack!");
            return 0;
        }

        // REFLECT: bounce a fraction back (consumed)
        ActiveEffect reflect = tgtFx.stream()
                .filter(e -> e.type == DuelStatusType.REFLECT)
                .findFirst().orElse(null);
        if (reflect != null) {
            tgtFx.remove(reflect);
            int reflectDmg = Math.max(1, (int)(damage * reflect.magnitude));
            applyRawDamage(attacker, attackerPet, reflectDmg);
            addLog(petName(attacker, attackerPet) + " takes " + reflectDmg + " reflected damage!");
        }

        // SHIELD: reduce incoming damage (consumed)
        ActiveEffect shield = tgtFx.stream()
                .filter(e -> e.type == DuelStatusType.SHIELD)
                .findFirst().orElse(null);
        if (shield != null) {
            tgtFx.remove(shield);
            damage *= (1.0f - shield.magnitude);
        }

        // FORTIFY: persistent damage reduction
        for (ActiveEffect e : tgtFx) {
            if (e.type == DuelStatusType.FORTIFY) {
                damage *= (1.0f - e.magnitude);
                break;
            }
        }

        int finalDmg = Math.max(0, (int) damage);
        if (finalDmg > 0) applyRawDamage(target, targetPet, finalDmg);
        return finalDmg;
    }

    /**
     * Applies raw damage to a pet, bypassing all mitigation checks.
     * Handles the Second Life passive.
     */
    public void applyRawDamage(UUID player, int petIdx, int damage) {
        int[] hp    = hpFor(player);
        int[] maxHp = maxHpFor(player);
        hp[petIdx] = Math.max(0, hp[petIdx] - damage);

        // Second Life passive: revive at 2 HP (once per pet per duel)
        if (hp[petIdx] == 0 && hasSecondLifeSkill(player, petIdx)) {
            boolean[] used = secondLifeUsedFor(player);
            if (!used[petIdx]) {
                used[petIdx] = true;
                hp[petIdx] = Math.min(2, maxHp[petIdx]);
                addLog("★ " + petName(player, petIdx) + "'s Second Life activates! Revived at 2 HP!");
            }
        }
    }

    /**
     * Adds {@code amount} to a pet's stored SP (carried over between turns), capped at SP_MAX.
     * Use this for off-turn SP restoration (e.g. spirit_burst hitting allies).
     */
    public void addStoredSP(UUID player, int petIdx, int amount) {
        String key = petKey(player, petIdx);
        int current = petSP.getOrDefault(key, 0);
        petSP.put(key, Math.min(SP_MAX, current + amount));
    }

    /** Heal a pet by {@code amount}, capped at its current max HP. */
    public void heal(UUID player, int petIdx, int amount) {
        int[] hp    = hpFor(player);
        int[] maxHp = maxHpFor(player);
        hp[petIdx] = Math.min(maxHp[petIdx], hp[petIdx] + amount);
    }

    // =========================================================================
    // Win-condition check
    // =========================================================================

    /**
     * Checks whether one side has all three pets at 0 HP.
     * If so, sets {@link #winner} and {@link #phase} = FINISHED.
     *
     * @return true if the duel is now finished
     */
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

    /** Returns an unmodifiable snapshot of the combat log (most-recent last). */
    public List<String> getLog() {
        return List.copyOf(combatLog);
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /** Human-readable pet name for use in combat log lines. */
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

    /** Checks whether a pet has the second_life skill at level ≥ 1. */
    private boolean hasSecondLifeSkill(UUID player, int petIdx) {
        PetData pet = rosterFor(player)[petIdx];
        if (pet == null) return false;
        for (SkillInstance si : pet.skills()) {
            if ("second_life".equals(si.skill().getId()) && si.level() > 0) return true;
        }
        return false;
    }

    /** Returns the ATK value for a pet (used when building basic-attack damage). */
    public int getAtk(UUID player, int petIdx) {
        return new DerivedPetStats(rosterFor(player)[petIdx]).atk;
    }

    /** Snapshot of all active effects for network serialisation. */
    public Map<String, List<ActiveEffect>> getAllEffects() {
        return Collections.unmodifiableMap(effects);
    }

    /** Snapshot of all skill cooldowns. */
    public Map<String, Integer> getAllCooldowns() {
        return Collections.unmodifiableMap(skillCooldowns);
    }

    /** Current turn order snapshot. */
    public List<TurnSlot> getTurnOrder() {
        return Collections.unmodifiableList(turnOrder);
    }

    public int getTurnOrderIndex() {
        return turnOrderIndex;
    }
}
