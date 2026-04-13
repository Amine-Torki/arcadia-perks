package com.arcadia.pets.item;

/**
 * Computes the six derived combat stats from a pet's gene stars.
 *
 * <pre>
 *  ATK    = ATK_BASE + POW                        — base bonus damage
 *  DEF%   = END × END_TO_DEF_PCT                  — damage reduction in DEFEND mode
 *  HP     = HP_BASE + END × END_TO_HP             — max health points
 *  CRIT%  = AGI × AGI_TO_CRIT_PCT + LCK × LCK_TO_CRIT_PCT
 *  EVA%   = AGI × AGI_TO_EVA_PCT  + WIT × WIT_TO_EVA_PCT
 *  SPD    = SPD_BASE + AGI × AGI_TO_SPD           — movement speed attribute
 * </pre>
 *
 * Tune the constants below to adjust game balance without touching formulas.
 */
public final class DerivedPetStats {

    // ── Balance constants ─────────────────────────────────────────────────────
    public static final int   ATK_BASE         = 1;
    public static final int   END_TO_DEF_PCT   = 8;
    public static final int   HP_BASE          = 5;
    public static final int   END_TO_HP        = 5;
    public static final int   AGI_TO_CRIT_PCT  = 3;
    public static final int   LCK_TO_CRIT_PCT  = 4;
    public static final int   AGI_TO_EVA_PCT   = 2;
    public static final int   WIT_TO_EVA_PCT   = 5;
    public static final float SPD_BASE         = 0.22f;
    public static final float AGI_TO_SPD       = 0.04f;

    // ── Derived fields ────────────────────────────────────────────────────────
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

        atk     = ATK_BASE        + pow;
        defPct  = END_TO_DEF_PCT  * end;
        hp      = HP_BASE         + END_TO_HP       * end;
        critPct = AGI_TO_CRIT_PCT * agi + LCK_TO_CRIT_PCT * lck;
        evaPct  = AGI_TO_EVA_PCT  * agi + WIT_TO_EVA_PCT  * wit;
        spd     = SPD_BASE        + AGI_TO_SPD      * agi;
    }
}
