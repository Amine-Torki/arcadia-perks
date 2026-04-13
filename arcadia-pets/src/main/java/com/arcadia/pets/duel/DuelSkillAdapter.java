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
 * Maps every pet skill ID to its {@link DuelSkillDef}, defining SP cost,
 * cooldown (scaling with level), target type, and the actual combat effect.
 *
 * <h3>Spirit Point economy (SP: 1 start, +1/turn, max 5, carries over)</h3>
 * <ul>
 *   <li><b>1 SP</b> — quick / reactive (DoT, single-target buff, small heal, EVA)</li>
 *   <li><b>2 SP</b> — moderate (FOCUSED, shields, piercing, multi-turn debuffs)</li>
 *   <li><b>3 SP</b> — heavy (AoE, stun, massive single-target burst)</li>
 *   <li><b>4 SP</b> — ultimate (LEGENDARY/MYTHIC tier, unblockable or full-team effects)</li>
 * </ul>
 *
 * <h3>SP-restore skills</h3>
 * A handful of skills give back SP directly (via {@code session.currentSP +=}).
 * These are thematic "energy gathering" moves that reward patience.
 *
 * <h3>Level-scaling patterns</h3>
 * <ul>
 *   <li>Linear: {@code base + level * step}</li>
 *   <li>Tier: {@code base + level / N} (integer division)</li>
 *   <li>Cooldown: {@code max(minCd, baseCd - level / 3)}</li>
 * </ul>
 *
 * <p>{@code second_life} is absent — it is a passive triggered automatically
 * by {@link DuelSession#applyRawDamage}.</p>
 */
public final class DuelSkillAdapter {

    private static final Map<String, DuelSkillDef> REGISTRY;

    static {
        Map<String, DuelSkillDef> m = new HashMap<>();

        // ══════════════════════════════════════════════════════════════════════
        // COMMON SKILLS  (1 SP — lightweight, always accessible early game)
        // ══════════════════════════════════════════════════════════════════════

        // featherfall: EVA boost self (1 or 2 turns)
        // lv1: +13% EVA × 1t → lv5+: × 2t → lv10: +40% EVA × 2t
        m.put("featherfall", new DuelSkillDef(1, 2, 1, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int bonus = 10 + level * 3;
                    int turns = level >= 5 ? 2 : 1;
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.EVA_BOOST, turns, bonus));
                    return session.petName(actor, aIdx) + " levitates! +" + bonus
                            + "% EVA for " + turns + " turn(s).";
                }));

        // lucky_oink: small single-ally heal
        // lv1: 2 HP → lv10: 7 HP
        m.put("lucky_oink", new DuelSkillDef(1, 2, 1, DuelTargetType.ALLY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int heal = 2 + level / 2;
                    session.heal(target, tIdx, heal);
                    return session.petName(actor, aIdx) + " shares good fortune! "
                            + session.petName(target, tIdx) + " recovers " + heal + " HP.";
                }));

        // steady_heal: moderate single-ally heal
        // lv1: 3 HP → lv10: 8 HP
        m.put("steady_heal", new DuelSkillDef(1, 2, 1, DuelTargetType.ALLY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int heal = 3 + level / 2;
                    session.heal(target, tIdx, heal);
                    return session.petName(actor, aIdx) + " channels steady healing! "
                            + session.petName(target, tIdx) + " recovers " + heal + " HP.";
                }));

        // lucky_paw: LUCKY_BONUS — next attack gains CRIT chance
        // lv1: +10% CRIT → lv10: +28% CRIT
        m.put("lucky_paw", new DuelSkillDef(1, 2, 1, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int bonus = 8 + level * 2;
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.LUCKY_BONUS, -1, bonus));
                    return session.petName(actor, aIdx) + " raises a lucky paw! +"
                            + bonus + "% CRIT on next attack.";
                }));

        // night_vision: FOCUSED — multiplies the next attack
        // lv1: ×1.37 → lv10: ×2.0
        m.put("night_vision", new DuelSkillDef(1, 2, 1, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float mult = 1.3f + level * 0.07f;
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.FOCUSED, -1, mult));
                    return session.petName(actor, aIdx) + " focuses in the dark! Next attack ×"
                            + String.format("%.1f", mult) + ".";
                }));

        // quick_step: ★ SP RESTORE — rush forward and regain spirit
        // lv1: +1 SP → lv5+: +2 SP → lv10: +3 SP (capped at SP_MAX)
        m.put("quick_step", new DuelSkillDef(1, 3, 2, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int gain = 1 + level / 5;
                    // Net gain: costs 1 SP but gives back 2-4 → effective +1 to +3
                    session.currentSP = Math.min(DuelSession.SP_MAX, session.currentSP + gain);
                    return session.petName(actor, aIdx) + " dashes with light feet! +"
                            + gain + " SP restored (" + session.currentSP + "/" + DuelSession.SP_MAX + ").";
                }));

        // sweet_sting: POISON DoT on one enemy
        // lv1: 1 dmg/turn × 2t → lv10: 3 dmg/turn × 4t
        m.put("sweet_sting", new DuelSkillDef(1, 2, 1, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int dmg   = 1 + level / 4;
                    int turns = 2 + level / 5;
                    session.applyEffect(target, tIdx,
                            new ActiveEffect(DuelStatusType.POISON, turns, dmg));
                    return session.petName(actor, aIdx) + " stings " + session.petName(target, tIdx)
                            + "! " + dmg + " poison dmg × " + turns + " turns.";
                }));

        // ══════════════════════════════════════════════════════════════════════
        // UNCOMMON SKILLS  (2 SP — meaningful investment, mid-game payoff)
        // ══════════════════════════════════════════════════════════════════════

        // woolly_buffer: SHIELD an ally against the next hit
        // lv1: 24% block → lv10: 60% block
        m.put("woolly_buffer", new DuelSkillDef(2, 3, 2, DuelTargetType.ALLY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float pct = 0.20f + level * 0.04f;
                    session.applyEffect(target, tIdx,
                            new ActiveEffect(DuelStatusType.SHIELD, -1, pct));
                    return session.petName(actor, aIdx) + " wraps " + session.petName(target, tIdx)
                            + " in wool! Next hit blocked by " + Math.round(pct * 100) + "%.";
                }));

        // pack_call: heavy single-target damage (scaled ATK burst)
        // lv1: ATK×1.6 → lv10: ATK×2.5
        m.put("pack_call", new DuelSkillDef(2, 3, 2, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float mult  = 1.5f + level * 0.1f;
                    int baseDmg = (int)(session.getAtk(actor, aIdx) * mult);
                    int dealt   = session.resolveDamage(actor, aIdx, target, tIdx, baseDmg);
                    return session.petName(actor, aIdx) + " calls the pack on "
                            + session.petName(target, tIdx) + "! " + dealt + " damage.";
                }));

        // aquatic_bond: FATIGUE on one enemy (ATK reduction, multi-turn)
        // lv1: -34% ATK × 1t → lv10: -70% ATK × 3t
        m.put("aquatic_bond", new DuelSkillDef(2, 3, 2, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float reduction = 0.30f + level * 0.04f;
                    int   turns     = 1 + level / 4;
                    session.applyEffect(target, tIdx,
                            new ActiveEffect(DuelStatusType.FATIGUE, turns, reduction));
                    return session.petName(actor, aIdx) + " weakens " + session.petName(target, tIdx)
                            + "! ATK -" + Math.round(reduction * 100) + "% × " + turns + "t.";
                }));

        // bamboo_fortitude: FORTIFY an ally (persistent damage reduction)
        // lv1: -13% dmg × 2t → lv10: -40% dmg × 4t
        m.put("bamboo_fortitude", new DuelSkillDef(2, 3, 2, DuelTargetType.ALLY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float reduction = 0.10f + level * 0.03f;
                    int   turns     = 2 + level / 4;
                    session.applyEffect(target, tIdx,
                            new ActiveEffect(DuelStatusType.FORTIFY, turns, reduction));
                    return session.petName(actor, aIdx) + " hardens " + session.petName(target, tIdx)
                            + "'s hide! -" + Math.round(reduction * 100) + "% damage × " + turns + "t.";
                }));

        // bounding_leap: piercing damage (ignores SHIELD + FORTIFY)
        // lv1: ATK+1 → lv10: ATK+10
        m.put("bounding_leap", new DuelSkillDef(2, 3, 2, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int dmg = session.getAtk(actor, aIdx) + level;
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
                            + session.petName(target, tIdx) + "! " + dmg + " piercing dmg.";
                }));

        // shell_guard: PHASED self (next hit immune)
        // lv5+: also +EVA_BOOST — lv1-4: 10+lv×2% → lv10: 25% EVA
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

        // lava_walker: BURN DoT on one enemy
        // lv1: 1 dmg/turn × 2t → lv10: 4 dmg/turn × 4t
        m.put("lava_walker", new DuelSkillDef(2, 3, 2, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int dmg   = 1 + level / 3;
                    int turns = 2 + level / 4;
                    session.applyEffect(target, tIdx,
                            new ActiveEffect(DuelStatusType.BURN, turns, dmg));
                    return session.petName(actor, aIdx) + " scorches "
                            + session.petName(target, tIdx) + "! " + dmg
                            + " burn dmg × " + turns + " turns.";
                }));

        // ★ meditate: SP RESTORE — commune with inner spirit (2 SP cost → +3 or +4 SP)
        // lv1: cost 2, gain 3 → net +1 SP | lv6+: gain 4 → net +2 SP
        // Use sparingly: CD 4 turns (min 2 at high level)
        m.put("meditate", new DuelSkillDef(2, 4, 2, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int gain = level >= 6 ? 4 : 3;
                    session.currentSP = Math.min(DuelSession.SP_MAX, session.currentSP + gain);
                    // Small HP recovery as bonus
                    int healAmt = 1 + level / 4;
                    session.heal(actor, aIdx, healAmt);
                    return session.petName(actor, aIdx) + " meditates! +" + gain
                            + " SP (" + session.currentSP + "/" + DuelSession.SP_MAX
                            + ") and +" + healAmt + " HP.";
                }));

        // wishful_gift: random effect — heal/damage/cleanse
        // lv1: magnitude 3 → lv10: magnitude 8
        m.put("wishful_gift", new DuelSkillDef(2, 3, 2, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int value = 3 + level / 2;
                    int roll  = ThreadLocalRandom.current().nextInt(3);
                    return switch (roll) {
                        case 0 -> {
                            session.heal(actor, aIdx, value);
                            yield session.petName(actor, aIdx) + " receives a gift! +" + value + " HP.";
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

        // ══════════════════════════════════════════════════════════════════════
        // RARE / EPIC SKILLS  (2–3 SP — strong effects, longer cooldowns)
        // ══════════════════════════════════════════════════════════════════════

        // iron_will: REFLECT self (return fraction of incoming damage)
        // lv1: 23% → lv10: 50% reflect
        m.put("iron_will", new DuelSkillDef(2, 4, 3, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float reflectPct = 0.20f + level * 0.03f;
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.REFLECT, -1, reflectPct));
                    return session.petName(actor, aIdx) + " steels itself! Reflects "
                            + Math.round(reflectPct * 100) + "% of next hit.";
                }));

        // void_step: PHASED self — epic tier, also EVA at lv7+
        m.put("void_step", new DuelSkillDef(3, 4, 3, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.PHASED, -1, 1.0f));
                    if (level >= 7) {
                        int evaBonus = level * 2;
                        session.applyEffect(actor, aIdx,
                                new ActiveEffect(DuelStatusType.EVA_BOOST, 1, evaBonus));
                        return session.petName(actor, aIdx)
                                + " steps into the void! Immune to next hit + +" + evaBonus + "% EVA.";
                    }
                    return session.petName(actor, aIdx) + " steps into the void! Immune to next hit.";
                }));

        // flame_aura: AoE fire damage to all enemies
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

        // soul_drain: WITHER target + immediate damage + ★ restores 1 SP
        // lv1: wither -1 maxHP, 1 dmg → lv10: wither -4 maxHP, 3 dmg
        m.put("soul_drain", new DuelSkillDef(2, 4, 3, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int wither = 1 + level / 3;
                    int dmg    = 1 + level / 4;
                    session.applyEffect(target, tIdx,
                            new ActiveEffect(DuelStatusType.WITHER, -1, wither));
                    int dealt = session.resolveDamage(actor, aIdx, target, tIdx, dmg);
                    // Life drain restores 1 SP
                    session.currentSP = Math.min(DuelSession.SP_MAX, session.currentSP + 1);
                    return session.petName(actor, aIdx) + " drains the soul of "
                            + session.petName(target, tIdx) + "! Max HP -" + wither
                            + ", " + dealt + " dmg, +1 SP drained.";
                }));

        // wind_deflect: REFLECT + EVA_BOOST self
        // lv1: reflect 23%, +12% EVA × 1t → lv10: reflect 50%, +30% EVA × 2t
        m.put("wind_deflect", new DuelSkillDef(2, 4, 3, DuelTargetType.SELF,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float reflectPct = 0.20f + level * 0.03f;
                    int   evaBonus   = 10 + level * 2;
                    int   evaTurns   = level >= 5 ? 2 : 1;
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.REFLECT, -1, reflectPct));
                    session.applyEffect(actor, aIdx,
                            new ActiveEffect(DuelStatusType.EVA_BOOST, evaTurns, evaBonus));
                    return session.petName(actor, aIdx) + " rides the wind! Reflects "
                            + Math.round(reflectPct * 100) + "% + +" + evaBonus
                            + "% EVA × " + evaTurns + "t.";
                }));

        // ══════════════════════════════════════════════════════════════════════
        // LEGENDARY SKILLS  (3–4 SP — high-impact, long cooldowns)
        // ══════════════════════════════════════════════════════════════════════

        // sonic_shriek: damage + STUN (enemy loses next turn)
        // lv1: 3 dmg → lv10: 8 dmg
        m.put("sonic_shriek", new DuelSkillDef(3, 5, 3, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int dmg   = 3 + level / 2;
                    int dealt = session.resolveDamage(actor, aIdx, target, tIdx, dmg);
                    if (dealt > 0) {
                        session.applyEffect(target, tIdx,
                                new ActiveEffect(DuelStatusType.STUN, 1, 0));
                        return session.petName(actor, aIdx) + " shrieks at "
                                + session.petName(target, tIdx) + "! " + dealt
                                + " dmg + STUNNED!";
                    }
                    return session.petName(actor, aIdx) + "'s shriek missed!";
                }));

        // draconic_surge: massive single-target burst
        // lv1: ATK×2.1 → lv10: ATK×3.0
        m.put("draconic_surge", new DuelSkillDef(3, 5, 3, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    float mult  = 2.0f + level * 0.1f;
                    int baseDmg = (int)(session.getAtk(actor, aIdx) * mult);
                    int dealt   = session.resolveDamage(actor, aIdx, target, tIdx, baseDmg);
                    return session.petName(actor, aIdx) + " surges with draconic power! "
                            + dealt + " dmg to " + session.petName(target, tIdx) + "!";
                }));

        // wither_aura: AoE WITHER + damage on all enemies
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
                                        .append(": ").append(dealt).append(" dmg, maxHP -")
                                        .append(wither).append(".");
                        }
                    }
                    return log.toString();
                }));

        // fatigue_curse: FATIGUE + STUN on one enemy
        // lv1: -40% ATK × 1t → lv10: -70% ATK × 3t, always stuns
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
                            + Math.round(fatMag * 100) + "% × " + turns + "t + STUNNED!";
                }));

        // ground_slam: AoE scaling with END stat
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

        // ══════════════════════════════════════════════════════════════════════
        // MYTHIC SKILLS  (4 SP — ultimate power, rarest and most expensive)
        // ══════════════════════════════════════════════════════════════════════

        // wither_skull: UNBLOCKABLE damage (bypasses all mitigation, only second_life survives)
        // lv1: 4 dmg → lv10: 9 dmg
        m.put("wither_skull", new DuelSkillDef(4, 5, 3, DuelTargetType.ENEMY_SINGLE,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int dmg = 4 + level / 2;
                    session.applyRawDamage(target, tIdx, dmg);
                    return session.petName(actor, aIdx) + " fires a wither skull at "
                            + session.petName(target, tIdx) + "! " + dmg + " UNBLOCKABLE dmg!";
                }));

        // ★ spirit_burst: ★ TEAM SP RESTORE — rally all allies, restore SP to each
        // lv1-4: +1 SP to self only | lv5+: +1 SP to all alive allies | lv8+: +2 SP to all
        m.put("spirit_burst", new DuelSkillDef(2, 4, 2, DuelTargetType.ALL_ALLIES,
                (session, actor, aIdx, target, tIdx, level) -> {
                    int spGain = level >= 8 ? 2 : 1;
                    // Self gets SP immediately (current turn)
                    session.currentSP = Math.min(DuelSession.SP_MAX, session.currentSP + spGain);
                    // Allies (lv5+): queue SP into their stored pool for their next turns
                    int alliesBuffed = 0;
                    if (level >= 5) {
                        for (int i = 0; i < 3; i++) {
                            if (i == aIdx) continue;
                            if (!session.isAlive(actor, i)) continue;
                            session.addStoredSP(actor, i, spGain);
                            alliesBuffed++;
                        }
                    }
                    String msg = session.petName(actor, aIdx) + " releases a spirit burst! +"
                            + spGain + " SP";
                    if (alliesBuffed > 0) msg += " to " + (alliesBuffed + 1) + " allies!";
                    else msg += "!";
                    return msg;
                }));

        // second_life: PASSIVE — not manually usable (handled in DuelSession.applyRawDamage)
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
