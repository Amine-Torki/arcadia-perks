package com.arcadia.pets.item;

import com.arcadia.pets.skill.PetSkill;
import com.arcadia.pets.skill.PetSkills;
import com.arcadia.pets.skill.SkillInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

public record PetData(
        UUID petId,
        String mobType,
        PetRarity rarity,
        EnumMap<PetStat, Integer> stats,
        boolean modifierApplied,
        String customName,
        int hunger,
        int happiness,
        List<SkillInstance> skills
) {

    public PetData(UUID petId, String mobType, PetRarity rarity, EnumMap<PetStat, Integer> stats, boolean modifierApplied, String customName, int hunger, int happiness) {
        this(petId, mobType, rarity, stats, modifierApplied, customName, hunger, happiness, new ArrayList<>());
    }

    private static final String TAG_PET_ID          = "PetId";
    private static final String TAG_MOB_TYPE        = "MobType";
    private static final String TAG_RARITY          = "Rarity";
    private static final String TAG_MODIFIER_APPLIED = "ModifierApplied";
    private static final String TAG_CUSTOM_NAME     = "CustomName";

    /**
     * Serializes this PetData into a flat CompoundTag (no nested CompoundTag or ListTag).
     * Stats are stored as individual bytes (S_POW, S_END, …) and skills as a single
     * packed string ("id|level|eff|xp,…"), eliminating recursive copy() allocations.
     */
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_PET_ID, petId);
        tag.putString(TAG_MOB_TYPE, mobType);
        tag.putByte(TAG_RARITY, (byte) rarity.ordinal());
        tag.putBoolean(TAG_MODIFIER_APPLIED, modifierApplied);
        tag.putString(TAG_CUSTOM_NAME, customName != null ? customName : "");
        tag.putByte("Hunger", (byte) hunger);
        tag.putByte("Happiness", (byte) happiness);
        for (PetStat stat : PetStat.values()) {
            tag.putByte("S_" + stat.name(), stats.getOrDefault(stat, 0).byteValue());
        }
        if (!skills.isEmpty()) tag.putString("Sk", packSkills(skills));
        return tag;
    }

    /**
     * Deserializes a PetData from the flat CompoundTag format written by {@link #toTag()}.
     */
    public static PetData fromTag(CompoundTag tag) {
        UUID id       = tag.getUUID(TAG_PET_ID);
        String mob    = tag.getString(TAG_MOB_TYPE);
        PetRarity rar = PetRarity.fromId(tag.getByte(TAG_RARITY) & 0xFF);
        boolean mod   = tag.getBoolean(TAG_MODIFIER_APPLIED);
        String name   = tag.getString(TAG_CUSTOM_NAME);
        if (name.isEmpty()) name = null;

        EnumMap<PetStat, Integer> statsMap = new EnumMap<>(PetStat.class);
        for (PetStat stat : PetStat.values()) {
            statsMap.put(stat, tag.getByte("S_" + stat.name()) & 0xFF);
        }

        int hunger    = tag.contains("Hunger")    ? (tag.getByte("Hunger")    & 0xFF) : 100;
        int happiness = tag.contains("Happiness") ? (tag.getByte("Happiness") & 0xFF) : 100;

        List<SkillInstance> skills = tag.contains("Sk")
                ? unpackSkills(tag.getString("Sk"))
                : new ArrayList<>();

        return new PetData(id, mob, rar, statsMap, mod, name, hunger, happiness, skills);
    }

    // ── Skill packing helpers ─────────────────────────────────────────────────

    /** Packs skills into a single string: "id|level|eff|xp,id|level|eff|xp,…" */
    private static String packSkills(List<SkillInstance> skills) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < skills.size(); i++) {
            if (i > 0) sb.append(',');
            SkillInstance s = skills.get(i);
            sb.append(s.skill().getId()).append('|')
              .append(s.level()).append('|')
              .append(s.effectiveness()).append('|')
              .append(s.xp());
        }
        return sb.toString();
    }

    /** Unpacks skills from the string produced by {@link #packSkills}. */
    private static List<SkillInstance> unpackSkills(String packed) {
        List<SkillInstance> list = new ArrayList<>();
        if (packed.isEmpty()) return list;
        for (String entry : packed.split(",")) {
            try {
                String[] f = entry.split("\\|");
                if (f.length < 2) {
                    com.mojang.logging.LogUtils.getLogger()
                            .warn("[PetData] Skipping malformed skill entry: '{}'", entry);
                    continue;
                }
                PetSkill skill = PetSkills.get(f[0]);
                if (skill == null) {
                    com.mojang.logging.LogUtils.getLogger()
                            .warn("[PetData] Unknown skill id '{}' — skipping", f[0]);
                    continue;
                }
                int   level = Integer.parseInt(f[1]);
                float eff   = f.length > 2 ? Float.parseFloat(f[2]) : 1.0f;
                int   xp    = f.length > 3 ? Integer.parseInt(f[3]) : 0;
                list.add(new SkillInstance(skill, level, eff, xp));
            } catch (NumberFormatException e) {
                com.mojang.logging.LogUtils.getLogger()
                        .warn("[PetData] Failed to parse skill entry '{}': {}", entry, e.getMessage());
            }
        }
        return list;
    }

    /**
     * Checks if any skill at level 0 should be revealed (previous skill reached Lv 10)
     * and returns a new PetData with those skills advanced to level 1. Returns {@code this}
     * if nothing changed.
     */
    public PetData withUnlockedSkills() {
        List<SkillInstance> updated = new ArrayList<>(skills);
        boolean changed = false;
        for (int i = 1; i < updated.size(); i++) {
            if (updated.get(i).level() == 0 && updated.get(i - 1).level() >= 10) {
                SkillInstance s = updated.get(i);
                updated.set(i, new SkillInstance(s.skill(), 1, s.effectiveness()));
                changed = true;
            }
        }
        if (!changed) return this;
        return new PetData(petId, mobType, rarity, stats, modifierApplied, customName,
                hunger, happiness, updated);
    }

    /**
     * Returns the sum of all stat star values.
     */
    public int totalStars() {
        int total = 0;
        for (int value : stats.values()) {
            total += value;
        }
        return total;
    }

    /**
     * Applies this PetData to an ItemStack via the CUSTOM_DATA component.
     */
    public void applyToStack(ItemStack stack) {
        CompoundTag tag = toTag();
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * Reads PetData from an ItemStack's CUSTOM_DATA component.
     * Returns null if no data is present.
     */
    public static PetData fromStack(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(TAG_PET_ID)) return null;
        return fromTag(customData.getUnsafe());
    }

    /**
     * Builds tooltip lines for this pet, including rarity, stats, and total stars.
     */
    public List<Component> buildTooltip() {
        List<Component> lines = new ArrayList<>();

        // Rarity line
        lines.add(rarity.getStyledName());

        // Mob type line
        String mobName = mobType;
        if (mobName.contains(":")) {
            mobName = mobName.substring(mobName.indexOf(':') + 1);
        }
        mobName = mobName.substring(0, 1).toUpperCase() + mobName.substring(1).replace('_', ' ');
        lines.add(Component.translatable("arcadia_prestige.gui.pet.type_label", mobName).withStyle(ChatFormatting.GRAY));

        // Custom name if present
        if (customName != null && !customName.isEmpty()) {
            lines.add(Component.translatable("arcadia_prestige.gui.pet.name_tooltip", customName).withStyle(ChatFormatting.AQUA));
        }

        // Gene bars: 2 compact lines (POW/END/AGI then WIT/CHM/LCK)
        // Uses filled █ / empty ░ blocks, color-coded by star count
        lines.add(Component.empty());
        PetStat[] statOrder = PetStat.values();
        for (int lineNum = 0; lineNum < 2; lineNum++) {
            MutableComponent barLine = Component.empty();
            for (int col = 0; col < 3; col++) {
                int idx = lineNum * 3 + col;
                if (idx >= statOrder.length) continue;
                PetStat stat  = statOrder[idx];
                int     stars = stats.getOrDefault(stat, 0);
                if (col > 0) barLine.append(Component.literal("  "));
                barLine.append(Component.literal(stat.getIcon() + " ").withStyle(ChatFormatting.GRAY));
                ChatFormatting barColor = stars >= 5 ? ChatFormatting.AQUA
                        : stars >= 4 ? ChatFormatting.GREEN
                        : stars >= 3 ? ChatFormatting.YELLOW
                        : stars >= 2 ? ChatFormatting.GOLD
                        :              ChatFormatting.RED;
                for (int s = 1; s <= 5; s++) {
                    barLine.append(Component.literal(s <= stars ? "\u2588" : "\u2591")
                            .withStyle(s <= stars ? barColor : ChatFormatting.DARK_GRAY));
                }
            }
            lines.add(barLine);
        }

        // Skills — one line each with a styled [Lv X] badge; locked skills show as "???"
        if (!skills.isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.translatable("arcadia_prestige.gui.pet.skills_label")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            for (SkillInstance instance : skills) {
                MutableComponent skillLine;
                if (instance.level() == 0) {
                    // Locked — identity hidden until previous skill reaches Lv 10
                    skillLine = Component.literal("\uD83D\uDD12 ").withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.literal("???").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(" [Locked]").withStyle(ChatFormatting.DARK_RED))
                            .append(Component.literal(" — Max previous skill to reveal").withStyle(ChatFormatting.DARK_GRAY));
                } else {
                    PetSkill skill = instance.skill();
                    ChatFormatting badgeColor = instance.level() >= 10 ? ChatFormatting.GOLD
                            : instance.level() >= 7 ? ChatFormatting.LIGHT_PURPLE
                            : instance.level() >= 4 ? ChatFormatting.BLUE
                            :                         ChatFormatting.DARK_BLUE;
                    skillLine = Component.literal("\u2605 ").withStyle(ChatFormatting.YELLOW)
                            .append(skill.getDisplayName().copy().withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" [Lv " + instance.level() + "]").withStyle(badgeColor))
                            .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(skill.getDescription(instance.level(), instance.effectiveness())
                                    .copy().withStyle(ChatFormatting.GRAY));
                    lines.add(skillLine);
                    // Compact XP bar (only if not max level)
                    if (instance.level() < 10) {
                        int needed = SkillInstance.xpForNextLevel(instance.level());
                        int filled = needed > 0 ? Math.min(5, instance.xp() * 5 / needed) : 0;
                        MutableComponent bar = Component.literal("  ");
                        for (int b = 0; b < 5; b++) {
                            bar.append(Component.literal(b < filled ? "\u2593" : "\u2591")
                                    .withStyle(b < filled ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
                        }
                        bar.append(Component.literal(" " + instance.xp() + "/" + needed + " XP")
                                .withStyle(ChatFormatting.DARK_GRAY));
                        lines.add(bar);
                    }
                    continue;
                }
                lines.add(skillLine);
            }
        }

        return lines;
    }
}
