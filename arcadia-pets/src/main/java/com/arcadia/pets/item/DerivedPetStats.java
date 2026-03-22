package com.arcadia.pets.item;

/**
 * Computes the six derived combat stats from a pet's gene stars.
 *
 * <pre>
 *  ATK    = 1 + POW               — base bonus damage in ATTACK mode
 *  DEF%   = END × 8               — damage reduction in DEFEND mode
 *  HP     = 5 + END × 5           — max health points
 *  CRIT%  = AGI×3 + LCK×4        — critical hit chance
 *  EVA%   = AGI×2 + WIT×5        — dodge/evasion chance
 *  SPD    = 0.22 + AGI×0.04      — movement speed attribute
 * </pre>
 */
public final class DerivedPetStats {

    public final int   atk;     // bonus damage added in ATTACK mode
    public final int   defPct;  // % damage absorbed in DEFEND mode
    public final int   hp;      // max HP
    public final int   critPct; // crit chance %
    public final int   evaPct;  // evasion / dodge %
    public final float spd;     // movement speed (raw Minecraft attribute value)

    public DerivedPetStats(PetData data) {
        int pow = data.stats().getOrDefault(PetStat.POWER,     1);
        int end = data.stats().getOrDefault(PetStat.ENDURANCE, 1);
        int agi = data.stats().getOrDefault(PetStat.AGILITY,   1);
        int wit = data.stats().getOrDefault(PetStat.WIT,       1);
        int lck = data.stats().getOrDefault(PetStat.LUCK,      1);

        atk     = 1 + pow;
        defPct  = end * 8;
        hp      = 5 + end * 5;
        critPct = agi * 3 + lck * 4;
        evaPct  = agi * 2 + wit * 5;
        spd     = 0.22f + agi * 0.04f;
    }
}
