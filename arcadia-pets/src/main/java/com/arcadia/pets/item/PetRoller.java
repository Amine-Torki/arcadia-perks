package com.arcadia.pets.item;

import com.arcadia.pets.config.PetPoolConfig;
import com.arcadia.pets.skill.PetSkill;
import com.arcadia.pets.skill.PetSkills;
import com.arcadia.pets.skill.SkillInstance;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/**
 * Static utility class for rolling random pets with weighted rarity and stat distributions.
 * Mob pools, stat floors, and star weights are all driven by {@link PetPoolConfig}.
 */
public final class PetRoller {

    private PetRoller() {
    }

    /**
     * Rolls a complete PetData with the given minimum rarity.
     */
    public static PetData roll(PetRarity minimumRarity) {
        PetRarity rarity = PetRarity.rollRarity(minimumRarity);
        String mobType = rollMob(rarity);
        EnumMap<PetStat, Integer> stats = rollStats(rarity);
        List<SkillInstance> skills = rollSkills(mobType, rarity);

        return new PetData(
                UUID.randomUUID(),
                mobType,
                rarity,
                stats,
                false,
                null,
                100,
                100,
                skills
        );
    }

    /**
     * Picks a random mob from the config-driven pool for the given rarity.
     */
    public static String rollMob(PetRarity rarity) {
        List<String> pool = PetPoolConfig.getPool(rarity);
        RandomSource random = RandomSource.create();
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * Rolls stats for each PetStat using weighted distribution based on rarity's stat floor.
     */
    public static EnumMap<PetStat, Integer> rollStats(PetRarity rarity) {
        EnumMap<PetStat, Integer> stats = new EnumMap<>(PetStat.class);
        int floor = rarity.getStatFloor();
        for (PetStat stat : PetStat.values()) {
            stats.put(stat, rollSingle(floor));
        }
        return stats;
    }

    /**
     * Rolls a single stat value (1-5) using the weighted system.
     * For each star level below the floor, its weight is redistributed upward.
     *
     * @param floor the minimum star floor from the rarity
     * @return a star value between 1 and 5
     */
    public static int rollSingle(int floor) {
        int[] weights = PetPoolConfig.CACHED_STAR_WEIGHTS.clone();

        // Redistribute weights from below floor upward
        if (floor > 1) {
            int redistributed = 0;
            for (int i = 0; i < floor - 1 && i < weights.length; i++) {
                redistributed += weights[i];
                weights[i] = 0;
            }
            // Distribute evenly among remaining star levels
            int remaining = weights.length - (floor - 1);
            if (remaining > 0) {
                int perLevel = redistributed / remaining;
                int leftover = redistributed % remaining;
                for (int i = floor - 1; i < weights.length; i++) {
                    weights[i] += perLevel;
                    if (leftover > 0) {
                        weights[i]++;
                        leftover--;
                    }
                }
            }
        }

        int totalWeight = 0;
        for (int w : weights) {
            totalWeight += w;
        }

        RandomSource random = RandomSource.create();
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return i + 1; // Star values are 1-indexed
            }
        }

        return floor; // Fallback
    }

    /**
     * Rolls skills for a pet based on its species and rarity.
     */
    public static List<SkillInstance> rollSkills(String mobType, PetRarity rarity) {
        List<SkillInstance> skills = new ArrayList<>();
        
        // 1. Assign Unique Skill
        PetSkill unique = PetSkills.getUniqueForSpecies(mobType);
        if (unique != null) {
            skills.add(new SkillInstance(unique, 1, 1.0f));
        }
        

        // 3. Determine random slots
        int randomSlots = 0;
        if (rarity == PetRarity.RARE || rarity == PetRarity.EPIC) randomSlots = 1;
        else if (rarity == PetRarity.LEGENDARY) randomSlots = 2;
        else if (rarity == PetRarity.MYTHIC) randomSlots = 1; // Mythics have 2 unique + 1 random later

        if (randomSlots > 0) {
            List<PetSkill> pool = new ArrayList<>(PetSkills.getAll());
            // Remove unique from pool to avoid duplicates
            if (unique != null) pool.remove(unique);
            
            RandomSource random = RandomSource.create();
            for (int i = 0; i < randomSlots && !pool.isEmpty(); i++) {
                PetSkill rolled = pool.get(random.nextInt(pool.size()));
                skills.add(new SkillInstance(rolled, 0, 0.5f)); // locked until previous skill hits Lv 10
                pool.remove(rolled); // Avoid duplicate random skills
            }
        }

        return skills;
    }
}
