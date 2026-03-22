package com.arcadia.pets.skill;

import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetStat;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Base class for all pet skills.
 * Skills are singletons registered in PetSkills.
 */
public abstract class PetSkill {

    /** Skill category — determines which gene stat boosts this skill's effectiveness. */
    public enum SkillType {
        DAMAGE(PetStat.POWER),
        DEFENSE(PetStat.ENDURANCE),
        MOVEMENT(PetStat.AGILITY),
        SUPPORT(PetStat.WIT),
        LUCK(PetStat.LUCK);

        private final PetStat linkedStat;
        SkillType(PetStat stat) { this.linkedStat = stat; }
        public PetStat getLinkedStat() { return linkedStat; }
    }

    private final String id;
    private final String translationKey;

    public PetSkill(String id) {
        this.id = id;
        this.translationKey = "arcadia_prestige.skill." + id;
    }

    public String getId() {
        return id;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey + ".name");
    }

    /** Returns this skill's type category (DAMAGE, DEFENSE, MOVEMENT, SUPPORT, LUCK). */
    public abstract SkillType getSkillType();

    public Component getDescription(int level, float effectiveness) {
        return Component.translatable(translationKey + ".desc", getFormattedValue(level, effectiveness));
    }

    /**
     * Returns the raw value for a given level (1-10).
     * Subclasses should define the scaling logic.
     */
    public abstract float getRawValue(int level);

    /**
     * Returns the value for a given level, adjusted by effectiveness (usually 0.5 or 1.0).
     */
    public float getValue(int level, float effectiveness) {
        return getRawValue(level) * effectiveness;
    }

    /**
     * Optional: Format the value for the description (e.g., "5%" or "2.0 HP").
     */
    public String getFormattedValue(int level, float effectiveness) {
        float val = getValue(level, effectiveness);
        if (val == (long) val) return String.format("%d", (long) val);
        return String.format("%.1f", val);
    }

    // --- Activation message ---

    /**
     * Sends "Your &lt;pet name&gt; used &lt;skill&gt;!" to the owner's chat.
     * Pet name falls back to the mob type's translated name if no custom name is set.
     */
    protected void notifyUsed(Player owner, PetData petData, String detail) {
        if (!(owner instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        String petName = petData.customName();
        if (petName == null || petName.isEmpty()) {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.parse(petData.mobType());
            petName = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(rl)
                    .map(t -> t.getDescription().getString())
                    .orElse(petData.mobType());
        }
        sp.sendSystemMessage(Component.translatable("arcadia_prestige.msg.skill_used", petName, getDisplayName(), detail)
                .withStyle(net.minecraft.ChatFormatting.AQUA));
    }

    // --- Hooks ---

    /**
     * Whether this skill's {@link #onTick} must run every game tick (true) or can be
     * throttled to every 20 ticks / 1 second (false, default). Override to {@code true}
     * only for time-sensitive logic like fall detection or input-triggered abilities.
     */
    public boolean isPerTick() { return false; }

    public void onTick(Player owner, PetData petData, int level, float effectiveness) {}

    /**
     * Called when the client detects nearby hostile mobs and triggers the aura check.
     * Entity-scanning skills should override this instead of {@link #onTick} to offload
     * the proximity detection to the client, keeping the server tick lean.
     * <p>Override {@link #isAuraTick()} to {@code true} alongside this method so the
     * client can skip the proximity scan when no aura skill is present.</p>
     */
    public void onAuraTick(Player owner, PetData petData, int level, float effectiveness) {}

    /** Returns {@code true} if this skill uses the {@link #onAuraTick} path. Default: false. */
    public boolean isAuraTick() { return false; }

    public void onOwnerHurt(LivingDamageEvent.Pre event, Player owner, PetData petData, int level, float effectiveness) {}

    public void onOwnerAttack(LivingDamageEvent.Pre event, Player owner, PetData petData, int level, float effectiveness) {}

    public void onOwnerKill(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event, Player owner, PetData petData, int level, float effectiveness) {}

    public void onBlockBreak(BlockEvent.BreakEvent event, Player owner, PetData petData, int level, float effectiveness) {}

    public void onAftershock(Player owner, PetData petData, int level, float effectiveness, LivingEntity target) {}

    public void onSummon(Player owner, PetData petData, int level, float effectiveness) {}

    public void onRecall(Player owner, PetData petData, int level, float effectiveness) {}

    // ── Cooldown tracking (optional per-skill) ────────────────────────────────

    /**
     * Key in {@code player.getPersistentData()} where this skill stores the
     * last-use timestamp as {@code System.currentTimeMillis()}.
     * Returns {@code null} if this skill has no cooldown.
     */
    public String getCooldownPersistentKey() { return null; }

    /**
     * Total cooldown duration in <em>milliseconds</em> for the given level.
     * Returns {@code 0} if this skill has no cooldown.
     */
    public long getCooldownMs(int level) { return 0L; }
}
