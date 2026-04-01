package com.arcadia.pets.duel;

import com.arcadia.pets.item.DerivedPetStats;
import com.arcadia.pets.item.PetStat;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Maps every pet skill ID to its {@link DuelSkillDef}, defining AP cost,
 * cooldown (scaling with level), target type, and the actual combat effect.
 *
 * <h3>Level-scaling patterns used</h3>
 * <ul>
 *   <li>Linear step: {@code base + level * step}</li>
 *   <li>Tier divisions: {@code base + level / N} (integer division)</li>
 *   <li>Cooldown formula: {@code max(minCd, baseCd - level / 3)}</li>
 * </ul>
 *
 * <p>{@code second_life} is intentionally absent — it is a passive triggered
 * automatically by {@link DuelSession#applyRawDamage}.</p>
 */
public final class DuelSkillAdapter {

    private static final Map<String, DuelSkillDef> REGISTRY;

    static {
        Map<String, DuelSkillDef> m = new HashMap<>();

        // ── COMMON ────────────────────────────────────────────────────────────

        // featherfall: EVA boost self
        // lv1: +13% EVA for 1 turn → lv10: +40% EVA for 2 turns
        m.put("featherfall", new DuelSkillDef(1, 2, 1, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int bonus = 10 + level * 3;
                    int turns = level >= 5 ? 2 : 1;
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.EVA_BOOST, turns, bonus));
                    return session.petName(actor, aIdx) + " levitates! +" + bonus
                            + "% EVA for " + turns + " turn(s).";
                }));

        // lucky_oink: small heal to any ally
        // lv1: heal 1 HP → lv10: heal 6 HP
        m.put("lucky_oink", new DuelSkillDef(1, 2, 1, DuelTargetType.ALLY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int heal = Math.max(1, 1 + level / 2);
                    session.heal(target, tIdx, heal);
                    return session.petName(actor, aIdx) + " shares good fortune! "
                            + session.petName(target, tIdx) + " recovers " + heal + " HP.";
                }));

        // steady_heal: moderate heal to any ally
        // lv1: heal 2 HP → lv10: heal 7 HP
        m.put("steady_heal", new DuelSkillDef(1, 2, 1, DuelTargetType.ALLY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int heal = 2 + level / 2;
                    session.heal(target, tIdx, heal);
                    return session.petName(actor, aIdx) + " channels steady healing! "
                            + session.petName(target, tIdx) + " recovers " + heal + " HP.";
                }));

        // woolly_buffer: shield any ally
        // lv1: 24% shield → lv10: 60% shield
        m.put("woolly_buffer", new DuelSkillDef(1, 3, 2, DuelTargetType.ALLY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float pct = 0.20f + level * 0.04f;
                    session.applyEffect(target, tIdx,
                            new ActiveEffect(DuelStatusType.SHIELD, -1, pct));
                    return session.petName(actor, aIdx) + " wraps " + session.petName(target, tIdx)
                            + " in wool! Next hit blocked by " + Math.round(pct * 100) + "%.";
                }));

        // lucky_paw: CRIT bonus for self's next attack
        // lv1: +10% CRIT → lv10: +28% CRIT
        m.put("lucky_paw", new DuelSkillDef(1, 2, 1, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int bonus = 8 + level * 2;
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.LUCKY_BONUS, -1, bonus));
                    return session.petName(actor, aIdx) + " raises a lucky paw! +"
                            + bonus + "% CRIT on next attack.";
                }));

        // night_vision: FOCUSED — next attack multiplied
        // lv1: ×1.37 → lv10: ×2.0
        m.put("night_vision", new DuelSkillDef(1, 2, 1, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float mult = 1.3f + level * 0.07f;
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.FOCUSED, -1, mult));
                    return session.petName(actor, aIdx) + " focuses in the dark! Next attack ×"
                            + String.format("%.1f", mult) + ".";
                }));

        // quick_step: grant bonus AP this turn
        // lv1: +1 AP → lv5: +2 AP → lv10: +3 AP
        m.put("quick_step", new DuelSkillDef(1, 3, 2, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int extra = 1 + level / 5;
                    session.currentAP += extra;
                    return session.petName(actor, aIdx) + " dashes with light feet! +"
                            + extra + " AP granted.";
                }));

        // sweet_sting: apply POISON to an enemy
        // lv1: 1 dmg/turn × 2 turns → lv10: 3 dmg/turn × 4 turns
        m.put("sweet_sting", new DuelSkillDef(1, 2, 1, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int dmg   = 1 + level / 4;
                    int turns = 2 + level / 5;
                    session.applyEffect(target, tIdx,
                            new ActiveEffect(DuelStatusType.POISON, turns, dmg));
                    return session.petName(actor, aIdx) + " stings " + session.petName(target, tIdx)
                            + "! " + dmg + " poison damage for " + turns + " turns.";
                }));

        // ── UNCOMMON ──────────────────────────────────────────────────────────

        // pack_call: heavy single-target damage
        // lv1: ATK×1.6 → lv10: ATK×2.5
        m.put("pack_call", new DuelSkillDef(2, 3, 2, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float mult  = 1.5f + level * 0.1f;
                    int baseDmg = (int)(session.getAtk(actor, aIdx) * mult);
                    int dealt   = session.resolveDamage(actor, aIdx, target, tIdx, baseDmg);
                    return session.petName(actor, aIdx) + " calls the pack on "
                            + session.petName(target, tIdx) + "! " + dealt + " damage.";
                }));

        // aquatic_bond: FATIGUE on enemy (ATK reduction)
        // lv1: -34% ATK for 1 turn → lv10: -70% ATK for 3 turns
        m.put("aquatic_bond", new DuelSkillDef(2, 3, 2, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float reduction = 0.30f + level * 0.04f;
                    int   turns     = 1 + level / 4;
                    session.applyEffect(target, tIdx,
                            new ActiveEffect(DuelStatusType.FATIGUE, turns, reduction));
                    return session.petName(actor, aIdx) + " weakens " + session.petName(target, tIdx)
                            + "! ATK -" + Math.round(reduction * 100) + "% for " + turns + " turn(s).";
                }));

        // bamboo_fortitude: FORTIFY an ally (damage reduction)
        // lv1: -13% dmg for 2 turns → lv10: -40% dmg for 4 turns
        m.put("bamboo_fortitude", new DuelSkillDef(2, 3, 2, DuelTargetType.ALLY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float reduction = 0.10f + level * 0.03f;
                    int   turns     = 2 + level / 4;
                    session.applyEffect(target, tIdx,
                            new ActiveEffect(DuelStatusType.FORTIFY, turns, reduction));
                    return session.petName(actor, aIdx) + " hardens " + session.petName(target, tIdx)
                            + "'s hide! -" + Math.round(reduction * 100) + "% incoming damage for "
                            + turns + " turn(s).";
                }));

        // bounding_leap: damage ignoring SHIELD and FORTIFY (piercing)
        // lv1: ATK+1 → lv10: ATK+10, bypasses damage reductions
        m.put("bounding_leap", new DuelSkillDef(2, 3, 2, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int dmg = session.getAtk(actor, aIdx) + level;
                    // Piercing: apply raw damage, skip full mitigation pipeline
                    // Still checks PHASED (full immunity) and basic EVA
                    DerivedPetStats tStats = new DerivedPetStats(session.rosterFor(target)[tIdx]);
                    List<ActiveEffect> tFx = session.effectsFor(target, tIdx);
                    ActiveEffect phased = tFx.stream()
                            .filter(e -> e.type == DuelStatusType.PHASED)
                            .findFirst().orElse(null);
                    if (phased != null) {
                        tFx.remove(phased);
                        return session.petName(target, tIdx) + " phased through the leap!";
                    }
                    if (ThreadLocalRandom.current().nextInt(100) < tStats.evaPct) {
                        return session.petName(target, tIdx) + " leaped away!";
                    }
                    session.applyRawDamage(target, tIdx, dmg);
                    return session.petName(actor, aIdx) + " leaps at "
                            + session.petName(target, tIdx) + "! " + dmg + " piercing damage.";
                }));

        // shell_guard: PHASED self + EVA boost at higher levels
        // lv1-4: just PHASED (immune to next hit)
        // lv5+: PHASED + EVA_BOOST (+15% for 1 turn)
        // lv10: PHASED + EVA_BOOST (+25%)
        m.put("shell_guard", new DuelSkillDef(2, 3, 2, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.PHASED, -1, 1.0f));
                    if (level >= 5) {
                        int evaBonus = 10 + level * 2;
                        session.applyEffect(actor, aIdx,
                                new ActiveEffect(DuelStatusType.EVA_BOOST, 1, evaBonus));
                        return session.petName(actor, aIdx)
                                + " withdraws into its shell! Immune to next hit + +"
                                + evaBonus + "% EVA.";
                    }
                    return session.petName(actor, aIdx)
                            + " withdraws into its shell! Immune to next hit.";
                }));

        // ── RARE ──────────────────────────────────────────────────────────────

        // wishful_gift: random effect — heal self, damage random enemy, or cleanse a status
        // lv1: magnitude 2 → lv10: magnitude 7
        m.put("wishful_gift", new DuelSkillDef(2, 3, 2, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int value = 2 + level / 2;
                    int roll  = ThreadLocalRandom.current().nextInt(3);
                    return switch (roll) {
                        case 0 -> {
                            session.heal(actor, aIdx, value);
                            yield session.petName(actor, aIdx) + " receives a gift! Healed " + value + " HP.";
                        }
                        case 1 -> {
                            UUID opp    = session.opponentOf(actor);
                            int  oppIdx = session.firstAlivePet(opp);
                            if (oppIdx < 0) yield session.petName(actor, aIdx) + "'s gift fizzles.";
                            int dealt = session.resolveDamage(actor, aIdx, opp, oppIdx, value);
                            yield session.petName(actor, aIdx) + "'s gift curses "
                                    + session.petName(opp, oppIdx) + "! " + dealt + " damage.";
                        }
                        default -> {
                            List<ActiveEffect> ownFx = session.effectsFor(actor, aIdx);
                            if (ownFx.isEmpty())
                                yield session.petName(actor, aIdx) + "'s gift sparkles harmlessly.";
                            // Remove the highest-impact negative effect
                            ActiveEffect worst = ownFx.stream()
                                    .filter(e -> e.type == DuelStatusType.POISON
                                            || e.type == DuelStatusType.BURN
                                            || e.type == DuelStatusType.STUN
                                            || e.type == DuelStatusType.FATIGUE
                                            || e.type == DuelStatusType.WITHER)
                                    .findFirst().orElse(null);
                            if (worst != null) {
                                ownFx.remove(worst);
                                yield session.petName(actor, aIdx) + "'s gift cleanses " + worst.label() + "!";
                            }
                            yield session.petName(actor, aIdx) + "'s gift sparkles harmlessly.";
                        }
                    };
                }));

        // ancient_sense: large FOCUSED multiplier (next attack)
        // lv1: ×1.6 → lv10: ×2.5
        m.put("ancient_sense", new DuelSkillDef(2, 3, 2, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float mult = 1.5f + level * 0.1f;
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.FOCUSED, -1, mult));
                    return session.petName(actor, aIdx) + " senses weakness! Next attack ×"
                            + String.format("%.1f", mult) + ".";
                }));

        // lava_walker: apply BURN to an enemy
        // lv1: 1 dmg/turn × 2 turns → lv10: 4 dmg/turn × 4 turns
        m.put("lava_walker", new DuelSkillDef(2, 3, 2, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int dmg   = 1 + level / 3;
                    int turns = 2 + level / 4;
                    session.applyEffect(target, tIdx,
                            new ActiveEffect(DuelStatusType.BURN, turns, dmg));
                    return session.petName(actor, aIdx) + " scorches "
                            + session.petName(target, tIdx) + "! " + dmg
                            + " burn damage for " + turns + " turns.";
                }));

        // ── EPIC ──────────────────────────────────────────────────────────────

        // iron_will: REFLECT self (return a fraction of incoming damage)
        // lv1: reflect 23% → lv10: reflect 50%
        m.put("iron_will", new DuelSkillDef(2, 4, 3, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float reflectPct = 0.20f + level * 0.03f;
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.REFLECT, -1, reflectPct));
                    return session.petName(actor, aIdx) + " steels itself! Will reflect "
                            + Math.round(reflectPct * 100) + "% of the next hit.";
                }));

        // void_step: PHASED self (immune to next hit)
        m.put("void_step", new DuelSkillDef(2, 4, 3, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    // Higher level also grants a small EVA boost after phasing
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.PHASED, -1, 1.0f));
                    if (level >= 7) {
                        int evaBonus = level * 2;
                        session.applyEffect(actor, aIdx,
                                new ActiveEffect(DuelStatusType.EVA_BOOST, 1, evaBonus));
                        return session.petName(actor, aIdx)
                                + " steps into the void! Immune to next hit + +"
                                + evaBonus + "% EVA.";
                    }
                    return session.petName(actor, aIdx) + " steps into the void! Immune to next hit.";
                }));

        // flame_aura: damage all enemies with fire
        // lv1: 2 dmg each → lv10: 7 dmg each
        m.put("flame_aura", new DuelSkillDef(3, 4, 3, DuelTargetType.ALL_ENEMIES,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int dmg = 2 + level / 2;
                    UUID opp = session.opponentOf(actor);
                    StringBuilder log = new StringBuilder(
                            session.petName(actor, aIdx) + " erupts in flames!");
                    for (int i = 0; i < 3; i++) {
                        if (session.isAlive(opp, i)) {
                            int dealt = session.resolveDamage(actor, aIdx, opp, i, dmg);
                            if (dealt > 0)
                                log.append(" ").append(session.petName(opp, i))
                                        .append(": ").append(dealt).append(" dmg.");
                        }
                    }
                    return log.toString();
                }));

        // soul_drain: WITHER + immediate damage on single enemy
        // lv1: wither -1 maxHP, 1 dmg → lv10: wither -4 maxHP, 3 dmg
        m.put("soul_drain", new DuelSkillDef(2, 4, 3, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int wither = 1 + level / 3;
                    int dmg    = 1 + level / 4;
                    session.applyEffect(target, tIdx,
                            new ActiveEffect(DuelStatusType.WITHER, -1, wither));
                    int dealt = session.resolveDamage(actor, aIdx, target, tIdx, dmg);
                    return session.petName(actor, aIdx) + " drains the soul of "
                            + session.petName(target, tIdx) + "! Max HP -" + wither
                            + ", " + dealt + " damage.";
                }));

        // ── LEGENDARY ─────────────────────────────────────────────────────────

        // sonic_shriek: damage + STUN (skip enemy's next turn)
        // lv1: 3 dmg → lv10: 8 dmg, always stuns
        m.put("sonic_shriek", new DuelSkillDef(3, 5, 3, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int dmg   = 3 + level / 2;
                    int dealt = session.resolveDamage(actor, aIdx, target, tIdx, dmg);
                    if (dealt > 0) {
                        session.applyEffect(target, tIdx,
                                new ActiveEffect(DuelStatusType.STUN, 1, 0));
                        return session.petName(actor, aIdx) + " unleashes a sonic shriek at "
                                + session.petName(target, tIdx) + "! " + dealt
                                + " damage + STUNNED!";
                    }
                    return session.petName(actor, aIdx) + "'s shriek missed "
                            + session.petName(target, tIdx) + "!";
                }));

        // draconic_surge: massive single-target burst
        // lv1: ATK×2.1 → lv10: ATK×3.0
        m.put("draconic_surge", new DuelSkillDef(3, 5, 3, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float mult  = 2.0f + level * 0.1f;
                    int baseDmg = (int)(session.getAtk(actor, aIdx) * mult);
                    int dealt   = session.resolveDamage(actor, aIdx, target, tIdx, baseDmg);
                    return session.petName(actor, aIdx) + " surges with draconic power! "
                            + dealt + " damage to " + session.petName(target, tIdx) + "!";
                }));

        // wither_aura: damage + WITHER all enemies
        // lv1: 1 dmg, -1 maxHP each → lv10: 4 dmg, -3 maxHP each
        m.put("wither_aura", new DuelSkillDef(3, 5, 3, DuelTargetType.ALL_ENEMIES,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int dmg    = 1 + level / 3;
                    int wither = 1 + level / 4;
                    UUID opp = session.opponentOf(actor);
                    StringBuilder log = new StringBuilder(
                            session.petName(actor, aIdx) + " radiates wither energy!");
                    for (int i = 0; i < 3; i++) {
                        if (session.isAlive(opp, i)) {
                            session.applyEffect(opp, i,
                                    new ActiveEffect(DuelStatusType.WITHER, -1, wither));
                            int dealt = session.resolveDamage(actor, aIdx, opp, i, dmg);
                            if (dealt > 0 || wither > 0)
                                log.append(" ").append(session.petName(opp, i))
                                        .append(": ").append(dealt).append(" dmg, max HP -")
                                        .append(wither).append(".");
                        }
                    }
                    return log.toString();
                }));

        // fatigue_curse: FATIGUE + STUN on one enemy
        // lv1: -40% ATK for 1 turn → lv10: -70% ATK for 3 turns, always stuns
        m.put("fatigue_curse", new DuelSkillDef(3, 5, 3, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float fatMag = 0.37f + level * 0.03f;
                    int   turns  = 1 + level / 4;
                    session.applyEffect(target, tIdx,
                            new ActiveEffect(DuelStatusType.FATIGUE, turns, fatMag));
                    session.applyEffect(target, tIdx,
                            new ActiveEffect(DuelStatusType.STUN, 1, 0));
                    return session.petName(actor, aIdx) + " curses "
                            + session.petName(target, tIdx) + "! ATK -"
                            + Math.round(fatMag * 100) + "% for " + turns
                            + " turn(s) + STUNNED!";
                }));

        // ground_slam: AoE damage scaling with actor's END stat
        // lv1: (ATK+END)×0.84 each → lv10: (ATK+END)×1.2 each
        m.put("ground_slam", new DuelSkillDef(3, 5, 3, DuelTargetType.ALL_ENEMIES,
                (session, actor, aIdx, target, tIdx, level) -> {
                    DerivedPetStats atkStats = new DerivedPetStats(session.rosterFor(actor)[aIdx]);
                    int endStar = session.rosterFor(actor)[aIdx].stats()
                            .getOrDefault(PetStat.ENDURANCE, 1);
                    float scale  = 0.80f + level * 0.04f;
                    int   baseDmg = (int)((atkStats.atk + endStar) * scale);
                    UUID opp = session.opponentOf(actor);
                    StringBuilder log = new StringBuilder(
                            session.petName(actor, aIdx) + " slams the ground!");
                    for (int i = 0; i < 3; i++) {
                        if (session.isAlive(opp, i)) {
                            int dealt = session.resolveDamage(actor, aIdx, opp, i, baseDmg);
                            if (dealt > 0)
                                log.append(" ").append(session.petName(opp, i))
                                        .append(": ").append(dealt).append(" dmg.");
                        }
                    }
                    return log.toString();
                }));

        // wind_deflect: REFLECT + EVA_BOOST self
        // lv1: reflect 23%, +12% EVA for 1 turn → lv10: reflect 50%, +30% EVA for 2 turns
        m.put("wind_deflect", new DuelSkillDef(2, 4, 3, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float reflectPct = 0.20f + level * 0.03f;
                    int   evaBonus   = 10 + level * 2;
                    int   evaTurns   = level >= 5 ? 2 : 1;
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.REFLECT, -1, reflectPct));
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.EVA_BOOST, evaTurns, evaBonus));
                    return session.petName(actor, aIdx) + " rides the wind! Reflect "
                            + Math.round(reflectPct * 100) + "% + +" + evaBonus
                            + "% EVA for " + evaTurns + " turn(s).";
                }));

        // wither_skull: unblockable damage (bypasses SHIELD, FORTIFY, PHASED)
        // lv1: 3 dmg → lv10: 8 dmg, truly piercing (uses applyRawDamage directly)
        m.put("wither_skull", new DuelSkillDef(3, 5, 3, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int dmg = 3 + level / 2;
                    // Unblockable — bypass all mitigation, only second_life can save from this
                    session.applyRawDamage(target, tIdx, dmg);
                    return session.petName(actor, aIdx) + " fires a wither skull at "
                            + session.petName(target, tIdx) + "! " + dmg + " unblockable damage!";
                }));

        // second_life: PASSIVE — not manually usable (see DuelSession.applyRawDamage)
        // Intentionally absent from registry.

        REGISTRY = Collections.unmodifiableMap(m);
    }

    private DuelSkillAdapter() {}

    /** Returns the {@link DuelSkillDef} for a skill ID, or {@code null} if not found. */
    public static DuelSkillDef get(String skillId) {
        return REGISTRY.get(skillId);
    }

    /** Returns true if the given skill has a duel definition (i.e. is manually usable). */
    public static boolean hasDef(String skillId) {
        return REGISTRY.containsKey(skillId);
    }
}
