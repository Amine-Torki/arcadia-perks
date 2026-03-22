package com.arcadia.pets.skill;

import net.minecraft.nbt.CompoundTag;

/**
 * Represents a specific skill assigned to a pet.
 */
public record SkillInstance(
        PetSkill skill,
        int level,
        float effectiveness,
        /** XP toward the NEXT level. Resets on level-up. 0 when level == 0 (locked). */
        int xp
) {
    /** Convenience constructor — xp defaults to 0. */
    public SkillInstance(PetSkill skill, int level, float effectiveness) {
        this(skill, level, effectiveness, 0);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", skill.getId());
        tag.putInt("Level", level);
        tag.putFloat("Effect", effectiveness);
        if (xp > 0) tag.putInt("Xp", xp);
        return tag;
    }

    public static SkillInstance fromTag(CompoundTag tag) {
        String id = tag.getString("Id");
        PetSkill skill = PetSkills.get(id);
        if (skill == null) return null;
        int level = tag.getInt("Level");
        float effect = tag.contains("Effect") ? tag.getFloat("Effect") : 1.0f;
        int xp = tag.contains("Xp") ? tag.getInt("Xp") : 0;
        return new SkillInstance(skill, level, effect, xp);
    }

    /** XP cost to go from the current level to the next: 50 × current level.
     *  Lv1→2 = 50 XP (5 treats), Lv2→3 = 100 XP (10 treats), …, Lv9→10 = 450 XP (45 treats). */
    public static int xpForNextLevel(int currentLevel) {
        return 50 * currentLevel;
    }
}
