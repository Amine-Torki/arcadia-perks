package com.arcadia.pets.skill;

import com.arcadia.pets.item.PetData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry for all PetSkills.
 */
public class PetSkills {

    private static final Map<String, PetSkill> REGISTRY = new HashMap<>();
    private static final Map<String, PetSkill> UNIQUE_MAPPING = new HashMap<>();

    /** UUIDs of WitherSkull entities shot by this mod — explosion handler clears block list for these. */
    public static final java.util.Set<java.util.UUID> NO_GRIEF_SKULLS =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /** Tracked skulls with expiry — (entity, expire game tick). Cleaned up by PetEventHandler. */
    public static final java.util.List<java.util.Map.Entry<net.minecraft.world.entity.Entity, Long>> SKULL_EXPIRY =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    // ── Stable attribute modifier IDs ─────────────────────────────────────────
    // Declared here so SkillHandler can iterate them for cleanup without coupling
    // to individual inner skill classes.
    public static final net.minecraft.resources.ResourceLocation ATTR_FOX_SPEED   = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("arcadia_pets", "pet_fox_speed");
    public static final net.minecraft.resources.ResourceLocation ATTR_FROG_JUMP   = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("arcadia_pets", "pet_frog_jump");
    public static final net.minecraft.resources.ResourceLocation ATTR_RABBIT_LUCK = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("arcadia_pets", "pet_rabbit_luck");

    // --- Common Skills ---
    public static final PetSkill FEATHERFALL = register(new FeatherfallSkill());
    public static final PetSkill LUCKY_OINK = register(new LuckyOinkSkill());
    public static final PetSkill STEADY_HEAL = register(new SteadyHealSkill());
    public static final PetSkill WOOLLY_BUFFER = register(new WoollyBufferSkill());
    public static final PetSkill LUCKY_PAW = register(new LuckyPawSkill());
    public static final PetSkill NIGHT_VISION = register(new NightVisionSkill());
    public static final PetSkill QUICK_STEP = register(new QuickStepSkill());
    public static final PetSkill SWEET_STING = register(new SweetStingSkill());

    // --- Uncommon Skills ---
    public static final PetSkill PACK_CALL = register(new PackCallSkill());
    public static final PetSkill AQUATIC_BOND = register(new AquaticBondSkill());
    public static final PetSkill BAMBOO_FORTITUDE = register(new BambooFortitudeSkill());
    public static final PetSkill BOUNDING_LEAP = register(new BoundingLeapSkill());
    public static final PetSkill SHELL_GUARD = register(new ShellGuardSkill());

    // --- Rare Skills ---
    public static final PetSkill WISHFUL_GIFT = register(new WishfulGiftSkill());
    public static final PetSkill ANCIENT_SENSE = register(new AncientSenseSkill());
    public static final PetSkill LAVA_WALKER = register(new LavaWalkerSkill());

    // --- Epic Skills ---
    public static final PetSkill IRON_WILL = register(new IronWillSkill());
    public static final PetSkill VOID_STEP = register(new VoidStepSkill());
    public static final PetSkill FLAME_AURA = register(new FlameAuraSkill());
    public static final PetSkill SOUL_DRAIN = register(new SoulDrainSkill());
    
    // --- Legendary Skills ---
    public static final PetSkill SONIC_SHRIEK = register(new SonicShriekSkill());
    public static final PetSkill DRACONIC_SURGE = register(new DraconicSurgeSkill());
    public static final PetSkill WITHER_AURA = register(new WitherAuraSkill());
    public static final PetSkill WITHER_SKULL = register(new WitherSkullSkill());
    public static final PetSkill FATIGUE_CURSE = register(new FatigueCurseSkill());
    public static final PetSkill GROUND_SLAM = register(new GroundSlamSkill());
    public static final PetSkill WIND_DEFLECT = register(new WindDeflectSkill());
    public static final PetSkill SECOND_LIFE = register(new SecondLifeSkill());

    static {
        // COMMON
        linkUnique("minecraft:chicken", FEATHERFALL);
        linkUnique("minecraft:pig",     LUCKY_OINK);
        linkUnique("minecraft:cow",     STEADY_HEAL);
        linkUnique("minecraft:sheep",   WOOLLY_BUFFER);
        linkUnique("minecraft:rabbit",  LUCKY_PAW);
        linkUnique("minecraft:cat",     NIGHT_VISION);
        linkUnique("minecraft:fox",     QUICK_STEP);
        linkUnique("minecraft:bee",     SWEET_STING);
        
        // UNCOMMON
        linkUnique("minecraft:wolf",    PACK_CALL);
        linkUnique("minecraft:axolotl", AQUATIC_BOND);
        linkUnique("minecraft:panda",   BAMBOO_FORTITUDE);
        linkUnique("minecraft:frog",    BOUNDING_LEAP);
        linkUnique("minecraft:turtle",  SHELL_GUARD);
        
        // RARE
        linkUnique("minecraft:allay",      WISHFUL_GIFT);
        linkUnique("minecraft:sniffer",    ANCIENT_SENSE);
        linkUnique("minecraft:strider",    LAVA_WALKER);
        
        // EPIC
        linkUnique("minecraft:iron_golem", IRON_WILL);
        linkUnique("minecraft:enderman",   VOID_STEP);
        linkUnique("minecraft:blaze",      FLAME_AURA);
        linkUnique("minecraft:wither_skeleton", SOUL_DRAIN);
        
        // LEGENDARY
        linkUnique("minecraft:warden",     SONIC_SHRIEK);
        linkUnique("minecraft:ender_dragon", DRACONIC_SURGE);
        linkUnique("minecraft:wither",     WITHER_SKULL);
        linkUnique("minecraft:elder_guardian", FATIGUE_CURSE);
        linkUnique("minecraft:ravager",    GROUND_SLAM);
        linkUnique("minecraft:breeze",     WIND_DEFLECT);
        linkUnique("minecraft:shulker",    SECOND_LIFE);
    }

    private static PetSkill register(PetSkill skill) {
        REGISTRY.put(skill.getId(), skill);
        return skill;
    }

    private static void linkUnique(String species, PetSkill skill) {
        UNIQUE_MAPPING.put(species, skill);
    }

    public static PetSkill getUniqueForSpecies(String species) {
        return UNIQUE_MAPPING.get(species.contains(":") ? species : "minecraft:" + species);
    }

    public static PetSkill get(String id) {
        return REGISTRY.get(id);
    }

    public static Collection<PetSkill> getAll() {
        return REGISTRY.values();
    }

    // --- Skill Implementations ---

    private static class FeatherfallSkill extends PetSkill {
        public FeatherfallSkill() { super("featherfall"); }
        @Override public SkillType getSkillType() { return SkillType.MOVEMENT; }
        @Override public boolean isPerTick() { return true; } // fall detection needs per-tick
        @Override public float getRawValue(int level) { return level == 10 ? 12 : level; }
        @Override public String getFormattedValue(int level, float effectiveness) {
            return (int)getValue(level, effectiveness) + "s";
        }
        @Override
        public void onTick(Player owner, PetData petData, int level, float effectiveness) {
            if (owner.fallDistance > 2.0f && owner.getDeltaMovement().y < -0.5) {
                int duration = (int)getValue(level, effectiveness);
                owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, duration * 20));
                if (level >= 10 && effectiveness >= 1.0f) {
                    owner.fallDistance = 0; // Immunity
                }
                notifyUsed(owner, petData, "Slow Falling for " + duration + "s");
            }
        }
    }

    private static class LuckyOinkSkill extends PetSkill {
        public LuckyOinkSkill() { super("lucky_oink"); }
        @Override public SkillType getSkillType() { return SkillType.LUCK; }
        @Override public float getRawValue(int level) {
            if (level == 1) return 5;
            if (level == 10) return 15;
            return 5 + (level - 1) * (10f / 9f);
        }
        @Override public String getFormattedValue(int level, float effectiveness) {
             return String.format("%.1f%%", getValue(level, effectiveness)) + (effectiveness >= 1.0f ? " (x2 at Lv10)" : "");
        }
        @Override
        public void onOwnerKill(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event, Player owner, PetData petData, int level, float effectiveness) {
            float chance = getValue(level, effectiveness);
            if (owner.getRandom().nextFloat() * 100 < chance && !event.getDrops().isEmpty()) {
                // Lv10 with full effectiveness: duplicate ALL drops. Otherwise: duplicate one random drop.
                boolean doubleAll = level >= 10 && effectiveness >= 1.0f;
                var drops = new java.util.ArrayList<>(event.getDrops());
                if (doubleAll) {
                    for (var item : drops) {
                        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                                owner.level(), item.getX(), item.getY(), item.getZ(), item.getItem().copy()));
                    }
                    notifyUsed(owner, petData, "Double drop! (" + drops.size() + " items)");
                } else {
                    net.minecraft.world.entity.item.ItemEntity original = drops.get(owner.getRandom().nextInt(drops.size()));
                    event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                            owner.level(), original.getX(), original.getY(), original.getZ(), original.getItem().copy()));
                    notifyUsed(owner, petData, "+1 " + original.getItem().getHoverName().getString());
                }
                if (owner.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    var ref = drops.get(0);
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                            ref.getX(), ref.getY(), ref.getZ(), doubleAll ? 15 : 5, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }
    }

    private static class SteadyHealSkill extends PetSkill {
        public SteadyHealSkill() { super("steady_heal"); }
        @Override public SkillType getSkillType() { return SkillType.SUPPORT; }
        @Override public float getRawValue(int level) {
            if (level == 1) return 0.5f;
            if (level == 10) return 2.0f;
            return 0.5f + (level - 1) * (1.5f / 9f);
        }
        @Override public String getFormattedValue(int level, float effectiveness) {
             return String.format("%.1f HP", getValue(level, effectiveness));
        }
        @Override
        public void onTick(Player owner, PetData petData, int level, float effectiveness) {
            int interval = level >= 10 ? 15 : level >= 5 ? 20 : 30;
            if (owner.tickCount % (interval * 20) == 0) {
                owner.heal(getValue(level, effectiveness));
            }
        }
    }

    private static class WoollyBufferSkill extends PetSkill {
        public WoollyBufferSkill() { super("woolly_buffer"); }
        @Override public SkillType getSkillType() { return SkillType.DEFENSE; }
        @Override public String getCooldownPersistentKey() { return "ArcadiaWoollyLast"; }
        @Override public long getCooldownMs(int level) { return (level >= 10 ? 20L : level >= 5 ? 40L : 60L) * 1000L; }
        @Override public float getRawValue(int level) {
            if (level == 1) return 1;
            if (level == 10) return 4;
            return 1 + (level - 1) * (3f / 9f);
        }
        @Override public String getFormattedValue(int level, float effectiveness) {
            return (int)getValue(level, effectiveness) + " HP";
        }
        @Override
        public void onOwnerHurt(LivingDamageEvent.Pre event, Player owner, PetData petData, int level, float effectiveness) {
            // Check cooldown (stored in a dummy tag or specialized map)
            long lastUse = owner.getPersistentData().getLong("ArcadiaWoollyLast");
            int cooldown = level >= 10 ? 20 : level >= 5 ? 40 : 60;
            if (System.currentTimeMillis() - lastUse > cooldown * 1000L) {
                float absorb = getValue(level, effectiveness);
                event.setNewDamage(Math.max(0, event.getNewDamage() - absorb));
                owner.getPersistentData().putLong("ArcadiaWoollyLast", System.currentTimeMillis());
                notifyUsed(owner, petData, "Absorbed " + String.format("%.1f", absorb) + " damage");
                if (owner.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                            owner.getX(), owner.getY() + 1, owner.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }
    }

    private static class LuckyPawSkill extends PetSkill {
        public LuckyPawSkill() { super("lucky_paw"); }
        @Override public SkillType getSkillType() { return SkillType.LUCK; }
        @Override public float getRawValue(int level) { return level >= 10 ? 3 : level >= 5 ? 2 : 1; }
        @Override public String getFormattedValue(int level, float effectiveness) {
            return "Luck " + (int)getValue(level, effectiveness);
        }
        @Override
        public void onSummon(Player owner, PetData petData, int level, float effectiveness) {
            var attr = owner.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
            if (attr == null) return;
            attr.removeModifier(ATTR_RABBIT_LUCK);
            attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    ATTR_RABBIT_LUCK, getValue(level, effectiveness),
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
        @Override
        public void onRecall(Player owner, PetData petData, int level, float effectiveness) {
            var attr = owner.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
            if (attr != null) attr.removeModifier(ATTR_RABBIT_LUCK);
        }
    }

    private static class NightVisionSkill extends PetSkill {
        public NightVisionSkill() { super("night_vision"); }
        @Override public SkillType getSkillType() { return SkillType.SUPPORT; }
        @Override public float getRawValue(int level) { return level; }
        @Override
        public void onTick(Player owner, PetData petData, int level, float effectiveness) {
            if (owner.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, owner.blockPosition()) < 8) {
                var current = owner.getEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                if (current == null || current.getDuration() < 40) {
                    owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 300, 0, true, false));
                }
            }
        }
    }

    private static class QuickStepSkill extends PetSkill {
        public QuickStepSkill() { super("quick_step"); }
        @Override public SkillType getSkillType() { return SkillType.MOVEMENT; }
        @Override public float getRawValue(int level) { return level >= 10 ? 2 : 1; }
        @Override
        public void onSummon(Player owner, PetData petData, int level, float effectiveness) {
            var attr = owner.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (attr == null) return;
            // 0.02 ≈ Speed I, 0.04 ≈ Speed II (player base is 0.1)
            double value = (level >= 10 ? 0.04 : 0.02) * effectiveness;
            attr.removeModifier(ATTR_FOX_SPEED);
            attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    ATTR_FOX_SPEED, value,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
        @Override
        public void onRecall(Player owner, PetData petData, int level, float effectiveness) {
            var attr = owner.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (attr != null) attr.removeModifier(ATTR_FOX_SPEED);
        }
        @Override
        public void onOwnerHurt(LivingDamageEvent.Pre event, Player owner, PetData petData, int level, float effectiveness) {
            // Level 10 burst: temporary Speed II on top of the permanent attribute
            if (level >= 10) {
                owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 100, 1, false, true));
            }
        }
    }

    private static class SweetStingSkill extends PetSkill {
        public SweetStingSkill() { super("sweet_sting"); }
        @Override public SkillType getSkillType() { return SkillType.DAMAGE; }
        @Override public float getRawValue(int level) { return 15 + (level - 1) * (20f / 9f); }
        @Override public String getFormattedValue(int level, float effectiveness) {
            return String.format("%.1f%%", getValue(level, effectiveness));
        }
        @Override
        public void onOwnerAttack(LivingDamageEvent.Pre event, Player owner, PetData petData, int level, float effectiveness) {
            float chance = getValue(level, effectiveness);
            if (owner.getRandom().nextFloat() * 100 < chance) {
                if (event.getEntity() instanceof net.minecraft.world.entity.LivingEntity victim) {
                    victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 60));
                    if (level >= 10) {
                        victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 40));
                    }
                    notifyUsed(owner, petData, "Poisoned " + victim.getName().getString());
                    // Visual feedback: poison splash particles on the victim
                    if (owner.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                                victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                                10, 0.3, 0.4, 0.3, 0.05);
                        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME,
                                victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                                4, 0.2, 0.2, 0.2, 0.1);
                    }
                }
            }
        }
    }

    // --- Uncommon Skills ---

    private static class PackCallSkill extends PetSkill {
        public PackCallSkill() { super("pack_call"); }
        @Override public SkillType getSkillType() { return SkillType.SUPPORT; }
        @Override public String getCooldownPersistentKey() { return "ArcadiaWolfLast"; }
        @Override public long getCooldownMs(int level) { return (level >= 10 ? 30L : level >= 5 ? 28L : 45L) * 1000L; }
        @Override public float getRawValue(int level) { return level >= 10 ? 3 : level >= 5 ? 2 : 1; }
        @Override
        public void onOwnerHurt(LivingDamageEvent.Pre event, Player owner, PetData petData, int level, float effectiveness) {
            long last = owner.getPersistentData().getLong("ArcadiaWolfLast");
            int cd = level >= 10 ? 30 : level >= 5 ? 28 : 45; // Wait, roadmap says 30 at lvl 10? no, 30 CD.
             if (System.currentTimeMillis() - last > cd * 1000L) {
                 int count = (int)getValue(level, effectiveness);
                 int dur = level >= 10 ? 20 : level >= 5 ? 15 : 10;
                 for (int i = 0; i < count; i++) {
                     net.minecraft.world.entity.animal.Wolf wolf = net.minecraft.world.entity.EntityType.WOLF.create(owner.level());
                     if (wolf != null) {
                         wolf.setTame(true, true);
                         wolf.setOwnerUUID(owner.getUUID());
                         wolf.setPos(owner.getX(), owner.getY(), owner.getZ());
                         owner.level().addFreshEntity(wolf);
                         com.arcadia.pets.server.SkillHandler.registerTemp(wolf, dur * 20);
                     }
                 }
                 owner.getPersistentData().putLong("ArcadiaWolfLast", System.currentTimeMillis());
                 notifyUsed(owner, petData, "Summoned " + count + " wolves for " + dur + "s");
             }
        }
    }

    private static class AquaticBondSkill extends PetSkill {
        public AquaticBondSkill() { super("aquatic_bond"); }
        @Override public SkillType getSkillType() { return SkillType.SUPPORT; }
        @Override public float getRawValue(int level) { return level; }
        @Override
        public void onTick(Player owner, PetData petData, int level, float effectiveness) {
            if (owner.isInWater()) {
                var wb = owner.getEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING);
                if (wb == null || wb.getDuration() < 40) {
                    owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WATER_BREATHING, 160, 0, true, false));
                }
                if (level >= 5) {
                    var dg = owner.getEffect(net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE);
                    if (dg == null || dg.getDuration() < 40) {
                        owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE, 160, 0, true, false));
                    }
                }
            }
        }
    }

    private static class BambooFortitudeSkill extends PetSkill {
        public BambooFortitudeSkill() { super("bamboo_fortitude"); }
        @Override public SkillType getSkillType() { return SkillType.DEFENSE; }
        @Override public String getCooldownPersistentKey() { return "ArcadiaPandaLast"; }
        @Override public long getCooldownMs(int level) { return level >= 5 ? (level >= 10 ? 12L : 30L) * 1000L : 0L; }
        @Override public float getRawValue(int level) { return 2 + (level - 1) * (8f / 9f); }
        @Override
        public void onOwnerHurt(LivingDamageEvent.Pre event, Player owner, PetData petData, int level, float effectiveness) {
            float reduction = getValue(level, effectiveness) / 100f;
            event.setNewDamage(event.getNewDamage() * (1.0f - reduction));
            
            if (level >= 5) {
                long last = owner.getPersistentData().getLong("ArcadiaPandaLast");
                int cd = level >= 10 ? 12 : 30;
                if (System.currentTimeMillis() - last > cd * 1000L) {
                    owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 0));
                    owner.getPersistentData().putLong("ArcadiaPandaLast", System.currentTimeMillis());
                    notifyUsed(owner, petData, "Resistance activated");
                }
            }
        }
    }

    private static class BoundingLeapSkill extends PetSkill {
        public BoundingLeapSkill() { super("bounding_leap"); }
        @Override public SkillType getSkillType() { return SkillType.MOVEMENT; }
        @Override public float getRawValue(int level) { return level >= 5 ? 2 : 1; }
        @Override
        public void onSummon(Player owner, PetData petData, int level, float effectiveness) {
            var attr = owner.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
            if (attr == null) return;
            // Jump Boost I ≈ +0.1, Jump Boost II ≈ +0.2 on base 0.42
            double value = (level >= 5 ? 0.2 : 0.1) * effectiveness;
            attr.removeModifier(ATTR_FROG_JUMP);
            attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    ATTR_FROG_JUMP, value,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
        @Override
        public void onRecall(Player owner, PetData petData, int level, float effectiveness) {
            var attr = owner.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
            if (attr != null) attr.removeModifier(ATTR_FROG_JUMP);
        }
        @Override
        public void onOwnerHurt(LivingDamageEvent.Pre event, Player owner, PetData petData, int level, float effectiveness) {
             if (level >= 10 && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
                 event.setNewDamage(event.getNewDamage() * 0.25f);
             }
        }
    }

    private static class ShellGuardSkill extends PetSkill {
        public ShellGuardSkill() { super("shell_guard"); }
        @Override public SkillType getSkillType() { return SkillType.DEFENSE; }
        @Override public boolean isPerTick() { return true; } // low-health trigger needs responsiveness
        @Override public String getCooldownPersistentKey() { return "ArcadiaTurtleLast"; }
        @Override public long getCooldownMs(int level) { return (level >= 10 ? 15L : level >= 5 ? 30L : 60L) * 1000L; }
        @Override public float getRawValue(int level) { return level; }
        @Override
        public void onTick(Player owner, PetData petData, int level, float effectiveness) {
            float healthLimit = level >= 10 ? 10f : level >= 5 ? 6f : 4f;
            if (owner.getHealth() <= healthLimit) {
                long last = owner.getPersistentData().getLong("ArcadiaTurtleLast");
                int cd = level >= 10 ? 15 : level >= 5 ? 30 : 60;
                if (System.currentTimeMillis() - last > cd * 1000L) {
                    owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 160, 1));
                    if (level >= 10) owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 80, 0));
                    owner.getPersistentData().putLong("ArcadiaTurtleLast", System.currentTimeMillis());
                    notifyUsed(owner, petData, "Emergency shield activated");
                }
            }
        }
    }

    // --- Rare Skills ---

    private static class WishfulGiftSkill extends PetSkill {
        public WishfulGiftSkill() { super("wishful_gift"); }
        @Override public SkillType getSkillType() { return SkillType.LUCK; }
        @Override public float getRawValue(int level) { return level >= 10 ? 3 : level >= 5 ? 6 : 10; }
        @Override
        public void onTick(Player owner, PetData petData, int level, float effectiveness) {
            int intervalMins = (int)getValue(level, effectiveness);
            // Divided by 20 because onTick is throttled to every 20 ticks
            if (owner.tickCount > 0 && owner.getRandom().nextInt(intervalMins * 60) == 0) {
                net.minecraft.world.item.Item[] pool = level >= 10 ? new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.ENDER_PEARL, net.minecraft.world.item.Items.GHAST_TEAR, net.minecraft.world.item.Items.DIAMOND} :
                                                      level >= 5 ? new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.BREAD, net.minecraft.world.item.Items.IRON_INGOT, net.minecraft.world.item.Items.GOLD_INGOT} :
                                                      new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.ARROW, net.minecraft.world.item.Items.BONE, net.minecraft.world.item.Items.STRING};
                net.minecraft.world.item.Item drop = pool[owner.getRandom().nextInt(pool.length)];
                owner.spawnAtLocation(drop);
                notifyUsed(owner, petData, "Dropped " + new net.minecraft.world.item.ItemStack(drop).getHoverName().getString());
            }
        }
    }

    private static class AncientSenseSkill extends PetSkill {
        public AncientSenseSkill() { super("ancient_sense"); }
        @Override public SkillType getSkillType() { return SkillType.LUCK; }
        @Override public float getRawValue(int level) { return level >= 10 ? 3f : level >= 5 ? 1.5f : 0.5f; }
        @Override
        public void onBlockBreak(BlockEvent.BreakEvent event, Player owner, PetData petData, int level, float effectiveness) {
             float chance = getValue(level, effectiveness);
             if (event.getState().is(net.minecraft.tags.BlockTags.COAL_ORES) || event.getState().is(net.minecraft.tags.BlockTags.IRON_ORES) || event.getState().is(net.minecraft.tags.BlockTags.GOLD_ORES) || event.getState().is(net.minecraft.tags.BlockTags.DIAMOND_ORES)) {
                 chance *= 3;
             }
             if (owner.getRandom().nextFloat() * 100 < chance) {
                 net.minecraft.world.item.Item item = event.getState().getBlock().asItem();
                 if (item != net.minecraft.world.item.Items.AIR) {
                     event.getLevel().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity((net.minecraft.world.level.Level)event.getLevel(), event.getPos().getX(), event.getPos().getY(), event.getPos().getZ(), new net.minecraft.world.item.ItemStack(item)));
                     notifyUsed(owner, petData, "+1 " + new net.minecraft.world.item.ItemStack(item).getHoverName().getString());
                 }
             }
             if (level >= 10 && owner.getRandom().nextFloat() * 100 < 0.2f) {
                 event.getLevel().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity((net.minecraft.world.level.Level)event.getLevel(), event.getPos().getX(), event.getPos().getY(), event.getPos().getZ(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ANCIENT_DEBRIS)));
             }
        }
    }

    private static class LavaWalkerSkill extends PetSkill {
        public LavaWalkerSkill() { super("lava_walker"); }
        @Override public SkillType getSkillType() { return SkillType.MOVEMENT; }
        @Override public String getCooldownPersistentKey() { return "ArcadiaStriderLast"; }
        @Override public long getCooldownMs(int level) { return level >= 10 ? 0L : 30_000L; }
        @Override public float getRawValue(int level) { return level >= 10 ? 1 : 0; }
        @Override
        public void onTick(Player owner, PetData petData, int level, float effectiveness) {
            if (level >= 10) {
                var fr = owner.getEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
                if (fr == null || fr.getDuration() < 40) {
                    owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 160, 0, true, false));
                }
                if (owner.isInLava()) owner.setDeltaMovement(owner.getDeltaMovement().add(0, 0.1, 0));
            } else {
                if (owner.isInLava() || owner.isOnFire()) {
                    long last = owner.getPersistentData().getLong("ArcadiaStriderLast");
                    if (System.currentTimeMillis() - last > 30000L) {
                        owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, level >= 5 ? 400 : 100, 0));
                        owner.getPersistentData().putLong("ArcadiaStriderLast", System.currentTimeMillis());
                        notifyUsed(owner, petData, "Fire Resistance for " + (level >= 5 ? 20 : 5) + "s");
                    }
                }
            }
        }
    }

    // --- Epic Skills ---

    private static class IronWillSkill extends PetSkill {
        public IronWillSkill() { super("iron_will"); }
        @Override public SkillType getSkillType() { return SkillType.DAMAGE; }
        @Override public float getRawValue(int level) { return level >= 10 ? 25 : level >= 5 ? 15 : 5; }
        @Override
        public void onOwnerHurt(LivingDamageEvent.Pre event, Player owner, PetData petData, int level, float effectiveness) {
            if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                attacker.hurt(owner.level().damageSources().thorns(owner), event.getNewDamage() * (getValue(level, effectiveness) / 100f));
                if (level >= 10) attacker.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                else if (level >= 5) attacker.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                notifyUsed(owner, petData, "Reflected " + String.format("%.1f", event.getNewDamage() * (getValue(level, effectiveness) / 100f)) + " thorns damage");
            }
        }
    }

    private static class VoidStepSkill extends PetSkill {
        public VoidStepSkill() { super("void_step"); }
        @Override public SkillType getSkillType() { return SkillType.MOVEMENT; }
        @Override public float getRawValue(int level) { return level >= 10 ? 50 : level >= 5 ? 30 : 10; }
        @Override
        public void onOwnerHurt(LivingDamageEvent.Pre event, Player owner, PetData petData, int level, float effectiveness) {
             if (owner.getRandom().nextFloat() * 100 < getValue(level, effectiveness)) {
                 int dist = level >= 10 ? 16 : level >= 5 ? 12 : 8;
                 double tx = owner.getX() + (owner.getRandom().nextDouble() - 0.5D) * dist;
                 double ty = owner.getY() + (owner.getRandom().nextInt(dist) - dist/2D);
                 double tz = owner.getZ() + (owner.getRandom().nextDouble() - 0.5D) * dist;
                 owner.randomTeleport(tx, ty, tz, true);
                 notifyUsed(owner, petData, "Teleported to safety");
             }
        }
    }

    private static class FlameAuraSkill extends PetSkill {
        public FlameAuraSkill() { super("flame_aura"); }
        @Override public SkillType getSkillType() { return SkillType.DAMAGE; }
        @Override public float getRawValue(int level) { return level >= 10 ? 6 : level >= 5 ? 4 : 2; }
        @Override
        public void onOwnerHurt(LivingDamageEvent.Pre event, Player owner, PetData petData, int level, float effectiveness) {
            if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                attacker.setRemainingFireTicks((int)getValue(level, effectiveness) * 20);
                if (level >= 5) attacker.hurt(owner.level().damageSources().onFire(), 0.5f);
                notifyUsed(owner, petData, "Ignited " + attacker.getName().getString() + " for " + (int)getValue(level, effectiveness) + "s");
                if (level >= 10) {
                    owner.level().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(3)).forEach(e -> {
                        if (e != owner && !e.isAlliedTo(owner)) e.setRemainingFireTicks(120);
                    });
                }
            }
        }
    }

    private static class SoulDrainSkill extends PetSkill {
        public SoulDrainSkill() { super("soul_drain"); }
        @Override public SkillType getSkillType() { return SkillType.DAMAGE; }
        @Override public boolean isAuraTick() { return true; }
        @Override public float getRawValue(int level) { return level >= 10 ? 25 : level >= 5 ? 16 : 8; }
        @Override
        public void onOwnerAttack(LivingDamageEvent.Pre event, Player owner, PetData petData, int level, float effectiveness) {
            if (owner.getRandom().nextFloat() * 100 < getValue(level, effectiveness)) {
                if (event.getEntity() instanceof LivingEntity victim) {
                    victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, level >= 10 ? 80 : level >= 5 ? 100 : 60, level >= 10 ? 1 : 0));
                    float healAmt = level >= 10 ? 1.0f : level >= 5 ? 0.5f : 0f;
                    owner.heal(healAmt);
                    notifyUsed(owner, petData, "Drained " + victim.getName().getString() + (healAmt > 0 ? ", healed " + String.format("%.1f", healAmt) + " HP" : ""));
                }
            }
        }
        @Override
        public void onAuraTick(Player owner, PetData petData, int level, float effectiveness) {
            int range = level >= 10 ? 4 : level >= 5 ? 3 : 2;
            java.util.List<LivingEntity> targets = owner.level().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(range));
            int affected = 0;
            for (LivingEntity e : targets) {
                if (e == owner || e instanceof Player || !(e instanceof net.minecraft.world.entity.monster.Enemy)) continue;
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 60, 0));
                affected++;
                if (owner.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH,
                            e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 4, 0.2, 0.3, 0.2, 0.02);
                }
            }
            if (affected > 0) {
                float healPerMob = level >= 10 ? 0.5f : level >= 5 ? 0.3f : 0.2f;
                owner.heal(affected * healPerMob);
            }
        }
    }

    // --- Legendary Skills ---

    private static class SonicShriekSkill extends PetSkill {
        public SonicShriekSkill() { super("sonic_shriek"); }
        @Override public SkillType getSkillType() { return SkillType.DAMAGE; }
        @Override public float getRawValue(int level) { return level >= 10 ? 8 : level >= 5 ? 5 : 2; }
        @Override
        public void onAftershock(Player owner, PetData petData, int level, float effectiveness, LivingEntity target) {
            target.hurt(owner.level().damageSources().sonicBoom(owner), getValue(level, effectiveness));
            int range = level >= 10 ? 7 : level >= 5 ? 5 : 3;
            net.minecraft.world.phys.Vec3 dir = target.position().subtract(owner.position()).normalize();
            target.push(dir.x * range * 0.1, 0.2, dir.z * range * 0.1);
            if (level >= 10) target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 80));
            else if (level >= 5) target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 40));
            if (owner.level() instanceof net.minecraft.server.level.ServerLevel sl) sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + 1, target.getZ(), 1, 0, 0, 0, 0);
            notifyUsed(owner, petData, String.format("%.1f", getValue(level, effectiveness)) + " sonic damage to " + target.getName().getString());
        }
    }

    private static class DraconicSurgeSkill extends PetSkill {
        public DraconicSurgeSkill() { super("draconic_surge"); }
        @Override public SkillType getSkillType() { return SkillType.MOVEMENT; }
        @Override public boolean isPerTick() { return true; } // flight state + input detection
        @Override public String getCooldownPersistentKey() { return "ArcadiaDragonLast"; }
        // Cooldown (refresh): Lv10 = 30min, Lv5+ = 45min, Lv1-4 = 1h
        @Override public long getCooldownMs(int level) { return (level >= 10 ? 1800L : level >= 5 ? 2700L : 3600L) * 1000L; }
        // Flight pool in seconds: Lv10 = 15min, Lv5+ = 10min, Lv1-4 = 5min
        @Override public float getRawValue(int level) { return level >= 10 ? 900 : level >= 5 ? 600 : 300; }
        @Override public String getFormattedValue(int level, float effectiveness) {
            int secs = (int) getValue(level, effectiveness);
            return secs >= 60 ? (secs / 60) + "m" : secs + "s";
        }
        @Override
        public void onTick(Player owner, PetData petData, int level, float effectiveness) {
            long last = owner.getPersistentData().getLong("ArcadiaDragonLast");
            long cdMs = getCooldownMs(level);
            if (owner.isShiftKeyDown() && owner.swinging && System.currentTimeMillis() - last > cdMs) {
                owner.getPersistentData().putLong("ArcadiaDragonLast", System.currentTimeMillis());
                if (owner.level() instanceof net.minecraft.server.level.ServerLevel sl) sl.sendParticles(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH, owner.getX(), owner.getY(), owner.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                int poolSecs = (int) getValue(level, effectiveness);
                notifyUsed(owner, petData, "Flight enabled for " + (poolSecs >= 60 ? (poolSecs / 60) + "m" : poolSecs + "s"));
            }
            long timeSinceStart = System.currentTimeMillis() - owner.getPersistentData().getLong("ArcadiaDragonLast");
            if (timeSinceStart < getValue(level, effectiveness) * 1000L) {
                if (!owner.getAbilities().mayfly) {
                    owner.getAbilities().mayfly = true;
                    owner.onUpdateAbilities();
                }
            } else {
                if (owner.getAbilities().mayfly && !owner.isCreative() && !owner.isSpectator()) {
                    owner.getAbilities().mayfly = false;
                    owner.getAbilities().flying = false;
                    owner.onUpdateAbilities();
                }
            }
        }
    }

    private static class WitherAuraSkill extends PetSkill {
        public WitherAuraSkill() { super("wither_aura"); }
        @Override public SkillType getSkillType() { return SkillType.DAMAGE; }
        @Override public boolean isAuraTick() { return true; }
        /** Returns the aura range in blocks (3 / 5 / 7). */
        @Override public float getRawValue(int level) { return level >= 10 ? 7 : level >= 5 ? 5 : 3; }
        @Override public String getFormattedValue(int level, float effectiveness) {
            return "Range " + (int) getValue(level, effectiveness);
        }
        @Override
        public void onAuraTick(Player owner, PetData petData, int level, float effectiveness) {
            int range = (int) getValue(level, effectiveness);
            java.util.List<LivingEntity> targets = owner.level().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(range));
            boolean hit = false;
            for (LivingEntity e : targets) {
                if (e == owner || e instanceof Player || !(e instanceof net.minecraft.world.entity.monster.Enemy)) continue;
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, level >= 10 ? 100 : level >= 5 ? 80 : 60, 0));
                hit = true;
                if (owner.level() instanceof net.minecraft.server.level.ServerLevel slh) {
                    slh.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH,
                            e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
                }
            }
            if (owner.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                for (int a = 0; a < 8; a++) {
                    double dx = range * Math.cos(Math.toRadians(a * 45));
                    double dz = range * Math.sin(Math.toRadians(a * 45));
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH,
                            owner.getX() + dx, owner.getY() + 0.5, owner.getZ() + dz, 1, 0.1, 0.2, 0.1, 0.0);
                }
            }
        }
        @Override
        public void onOwnerKill(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event, Player owner, PetData petData, int level, float effectiveness) {
            if (level >= 10 && event.getEntity().hasEffect(net.minecraft.world.effect.MobEffects.WITHER)) {
                net.minecraft.world.item.ItemStack drop = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE);
                event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(owner.level(), event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), drop));
            }
        }
    }

    private static class FatigueCurseSkill extends PetSkill {
        public FatigueCurseSkill() { super("fatigue_curse"); }
        @Override public SkillType getSkillType() { return SkillType.DAMAGE; }
        @Override public float getRawValue(int level) { return level >= 10 ? 7 : level >= 5 ? 5 : 3; }
        @Override
        public void onOwnerAttack(LivingDamageEvent.Pre event, Player owner, PetData petData, int level, float effectiveness) {
            if (event.getEntity() instanceof LivingEntity victim) {
                int dur = (int)getValue(level, effectiveness) * 20;
                victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, dur, level >= 5 ? 1 : 0));
                victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, dur, level >= 10 ? 1 : 0));
                if (level >= 10) victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, dur, 0));
                notifyUsed(owner, petData, "Cursed " + victim.getName().getString() + " for " + (dur / 20) + "s");
            }
        }
    }

    private static class GroundSlamSkill extends PetSkill {
        public GroundSlamSkill() { super("ground_slam"); }
        @Override public SkillType getSkillType() { return SkillType.DAMAGE; }
        @Override public float getRawValue(int level) { return level >= 10 ? 6 : level >= 5 ? 5 : 4; }
        @Override
        public void onAftershock(Player owner, PetData petData, int level, float effectiveness, LivingEntity target) {
            if (!(owner.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;

            float power = level >= 10 ? 6.0f : level >= 5 ? 5.0f : 4.0f;

            // Manual AoE: damage + knockback on non-player enemies — no sl.explode() so players are safe
            double cx = target.getX(), cy = target.getY(), cz = target.getZ();
            sl.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(cx, cy, cz, cx, cy, cz).inflate(power + 1f)).forEach(e -> {
                if (e instanceof Player) return;           // never hurt any player
                if (e.isAlliedTo(owner)) return;
                double dist = e.distanceTo(target);
                if (dist > power + 1f) return;
                // Scale damage by inverse distance (full at dist=0, 0 at dist=power)
                float dmg = (float)(getValue(level, effectiveness) * (1.0 - dist / (power + 1f)));
                e.hurt(sl.damageSources().explosion(null, owner), dmg);
                // Knockback away from explosion centre
                net.minecraft.world.phys.Vec3 kb = e.position().subtract(cx, cy, cz).normalize()
                        .scale(power * 0.08);
                e.push(kb.x, 0.3, kb.z);
                int duration  = level >= 10 ? 120 : level >= 5 ? 80 : 60;
                int amplifier = level >= 10 ? 1 : 0;
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, duration, amplifier));
            });

            // Shockwave particles + block-break visuals
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                    cx, cy + 0.5, cz, 1, 0, 0, 0, 0);
            notifyUsed(owner, petData, "Shockwave (power " + (int)power + ")");

            // Block-break sound/visual ring (client-only, no world change)
            int range = level >= 10 ? 3 : level >= 5 ? 2 : 1;
            net.minecraft.core.BlockPos base = target.blockPosition().below();
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    net.minecraft.core.BlockPos pos = base.offset(dx, 0, dz);
                    net.minecraft.world.level.block.state.BlockState state = sl.getBlockState(pos);
                    if (!state.isAir()) {
                        sl.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
                    }
                }
            }
        }
    }

    private static class WindDeflectSkill extends PetSkill {
        public WindDeflectSkill() { super("wind_deflect"); }
        @Override public SkillType getSkillType() { return SkillType.DEFENSE; }
        @Override public float getRawValue(int level) { return level >= 10 ? 40 : level >= 5 ? 28 : 15; }
        @Override
        public void onOwnerHurt(LivingDamageEvent.Pre event, Player owner, PetData petData, int level, float effectiveness) {
            if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
                if (owner.getRandom().nextFloat() * 100 < getValue(level, effectiveness)) {
                    event.setNewDamage(0);
                    notifyUsed(owner, petData, "Deflected projectile");
                    if (level >= 5 && owner.getRandom().nextFloat() * 100 < (level >= 10 ? 22 : 12)) {
                        Entity projectile = event.getSource().getDirectEntity();
                        if (projectile != null) {
                            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1f));
                            owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                        }
                    }
                }
            }
        }
    }

    private static class WitherSkullSkill extends PetSkill {
        public WitherSkullSkill() { super("wither_skull"); }
        @Override public SkillType getSkillType() { return SkillType.DAMAGE; }
        @Override public float getRawValue(int level) { return level >= 10 ? 12 : level >= 5 ? 8 : 5; }
        @Override public String getFormattedValue(int level, float effectiveness) {
            return (int)getValue(level, effectiveness) + " dmg";
        }
        @Override
        public void onAftershock(Player owner, PetData petData, int level, float effectiveness, LivingEntity target) {
            if (!(owner.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
            double ox = owner.getX(), oy = owner.getY() + owner.getBbHeight() * 0.5, oz = owner.getZ();
            double tx = target.getX(), ty = target.getY() + target.getBbHeight() * 0.5, tz = target.getZ();
            net.minecraft.world.entity.projectile.WitherSkull skull =
                    new net.minecraft.world.entity.projectile.WitherSkull(sl, owner,
                            new net.minecraft.world.phys.Vec3(tx - ox, ty - oy, tz - oz));
            skull.setPos(ox, oy, oz);
            skull.setDangerous(level >= 10); // charged at level 10
            NO_GRIEF_SKULLS.add(skull.getUUID());
            sl.addFreshEntity(skull);
            // 3-second lifetime to prevent infinite travel exploits
            SKULL_EXPIRY.add(java.util.Map.entry(skull, sl.getGameTime() + 60));
            notifyUsed(owner, petData, (level >= 10 ? "Charged skull" : "Skull") + " fired at " + target.getName().getString());
        }
    }

    private static class SecondLifeSkill extends PetSkill {
        public SecondLifeSkill() { super("second_life"); }
        @Override public SkillType getSkillType() { return SkillType.DEFENSE; }
        @Override public String getCooldownPersistentKey() { return "ArcadiaShulkerLast"; }
        @Override public long getCooldownMs(int level) { return (long) getRawValue(level) * 1000L; }
        @Override public float getRawValue(int level) { return level >= 10 ? 300 : level >= 5 ? 420 : 600; }
        @Override
        public void onOwnerHurt(LivingDamageEvent.Pre event, Player owner, PetData petData, int level, float effectiveness) {
            if (owner.getHealth() - event.getNewDamage() <= 0) {
                long last = owner.getPersistentData().getLong("ArcadiaShulkerLast");
                long cd = (long)getValue(level, effectiveness);
                if (System.currentTimeMillis() - last > cd * 1000L) {
                    event.setNewDamage(0);
                    owner.setHealth(level >= 10 ? 3f : level >= 5 ? 2f : 1f);
                    int dist = level >= 10 ? 12 : level >= 5 ? 10 : 5;
                    owner.randomTeleport(owner.getX() + owner.getRandom().nextInt(dist*2)-dist, owner.getY(), owner.getZ() + owner.getRandom().nextInt(dist*2)-dist, false);
                    if (level >= 10) owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 160, 2));
                    else if (level >= 5) owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 100, 1));
                    owner.getPersistentData().putLong("ArcadiaShulkerLast", System.currentTimeMillis());
                    owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), net.minecraft.sounds.SoundEvents.TOTEM_USE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                    notifyUsed(owner, petData, "Saved you from death!");
                }
            }
        }
    }
}
