package com.arcadia.pets.server;

import com.arcadia.lib.DebugMode;
import com.arcadia.pets.PetsModItems;
import com.arcadia.pets.item.PetBehaviourMode;
import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetItem;
import com.arcadia.pets.item.PetMovementMode;
import com.arcadia.pets.item.PetStat;
import com.arcadia.pets.item.StarEssenceItem;
import com.arcadia.pets.network.C2SPetAction;
import com.arcadia.pets.network.S2CPetPanel;
import com.arcadia.pets.network.S2CPetHpSync;
import com.arcadia.pets.network.S2CPocketPet;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages summoned pet entities for players. Handles spawning, despawning, HP
 * scaling, movement/behaviour modes, the death-cooldown system (per-pet), and
 * the panel feed action. All state is server-side.
 */
public final class PetManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ── Passive player-stat bonus modifier IDs ────────────────────────────────
    private static final ResourceLocation MOD_POW = ResourceLocation.fromNamespaceAndPath("arcadia_pets", "pet_pow");
    private static final ResourceLocation MOD_END = ResourceLocation.fromNamespaceAndPath("arcadia_pets", "pet_end");
    private static final ResourceLocation MOD_AGI = ResourceLocation.fromNamespaceAndPath("arcadia_pets", "pet_agi");
    private static final ResourceLocation MOD_WIT = ResourceLocation.fromNamespaceAndPath("arcadia_pets", "pet_wit");
    private static final ResourceLocation MOD_LCK = ResourceLocation.fromNamespaceAndPath("arcadia_pets", "pet_lck");

    /**
     * Mob types that cannot be used in Follow mode (Brain-based entities that cause server load).
     * Mirror of PetScreen.FOLLOW_BLACKLIST - add new entries here when a species causes issues.
     */
    private static final java.util.Set<String> FOLLOW_BLACKLIST = java.util.Set.of(
            "minecraft:warden",
            "minecraft:ender_dragon",
            "minecraft:shulker",
            "minecraft:breeze",
            "minecraft:elder_guardian",
            "minecraft:dolphin",
            "minecraft:wither"
    );

    /**
     * Mob types forced to pocket mode only (their custom renderer ignores Attributes.SCALE
     * so they appear huge as real entities even in Sit mode).
     */
    private static final java.util.Set<String> POCKET_ONLY = java.util.Set.of(
            "minecraft:ender_dragon"
    );

    // -- Designated pet (the "active" selection — not necessarily equipped) -
    /** playerUuid → petUuid of the player's chosen active pet. Persisted to NBT. */
    private static final Map<UUID, UUID> designatedPetId = new ConcurrentHashMap<>();

    // -- Equipped pet state (summoned — effects/modes apply) -
    private static final Map<UUID, Entity>  activePets    = new ConcurrentHashMap<>();
    private static final Map<UUID, PetData> activePetData = new ConcurrentHashMap<>();

    // -- Death cooldown  - keyed by PET UUID so other pets stay summonable
    private static final Map<UUID, Long> deathCooldowns = new ConcurrentHashMap<>();
    /** Tracks which pet UUID last died for each player, used to reduce cooldown via treats. */
    private static final Map<UUID, UUID> lastDeadPet    = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS_DEBUG = 10_000L;         // 10 seconds
    private static final long COOLDOWN_MS_PROD  = 10 * 60_000L;   // 10 minutes

    // -- Stored HP  - keys by pet UUID, persists across recalls within a session -
    // First summon: no entry -> full HP. Recall -> re-summon: restores last known HP.
    // Resets on server restart (acceptable  - pets regenerate over time eventually).
    private static final Map<UUID, Float> storedPetHp = new ConcurrentHashMap<>();

    // -- Pocket mode -- no server entity; purely client-side rendering 
    private static final Map<UUID, PetData> pocketPets = new ConcurrentHashMap<>();
    private static final Map<UUID, Float>   pocketHp   = new ConcurrentHashMap<>();

    // -- Movement / behaviour modes  - keyed by player UUID
    private static final Map<UUID, PetMovementMode>  petMovement  = new ConcurrentHashMap<>();
    private static final Map<UUID, PetBehaviourMode> petBehaviour = new ConcurrentHashMap<>();

    // -- Skill toggles  - keyed by player UUID → set of disabled skill IDs
    private static final Map<UUID, java.util.Set<String>> disabledSkills = new ConcurrentHashMap<>();

    private PetManager() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Summons a pet entity for the given player based on {@link PetData}.
     * Any previously active pet is despawned first. HP scales with Vitality.
     */
    public static void summon(ServerPlayer player, PetData data) {
        despawn(player);

        PetMovementMode requestedMove = petMovement.getOrDefault(player.getUUID(), PetMovementMode.POCKET);

        // Mobs whose renderer ignores SCALE must always use pocket mode (no Follow, no Sit).
        if (POCKET_ONLY.contains(data.mobType())) {
            petMovement.put(player.getUUID(), PetMovementMode.POCKET);
            activatePocketMode(player, data);
            return;
        }

        // Some mobs are blacklisted from Follow mode (Brain-based entities that cause server load).
        // Force pocket mode silently - the UI already grays the button out.
        if (FOLLOW_BLACKLIST.contains(data.mobType()) && requestedMove == PetMovementMode.FOLLOW) {
            petMovement.put(player.getUUID(), PetMovementMode.POCKET);
            activatePocketMode(player, data);
            return;
        }

        // If the player prefers pocket mode, skip entity creation entirely.
        if (requestedMove == PetMovementMode.POCKET) {
            activatePocketMode(player, data);
            return;
        }

        ResourceLocation entityRL = ResourceLocation.parse(data.mobType());
        Optional<EntityType<?>> typeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(entityRL);
        if (typeOpt.isEmpty()) {
            LOGGER.warn("PetManager: unknown entity type '{}' for player {}", data.mobType(), player.getName().getString());
            return;
        }

        EntityType<?> type = typeOpt.get();
        Entity pet = type.create(player.serverLevel());
        if (pet == null) {
            LOGGER.warn("PetManager: failed to create entity '{}' for player {}", data.mobType(), player.getName().getString());
            return;
        }

        pet.setPos(player.getX() + 1.5, player.getY(), player.getZ() + 1.5);
        // Mark so orphaned entities can be cleaned up on server start after a crash.
        pet.getPersistentData().putBoolean("arcadia_pet", true);

        if (pet instanceof Mob mob) {
            mob.setInvulnerable(true);
            mob.setPersistenceRequired();
            mob.setNoAi(false);

            // Scale (visual size)  - bigger the higher the vitality
            AttributeInstance scaleAttr = mob.getAttribute(Attributes.SCALE);
            if (scaleAttr != null) {
                double sizeNorm = getMobSizeScale(data.mobType());
                double baseScale = (0.25 + data.stats().getOrDefault(PetStat.ENDURANCE, 0) * 0.10) * sizeNorm;
                
                // Global Size Cap: Prevent pets from exceeding ~1.8 blocks visually
                float baseWidth = mob.getType().getDimensions().width();
                float baseHeight = mob.getType().getDimensions().height();
                double maxDim = Math.max(baseWidth, baseHeight);
                if (maxDim * baseScale > 1.8) {
                    baseScale = 1.8 / maxDim;
                }
                
                scaleAttr.setBaseValue(baseScale);
            }

            // Movement speed from SPD stat
            AttributeInstance speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.setBaseValue(0.22 + data.stats().getOrDefault(PetStat.AGILITY, 0) * 0.04);
            }

            // HP scales with Vitality: 36 HP (1*) -> 100 HP (5*).
            // Restore last known HP (from recall)  - only heal to full on first ever summon.
            AttributeInstance hpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
            if (hpAttr != null) {
                int vitality = data.stats().getOrDefault(PetStat.ENDURANCE, 1);
                hpAttr.setBaseValue(5.0 + vitality * 5.0);
                float restore = storedPetHp.getOrDefault(data.petId(), -1f);
                mob.setHealth(restore > 0 ? Math.min(restore, mob.getMaxHealth()) : mob.getMaxHealth());
                sendHpSync(player, mob.getHealth(), mob.getMaxHealth(), true, data);
            }

            // Remove all default AI goals  - we control behaviour
            mob.goalSelector.removeAllGoals(g -> true);
            mob.targetSelector.removeAllGoals(g -> true);

            if (data.customName() != null && !data.customName().isEmpty()) {
                mob.setCustomName(Component.literal(data.customName()).withStyle(ChatFormatting.YELLOW));
                mob.setCustomNameVisible(true);
            }

            // Apply movement/behaviour modes (defaulting to FOLLOW + IDLE if not set)
            applyModes(player, mob);

            // Suppress vanilla Brain behaviors that would remove/bury the pet
            suppressSpecialBehaviors(mob);
        }

        player.serverLevel().addFreshEntity(pet);
        activePets.put(player.getUUID(), pet);
        activePetData.put(player.getUUID(), data);
        designatedPetId.put(player.getUUID(), data.petId());
        SkillHandler.triggerSummon(player);
        applyPetStatBonuses(player, data);
    }

    // ── Passive player-stat bonuses ───────────────────────────────────────────

    /**
     * Applies passive player-stat bonuses based on the pet's gene stats.
     * POW → attack damage, END → max HP, AGI → movement speed,
     * WIT → attack speed, LCK → luck. Each star = +2% (×0.02 multiplied base).
     */
    private static void applyPetStatBonuses(ServerPlayer player, PetData data) {
        Map<PetStat, Integer> s = data.stats();
        applyMod(player, Attributes.ATTACK_DAMAGE,  MOD_POW, s.getOrDefault(PetStat.POWER,     0) * 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        applyMod(player, Attributes.MAX_HEALTH,     MOD_END, s.getOrDefault(PetStat.ENDURANCE, 0) * 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        applyMod(player, Attributes.MOVEMENT_SPEED, MOD_AGI, s.getOrDefault(PetStat.AGILITY,   0) * 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        applyMod(player, Attributes.ATTACK_SPEED,   MOD_WIT, s.getOrDefault(PetStat.WIT,       0) * 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        applyMod(player, Attributes.LUCK,           MOD_LCK, s.getOrDefault(PetStat.LUCK,      0) * 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static void applyMod(ServerPlayer player,
                                  net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                  ResourceLocation id, double value,
                                  AttributeModifier.Operation op) {
        if (value == 0) return;
        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null) inst.addOrReplacePermanentModifier(new AttributeModifier(id, value, op));
    }

    /** Removes all passive pet-stat bonuses from the player. */
    private static void removePetStatBonuses(ServerPlayer player) {
        removeMod(player, Attributes.ATTACK_DAMAGE,  MOD_POW);
        removeMod(player, Attributes.MAX_HEALTH,     MOD_END);
        removeMod(player, Attributes.MOVEMENT_SPEED, MOD_AGI);
        removeMod(player, Attributes.ATTACK_SPEED,   MOD_WIT);
        removeMod(player, Attributes.LUCK,           MOD_LCK);
    }

    private static void removeMod(ServerPlayer player,
                                   net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                   ResourceLocation id) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }

    /**
     * Despawns and discards any active pet for the given player.
     */
    public static void despawn(ServerPlayer player) {
        removePetStatBonuses(player);
        SkillHandler.triggerRecall(player);
        UUID playerUuid = player.getUUID();
        Entity pet = activePets.remove(playerUuid);
        PetData data = activePetData.remove(playerUuid);
        if (pet instanceof LivingEntity le && data != null) {
            storedPetHp.put(data.petId(), le.getHealth());
        }
        if (pet instanceof Mob mob && mob.isAlive()) {
            // Clear pocket mode on all tracking clients before discarding
            S2CPocketPet clear = S2CPocketPet.recall(playerUuid);
            PacketDistributor.sendToPlayersTrackingEntity(mob, clear);
            PacketDistributor.sendToPlayer(player, clear);
            mob.discard();
        }
        // Also clear if pet was in pocket mode (no entity)
        if (pocketPets.containsKey(playerUuid)) {
            PetData pd = pocketPets.remove(playerUuid);
            if (pd != null) {
                float maxHp = 5.0f + pd.stats().getOrDefault(PetStat.ENDURANCE, 1) * 5.0f;
                storedPetHp.put(pd.petId(), pocketHp.getOrDefault(pd.petId(), maxHp));
            }
            S2CPocketPet recall = S2CPocketPet.recall(playerUuid);
            broadcastToNearby(player, recall);
            PacketDistributor.sendToPlayer(player, recall);
        }
        // Notify client that no pet is active so the HUD widget hides
        sendHpSync(player, 0f, 0f, false);
    }

    public static boolean hasActivePet(UUID playerUuid) {
        return activePets.containsKey(playerUuid);
    }

    /** All currently summoned pet entities (excludes pocket-mode pets). Used for periodic brain refresh. */
    public static java.util.stream.Stream<Mob> getAllActivePetEntities() {
        return activePets.values().stream()
                .filter(e -> e instanceof Mob && e.isAlive())
                .map(e -> (Mob) e);
    }

    public static boolean hasActivePet(Player player) {
        return hasActivePet(player.getUUID());
    }

    public static PetData getActivePetData(UUID playerUuid) {
        return activePetData.get(playerUuid);
    }

    public static PetData getActivePetData(Player player) {
        return getActivePetData(player.getUUID());
    }

    public static void forceUpdateActivePetData(UUID playerUuid, PetData data) {
        activePetData.put(playerUuid, data);
    }

    public static Entity getActivePet(UUID playerUuid) {
        return activePets.get(playerUuid);
    }

    // =========================================================================
    // Event handlers
    // =========================================================================

    /** Called via ArcadiaModRegistry server action from prestige's ParticleScheduler. */
    public static void handlePlayerLogout(ServerPlayer sp) {
        onPlayerLogout_internal(sp);
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            onPlayerLogout_internal(sp);
        }
    }

    private static void onPlayerLogout_internal(ServerPlayer sp) {
        UUID pid = sp.getUUID();
        UUID designated = designatedPetId.get(pid);
        if (designated != null) {
            sp.getPersistentData().putUUID("arcadia_active_pet", designated);
        } else {
            sp.getPersistentData().remove("arcadia_active_pet");
        }
        PetData active = activePetData.get(pid);
        PetData pocket = pocketPets.get(pid);
        PetData equipped = active != null ? active : pocket;
        if (equipped != null) {
            sp.getPersistentData().putUUID("arcadia_last_pet", equipped.petId());
        } else {
            sp.getPersistentData().remove("arcadia_last_pet");
        }
        despawn(sp);
        designatedPetId.remove(pid);
        petMovement.remove(pid);
        petBehaviour.remove(pid);
        disabledSkills.remove(pid);
    }

    // =========================================================================
    // Designated ("active") pet — selection independent of equipped state
    // =========================================================================

    /**
     * Sets the designated pet and unsummons any currently equipped pet if different.
     * Use the UUID-only overload only during login restoration (before the entity exists).
     */
    public static void setDesignatedPet(ServerPlayer player, UUID petId) {
        UUID playerUuid = player.getUUID();
        UUID prev = designatedPetId.get(playerUuid);
        designatedPetId.put(playerUuid, petId);
        // If changing to a different pet, unsummon whatever is currently equipped
        if (prev != null && !prev.equals(petId)) {
            PetData activeDat = activePetData.get(playerUuid);
            PetData pocket = pocketPets.get(playerUuid);
            if (activeDat != null || pocket != null) {
                despawn(player);
            }
        }
    }

    /** UUID-only overload for login restoration (no entity to despawn yet). */
    public static void setDesignatedPet(UUID playerUuid, UUID petId) {
        designatedPetId.put(playerUuid, petId);
    }

    public static UUID getDesignatedPetId(UUID playerUuid) {
        return designatedPetId.get(playerUuid);
    }

    public static void clearDesignatedPet(UUID playerUuid, UUID petId) {
        designatedPetId.remove(playerUuid, petId);
        // Also persist the cleared state so it doesn't restore on relog
        // (persisted NBT is written on logout, so in-memory removal is enough)
    }

    /**
     * Finds the pet stack for {@code petId} in the player's inventory first,
     * then falls back to the server-side collection.
     */
    public static ItemStack findPetStackAnywhere(ServerPlayer player, UUID petId) {
        ItemStack inv = findPetStack(player, petId);
        if (!inv.isEmpty()) return inv;
        if (player.getServer() == null) return ItemStack.EMPTY;
        return PetCollectionSavedData.getOrCreate(player.getServer()).findStack(player.getUUID(), petId);
    }

    // =========================================================================
    // Inventory-driven helpers
    // =========================================================================

    public static void summonFromInventory(ServerPlayer player, int slotIndex) {
        ItemStack stack = player.getInventory().getItem(slotIndex);
        if (!(stack.getItem() instanceof PetItem)) return;
        PetData data = PetData.fromStack(stack);
        if (data == null) return;
        summon(player, data);
    }

    public static boolean applyStarEssence(ServerPlayer player, int petSlotIndex) {
        ItemStack petStack = player.getInventory().getItem(petSlotIndex);
        if (!(petStack.getItem() instanceof PetItem)) return false;
        PetData data = PetData.fromStack(petStack);
        if (data == null || data.modifierApplied()) return false;

        ItemStack essenceStack = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack c = player.getInventory().getItem(i);
            if (!c.isEmpty() && c.getItem() == PetsModItems.STAR_ESSENCE.get()) { essenceStack = c; break; }
        }
        if (essenceStack.isEmpty()) {
            player.sendSystemMessage(Component.translatable("arcadia_pets.msg.pet_no_essence").withStyle(ChatFormatting.RED));
            return false;
        }
        boolean applied = StarEssenceItem.applyToPet(petStack, essenceStack);
        if (applied) {
            player.sendSystemMessage(Component.translatable("arcadia_pets.msg.pet_star_upgraded").withStyle(ChatFormatting.GOLD));
        }
        return applied;
    }

    // =========================================================================
    // Rename handling
    // =========================================================================

    public static void handleRename(ServerPlayer player, UUID petId, String rawName) {
        String name = rawName == null ? "" : rawName.strip();
        if (name.length() > 20) name = name.substring(0, 20);
        String finalName = name.isEmpty() ? null : name;

        ItemStack s = findPetStack(player, petId);
        if (!s.isEmpty()) {
            PetData d = PetData.fromStack(s);
            if (d == null) return;

            PetData updated = new PetData(d.petId(), d.mobType(), d.rarity(), d.stats(),
                    d.modifierApplied(), finalName, d.hunger(), d.happiness(),
                    d.skills());
            updated.applyToStack(s);
            if (player.getServer() != null) PetCollectionSavedData.getOrCreate(player.getServer()).setDirty();

            // Keep in-memory PetData in sync so mode switches carry the new name
            UUID playerUuid = player.getUUID();
            PetData active = activePetData.get(playerUuid);
            if (active != null && active.petId().equals(petId)) {
                activePetData.put(playerUuid, updated);
            }
            PetData pocket = pocketPets.get(playerUuid);
            if (pocket != null && pocket.petId().equals(petId)) {
                pocketPets.put(playerUuid, updated);
                // Push updated name to all clients rendering the pocket pet
                float vitality = updated.stats().getOrDefault(PetStat.ENDURANCE, 0);
                float sizeNorm = getMobSizeScale(updated.mobType());
                float scale = (0.25f + vitality * 0.10f) * sizeNorm;
                String newName = (finalName != null) ? finalName : formatMobType(updated.mobType());
                S2CPocketPet refresh = new S2CPocketPet(playerUuid, updated.mobType(), scale, newName);
                broadcastToNearby(player, refresh);
                PacketDistributor.sendToPlayer(player, refresh);
            }

            // Update the live mob entity's nameplate if it's currently summoned
            Entity pet = activePets.get(player.getUUID());
            if (pet instanceof Mob mob) {
                if (finalName != null) {
                    mob.setCustomName(Component.literal(finalName).withStyle(ChatFormatting.YELLOW));
                    mob.setCustomNameVisible(true);
                } else {
                    mob.setCustomName(null);
                    mob.setCustomNameVisible(false);
                }
            }

            // Refresh the pet panel so the client sees the new name immediately
            openPanelFor(player, s);

            Component msg = finalName != null
                    ? Component.translatable("arcadia_pets.msg.pet_renamed", finalName)
                    : Component.translatable("arcadia_pets.msg.pet_name_cleared");
            player.sendSystemMessage(msg.copy().withStyle(ChatFormatting.GREEN));
            return;
        }
    }

    // =========================================================================
    // Death handling
    // =========================================================================

    public static void onPetDeath(Mob mob) {
        for (var iter = activePets.entrySet().iterator(); iter.hasNext(); ) {
            var entry = iter.next();
            if (entry.getValue().getId() == mob.getId()) {
                UUID playerUuid = entry.getKey();
                iter.remove();
                PetData data = activePetData.remove(playerUuid);

                // Cooldown is keyed by PET UUID  - so the player can still summon other pets
                if (data != null) {
                    long cooldownMs = getDeathCooldownMs(data);
                    deathCooldowns.put(data.petId(), System.currentTimeMillis() + cooldownMs);
                    lastDeadPet.put(playerUuid, data.petId());
                    storedPetHp.remove(data.petId()); // dead pet starts fresh when it recovers
                }

                if (mob.level() instanceof ServerLevel sl) {
                    ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(playerUuid);
                    if (sp != null && data != null) {
                        updatePetItemOnDeath(sp, data);
                        sendHpSync(sp, 0f, 0f, false);
                        sp.sendSystemMessage(Component.translatable("arcadia_pets.msg.pet_died")
                                .withStyle(ChatFormatting.RED));
                    }
                }
                return;
            }
        }
    }

    public static void drainHunger(ServerPlayer player, java.util.UUID petId, int amount) {
        updatePetItem(player, petId, d -> {
            int prev = d.hunger();
            int next = Math.max(0, prev - amount);
            if (prev > 0 && next == 0)
                player.sendSystemMessage(net.minecraft.network.chat.Component
                        .translatable("arcadia_pets.msg.pet_starving")
                        .withStyle(net.minecraft.ChatFormatting.RED));
            else if (prev > 25 && next <= 25)
                player.sendSystemMessage(net.minecraft.network.chat.Component
                        .translatable("arcadia_pets.msg.pet_hungry", next)
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
            return new PetData(d.petId(), d.mobType(), d.rarity(), d.stats(), d.modifierApplied(),
                    d.customName(), next, d.happiness(), d.skills());
        });
    }

    private static void updatePetItemOnDeath(ServerPlayer player, PetData deadData) {
        updatePetItem(player, deadData.petId(), d -> new PetData(
                d.petId(), d.mobType(), d.rarity(), d.stats(), d.modifierApplied(),
                d.customName(), Math.max(0, d.hunger() - 25), d.happiness(),
                d.skills()));
    }

    // =========================================================================
    // Cooldown helpers  - keyed by petId
    // =========================================================================

    public static boolean isOnCooldown(UUID petId) {
        Long expiry = deathCooldowns.get(petId);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) { deathCooldowns.remove(petId); return false; }
        return true;
    }

    public static int getCooldownTicks(UUID petId) {
        Long expiry = deathCooldowns.get(petId);
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) { deathCooldowns.remove(petId); return 0; }
        return (int) (remaining / 50L);
    }

    // =========================================================================
    // Panel support
    // =========================================================================

    public static void openPanelFor(ServerPlayer player, ItemStack stack) {
        PetData data = PetData.fromStack(stack);
        if (data == null) return;
        UUID playerUuid = player.getUUID();
        int cooldown = getCooldownTicks(data.petId());
        boolean active = isActivePet(playerUuid, data.petId());
        int movOrd  = petMovement.getOrDefault(playerUuid, PetMovementMode.POCKET).ordinal();
        int behOrd  = petBehaviour.getOrDefault(playerUuid, PetBehaviourMode.IDLE).ordinal();
        // Determine current / max HP for panel display
        int vitality = data.stats().getOrDefault(PetStat.ENDURANCE, 1);
        float maxHp  = 5.0f + vitality * 5.0f;
        float curHp;
        Mob mob = getActivePetMob(playerUuid);
        if (mob != null && mob.isAlive() && isActivePet(playerUuid, data.petId())) {
            curHp = mob.getHealth();
            maxHp = mob.getMaxHealth();
        } else {
            curHp = storedPetHp.getOrDefault(data.petId(), pocketHp.getOrDefault(data.petId(), maxHp));
        }
        // Compute skill cooldown remaining times (skill_id → remainingMs)
        net.minecraft.nbt.CompoundTag skillCds = new net.minecraft.nbt.CompoundTag();
        long now = System.currentTimeMillis();
        for (com.arcadia.pets.skill.SkillInstance inst : data.skills()) {
            String cdKey = inst.skill().getCooldownPersistentKey();
            long remaining = 0L;
            if (cdKey != null) {
                long lastUse = player.getPersistentData().getLong(cdKey);
                long cdMs    = inst.skill().getCooldownMs(inst.level());
                remaining    = Math.max(0L, cdMs - (now - lastUse));
            }
            skillCds.putLong(inst.skill().getId(), remaining);
        }
        // Skill toggle states (true = enabled)
        net.minecraft.nbt.CompoundTag skillToggles = new net.minecraft.nbt.CompoundTag();
        java.util.Set<String> disabled = getDisabledSkills(playerUuid);
        for (com.arcadia.pets.skill.SkillInstance inst : data.skills()) {
            skillToggles.putBoolean(inst.skill().getId(), !disabled.contains(inst.skill().getId()));
        }
        PacketDistributor.sendToPlayer(player, new S2CPetPanel(data.toTag(), cooldown, active, movOrd, behOrd, curHp, maxHp, skillCds, skillToggles));
    }

    public static boolean isActivePet(UUID playerUuid, UUID petId) {
        PetData active = activePetData.get(playerUuid);
        if (active != null && active.petId().equals(petId)) return true;
        PetData pocket = pocketPets.get(playerUuid);
        return pocket != null && pocket.petId().equals(petId);
    }

    public static void handlePetAction(ServerPlayer player, int actionId, UUID petId) {
        if (!com.arcadia.pets.PetsGlobalFlags.PETS_ENABLED && !player.hasPermissions(2)) {
            player.sendSystemMessage(com.arcadia.lib.ArcadiaMessages.error("Pets are currently disabled on this server."));
            return;
        }
        int baseAction = actionId >= 256 ? actionId / 256 : actionId;
        int modeIndex  = actionId >= 256 ? actionId % 256 : 0;

        if (baseAction == C2SPetAction.SUMMON_RECALL) {
            if (isActivePet(player.getUUID(), petId)) {
                despawn(player);
            } else {
                // Per-pet cooldown check  - other pets are unaffected
                if (isOnCooldown(petId)) {
                    int secs = getCooldownTicks(petId) / 20;
                    player.sendSystemMessage(Component.translatable("arcadia_pets.msg.pet_recovery_cooldown", (secs / 60), (secs % 60))
                            .withStyle(ChatFormatting.RED));
                } else {
                    summonByPetId(player, petId);
                }
            }

        } else if (baseAction == C2SPetAction.FEED) {
            // Priority: Snack (premium) > Treat (basic)
            int foodSlot = -1;
            int feedType = 0; // 0=none, 1=snack, 2=treat
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (!s.isEmpty() && s.getItem() == PetsModItems.PET_SNACK.get()) { foodSlot = i; feedType = 1; break; }
            }
            if (feedType == 0) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack s = player.getInventory().getItem(i);
                    if (!s.isEmpty() && s.getItem() == PetsModItems.PET_TREAT.get()) { foodSlot = i; feedType = 2; break; }
                }
            }
            if (foodSlot < 0) {
                player.sendSystemMessage(
                        Component.translatable("arcadia_pets.msg.pet_needs_food").withStyle(ChatFormatting.RED));
            } else {
                int hungerGain    = feedType == 1 ? com.arcadia.pets.item.PetSnackItem.HUNGER_BONUS : 30;
                int hpGain        = feedType == 1 ? 5 : 3;
                ItemStack petStack = findPetStack(player, petId);
                if (!petStack.isEmpty()) {
                    PetData d = PetData.fromStack(petStack);
                    if (d != null) {
                        int fHungerGain    = hungerGain;
                        updatePetItem(player, petId, pd -> new PetData(
                                pd.petId(), pd.mobType(), pd.rarity(), pd.stats(), pd.modifierApplied(),
                                pd.customName(),
                                Math.min(100, pd.hunger() + fHungerGain),
                                pd.happiness(),
                                pd.skills()));
                        player.getInventory().getItem(foodSlot).shrink(1);
                        // Generic feed message with HP gain
                        player.sendSystemMessage(Component.translatable("arcadia_pets.msg.pet_fed", hpGain).withStyle(ChatFormatting.GREEN));
                        if (hpGain > 0) applyHpHeal(player, petId, hpGain);
                        // Always send HP sync so hunger/happiness updates reach the client HUD
                        Mob mob = getActivePetMob(player.getUUID());
                        if (mob != null) {
                            sendHpSync(player, mob.getHealth(), mob.getMaxHealth(), true);
                        } else if (pocketPets.containsKey(player.getUUID())) {
                            PetData pd2 = pocketPets.get(player.getUUID());
                            if (pd2 != null) {
                                int vit = pd2.stats().getOrDefault(PetStat.ENDURANCE, 1);
                                float mx = 5.0f + vit * 5.0f;
                                sendHpSync(player, pocketHp.getOrDefault(pd2.petId(), mx), mx, true);
                            }
                        }
                    }
                }
            }

        } else if (baseAction == C2SPetAction.SET_MOVEMENT) {
            PetMovementMode[] modes = PetMovementMode.values();
            if (modeIndex >= 0 && modeIndex < modes.length) {
                UUID playerUuid = player.getUUID();
                PetMovementMode oldMode = petMovement.getOrDefault(playerUuid, PetMovementMode.POCKET);
                PetMovementMode newMode = modes[modeIndex];

                // Block blacklisted mobs from switching to Follow mode (UI grays the button,
                // this is a server-side safety net against modified clients).
                if (newMode == PetMovementMode.FOLLOW) {
                    PetData cur = activePetData.get(playerUuid);
                    if (cur == null) cur = pocketPets.get(playerUuid);
                    if (cur != null && FOLLOW_BLACKLIST.contains(cur.mobType())) {
                        sendPanelUpdate(player, petId);
                        return;
                    }
                }

                petMovement.put(playerUuid, newMode);

                if (oldMode == PetMovementMode.POCKET && newMode != PetMovementMode.POCKET) {
                    // Leaving pocket mode: recall visual on all clients + re-summon as entity
                    PetData data = pocketPets.remove(playerUuid);
                    if (data != null) {
                        float maxHp = 5.0f + data.stats().getOrDefault(PetStat.ENDURANCE, 1) * 5.0f;
                        storedPetHp.put(data.petId(), pocketHp.getOrDefault(data.petId(), maxHp));
                        S2CPocketPet recall = S2CPocketPet.recall(playerUuid);
                        broadcastToNearby(player, recall);
                        PacketDistributor.sendToPlayer(player, recall);
                        summon(player, data);
                    }
                } else {
                    refreshActivePetModes(player);
                    // Send HP sync to keep HUD data fresh on mode switch
                    Mob mob = getActivePetMob(playerUuid);
                    if (mob != null) {
                        sendHpSync(player, mob.getHealth(), mob.getMaxHealth(), true);
                    }
                }
            }

        } else if (baseAction == C2SPetAction.SET_BEHAVIOUR) {
            PetBehaviourMode[] modes = PetBehaviourMode.values();
            if (modeIndex >= 0 && modeIndex < modes.length) {
                petBehaviour.put(player.getUUID(), modes[modeIndex]);
                refreshActivePetModes(player);
            }

        } else if (baseAction == C2SPetAction.TOGGLE_SKILL) {
            PetData data = activePetData.get(player.getUUID());
            if (data == null) data = pocketPets.get(player.getUUID());
            if (data == null) {
                UUID designated = designatedPetId.get(player.getUUID());
                if (designated != null) {
                    ItemStack ds = findPetStackAnywhere(player, designated);
                    if (!ds.isEmpty()) data = PetData.fromStack(ds);
                }
            }
            if (data != null && modeIndex >= 0 && modeIndex < data.skills().size()) {
                com.arcadia.pets.skill.SkillInstance si = data.skills().get(modeIndex);
                if (si.level() > 0) {
                    toggleSkill(player.getUUID(), si.skill().getId());
                }
            }

        } else if (baseAction == C2SPetAction.OPEN_PANEL) {
            // Use designated pet first, fall back to equipped
            UUID targetPetId = designatedPetId.get(player.getUUID());
            if (targetPetId == null) {
                PetData eq = getActivePetData(player.getUUID());
                if (eq == null) eq = getPocketPetData(player.getUUID());
                if (eq != null) targetPetId = eq.petId();
            }
            if (targetPetId != null) {
                ItemStack stack = findPetStackAnywhere(player, targetPetId);
                if (!stack.isEmpty()) openPanelFor(player, stack);
            }
        }

        sendPanelUpdate(player, petId);
    }

    // =========================================================================
    // Movement / behaviour mode application
    // =========================================================================

    /**
     * Looks up the player's current active pet mob and re-applies goals.
     */
    private static void refreshActivePetModes(ServerPlayer player) {
        Entity petEntity = activePets.get(player.getUUID());
        if (petEntity instanceof Mob mob) {
            applyModes(player, mob);
        }
    }

    /**
     * Rebuilds the mob's goalSelector to match the current movement mode.
     * DEFEND/ATTACK behaviour handled by PetEventHandler  - no goals needed.
     */
    private static void applyModes(ServerPlayer player, Mob mob) {
        PetMovementMode move = petMovement.getOrDefault(player.getUUID(), PetMovementMode.POCKET);

        mob.goalSelector.removeAllGoals(g -> true);
        mob.targetSelector.removeAllGoals(g -> true);

        switch (move) {
            case POCKET -> {
                // Switching to POCKET while an entity exists: discard entity, activate pocket (no server entity).
                PetData data = activePetData.remove(player.getUUID());
                activePets.remove(player.getUUID());
                mob.discard();
                if (data != null) activatePocketMode(player, data);
                return; // entity discarded -- no further goal setup needed
            }
            case FOLLOW -> {
                mob.setNoAi(false);
                mob.setSilent(true);
                mob.setNoGravity(false);
                mob.goalSelector.addGoal(1, new PetFollowGoal(mob, player.getUUID(), 1.2, 3.0, 8.0));
            }
        }
        // IDLE / DEFEND / ATTACK behaviour is handled entirely by PetEventHandler
        // (LivingDamageEvent)  - no goals needed here.
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /** Heals the player's active or pocket pet by {@code amount} HP, then syncs the client. */
    private static void applyHpHeal(ServerPlayer player, UUID petId, int amount) {
        UUID playerUuid = player.getUUID();
        Mob mob = getActivePetMob(playerUuid);
        if (mob != null && mob.isAlive()) {
            float newHp = Math.min(mob.getMaxHealth(), mob.getHealth() + amount);
            mob.setHealth(newHp);
            storedPetHp.put(petId, newHp);
            sendHpSync(player, newHp, mob.getMaxHealth(), true);
            return;
        }
        PetData pocket = pocketPets.get(playerUuid);
        if (pocket != null && pocket.petId().equals(petId)) {
            int vitality = pocket.stats().getOrDefault(PetStat.ENDURANCE, 1);
            float max    = 5.0f + vitality * 5.0f;
            float newHp  = Math.min(max, pocketHp.getOrDefault(petId, max) + amount);
            pocketHp.put(petId, newHp);
            sendHpSync(player, newHp, max, true);
        }
    }

    private static void sendPanelUpdate(ServerPlayer player, UUID petId) {
        ItemStack s = findPetStackAnywhere(player, petId);
        if (!s.isEmpty()) openPanelFor(player, s);
    }

    public static void summonByPetId(ServerPlayer player, UUID petId) {
        ItemStack s = findPetStack(player, petId);
        if (!s.isEmpty()) {
            PetData d = PetData.fromStack(s);
            if (d != null) summon(player, d);
        }
    }

    /** Activates pocket mode: stores data server-side, sends render packet to nearby clients. */
    private static void activatePocketMode(ServerPlayer player, PetData data) {
        UUID playerUuid = player.getUUID();
        pocketPets.put(playerUuid, data);
        designatedPetId.put(playerUuid, data.petId());
        int vitality = data.stats().getOrDefault(PetStat.ENDURANCE, 0);
        float maxHp = 5.0f + vitality * 5.0f;
        pocketHp.putIfAbsent(data.petId(), maxHp);
        float sizeNorm = getMobSizeScale(data.mobType());
        float scale = (0.25f + vitality * 0.10f) * sizeNorm;

        // Pocket pets sit on the player's shoulder — apply a tighter size cap than summoned entities
        ResourceLocation entityRL = ResourceLocation.parse(data.mobType());
        java.util.Optional<EntityType<?>> typeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(entityRL);
        if (typeOpt.isPresent()) {
            float baseWidth  = typeOpt.get().getDimensions().width();
            float baseHeight = typeOpt.get().getDimensions().height();
            double maxDim = Math.max(baseWidth, baseHeight);
            if (maxDim * scale > 0.6) {
                scale = (float) (0.6 / maxDim);
            }
        }

        String name = (data.customName() != null && !data.customName().isEmpty())
                ? data.customName()
                : formatMobType(data.mobType());
        S2CPocketPet pkt = new S2CPocketPet(playerUuid, data.mobType(), scale, name);
        broadcastToNearby(player, pkt);
        PacketDistributor.sendToPlayer(player, pkt);
        float currentHp = pocketHp.getOrDefault(data.petId(), maxHp);
        sendHpSync(player, currentHp, maxHp, true);
        SkillHandler.triggerSummon(player);
        applyPetStatBonuses(player, data);
    }

    /** Sends a pocket-pet packet to all players within 64 blocks (excluding the owner). */
    private static void broadcastToNearby(ServerPlayer player, S2CPocketPet pkt) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        double rangeSq = 64.0 * 64.0;
        for (ServerPlayer other : sl.getServer().getPlayerList().getPlayers()) {
            if (other != player && other.distanceToSqr(player) <= rangeSq) {
                PacketDistributor.sendToPlayer(other, pkt);
            }
        }
    }

    // =========================================================================
    // Public helpers used by PetEventHandler
    // =========================================================================

    public static PetBehaviourMode getBehaviourMode(UUID playerUuid) {
        return petBehaviour.getOrDefault(playerUuid, PetBehaviourMode.IDLE);
    }

    public static Mob getActivePetMob(UUID playerUuid) {
        Entity e = activePets.get(playerUuid);
        return e instanceof Mob mob ? mob : null;
    }

    /** Updates stored HP when the pet takes shield damage (bypasses normal despawn flow). */
    public static void syncStoredHp(UUID playerUuid, float hp) {
        PetData data = activePetData.get(playerUuid);
        if (data != null) storedPetHp.put(data.petId(), hp);
    }

    /**
     * Called when DEFEND mode consumes the pet's last HP.
     * Handles cooldown, item update, and entity removal  - same as natural death.
     */
    public static void killPetAsShield(ServerPlayer player, Mob pet) {
        PetData dying = activePetData.get(player.getUUID());
        java.util.UUID dyingId = dying != null ? dying.petId() : null;
        onPetDeath(pet);  // registers cooldown, penalty, death message
        if (pet.isAlive()) pet.discard();
        if (dyingId != null) drainHunger(player, dyingId, 20);
    }

    /**
     * Returns the aftershock damage the pet adds in ATTACK mode.
     * Formula: 0.5 + STR * 0.5 -> 1.0 HP (1*) to 3.0 HP (5*).
     */
    public static float getAfterShockDamage(UUID playerUuid) {
        PetData data = activePetData.get(playerUuid);
        if (data == null) data = pocketPets.get(playerUuid);
        if (data == null) return 0f;
        int str = data.stats().getOrDefault(PetStat.POWER, 1);
        return 0.5f + str * 0.5f;
    }

    /**
     * Returns the INTELLIGENCE stat for the player's active pet (real entity or pocket).
     * Used by PetEventHandler for DEFEND-mode dodge chance.
     * Dodge chance per INT star: 4 / 8 / 14 / 22 / 32 %.
     */
    public static int getActivePetWit(UUID playerUuid) {
        PetData data = activePetData.get(playerUuid);
        if (data != null) return data.stats().getOrDefault(PetStat.WIT, 0);
        PetData pocket = pocketPets.get(playerUuid);
        if (pocket != null) return pocket.stats().getOrDefault(PetStat.WIT, 0);
        return 0;
    }

    /**
     * Computes the death cooldown in ms, reduced by CHARISMA.
     * Each CHA star reduces it by 10% (CHA 5 = 50% — 10 min → 5 min).
     * Always uses COOLDOWN_MS_PROD regardless of debug mode.
     */
    private static long getDeathCooldownMs(PetData data) {
        int cha = data.stats().getOrDefault(PetStat.CHARISMA, 0);
        return (long) (COOLDOWN_MS_PROD * (1.0 - cha * 0.10));
    }

    /**
     * Reduces the death cooldown of the player's last dead pet by {@code ms} milliseconds.
     * Called once per treat/snack consumed. No-op if no pet is on cooldown.
     */
    public static void reduceDeathCooldown(UUID playerUuid, long ms) {
        UUID petId = lastDeadPet.get(playerUuid);
        if (petId == null) return;
        Long expiry = deathCooldowns.get(petId);
        if (expiry == null) return;
        long newExpiry = expiry - ms;
        if (newExpiry <= System.currentTimeMillis()) {
            deathCooldowns.remove(petId);
        } else {
            deathCooldowns.put(petId, newExpiry);
        }
    }

    /** Returns pocket-mode pet data for a player, or null if not in pocket mode. */
    public static PetData getPocketPetData(UUID playerUuid) {
        return pocketPets.get(playerUuid);
    }

    // =========================================================================
    // Skill toggles
    // =========================================================================

    public static void toggleSkill(UUID playerUuid, String skillId) {
        java.util.Set<String> disabled = disabledSkills.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet());
        if (!disabled.remove(skillId)) disabled.add(skillId);
    }

    public static boolean isSkillDisabled(UUID playerUuid, String skillId) {
        java.util.Set<String> disabled = disabledSkills.get(playerUuid);
        return disabled != null && disabled.contains(skillId);
    }

    public static java.util.Set<String> getDisabledSkills(UUID playerUuid) {
        java.util.Set<String> disabled = disabledSkills.get(playerUuid);
        return disabled != null ? disabled : java.util.Collections.emptySet();
    }

    // =========================================================================
    // Feed active pet (used by treat/snack right-click)
    // =========================================================================

    /**
     * Feeds the player's active (or pocket) pet: adds hunger and HP.
     * Sends HP sync to keep HUD up to date.
     */
    public static void feedActivePet(ServerPlayer player, int hungerGain, int happinessGain, int hpGain) {
        UUID playerUuid = player.getUUID();
        PetData active = activePetData.get(playerUuid);
        PetData pocket = pocketPets.get(playerUuid);
        PetData data = active != null ? active : pocket;
        // Fall back to the designated pet (e.g. a dead pet that was re-selected)
        if (data == null) {
            UUID designated = designatedPetId.get(playerUuid);
            if (designated != null) {
                ItemStack stack = findPetStackAnywhere(player, designated);
                if (!stack.isEmpty()) data = PetData.fromStack(stack);
            }
        }
        if (data == null) return;
        UUID petId = data.petId();
        int fHunger = hungerGain;
        updatePetItem(player, petId, pd -> new PetData(
                pd.petId(), pd.mobType(), pd.rarity(), pd.stats(), pd.modifierApplied(),
                pd.customName(),
                Math.min(100, pd.hunger() + fHunger),
                pd.happiness(),
                pd.skills()));
        if (hpGain > 0) applyHpHeal(player, petId, hpGain);
        Mob mob = getActivePetMob(playerUuid);
        if (mob != null) {
            sendHpSync(player, mob.getHealth(), mob.getMaxHealth(), true);
        } else if (pocket != null) {
            int vit = pocket.stats().getOrDefault(PetStat.ENDURANCE, 1);
            float mx = 5.0f + vit * 5.0f;
            sendHpSync(player, pocketHp.getOrDefault(pocket.petId(), mx), mx, true);
        }
    }

    /**
     * Adds skill XP to the currently-leveling skill of the player's active pet.
     * Treat = 1 XP, Snack = 5 XP. Formula: Lv N→N+1 costs 5*N XP.
     */
    public static void addSkillXp(ServerPlayer player, int xpAmount) {
        UUID playerUuid = player.getUUID();
        PetData currentData = activePetData.get(playerUuid);
        boolean isPocket = (currentData == null);
        if (isPocket) currentData = pocketPets.get(playerUuid);
        // Fall back to the designated pet (e.g. dead pet that was re-selected)
        if (currentData == null) {
            UUID designated = designatedPetId.get(playerUuid);
            if (designated != null) {
                ItemStack stack = findPetStackAnywhere(player, designated);
                if (!stack.isEmpty()) currentData = PetData.fromStack(stack);
            }
            isPocket = false; // not in active/pocket cache — item-only update
        }
        if (currentData == null) {
            player.sendSystemMessage(Component.translatable("arcadia_pets.msg.no_active_pet")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        java.util.List<com.arcadia.pets.skill.SkillInstance> skills = currentData.skills();

        // Find first skill with 1 <= level < 10 (the one currently being leveled)
        int idx = -1;
        for (int i = 0; i < skills.size(); i++) {
            com.arcadia.pets.skill.SkillInstance s = skills.get(i);
            if (s.level() >= 1 && s.level() < 10) { idx = i; break; }
        }
        if (idx == -1) {
            player.sendSystemMessage(Component.translatable("arcadia_pets.msg.skills_maxed")
                    .withStyle(ChatFormatting.GOLD));
            return;
        }

        com.arcadia.pets.skill.SkillInstance target = skills.get(idx);
        int lvl = target.level();
        // Charisma gene bonus: +4% skill XP per CHM star
        int chmStars = currentData.stats().getOrDefault(com.arcadia.pets.item.PetStat.CHARISMA, 0);
        int boostedXp = Math.round(xpAmount * (1.0f + chmStars * 0.04f));
        int xp  = target.xp() + boostedXp;

        while (lvl < 10) {
            int cost = com.arcadia.pets.skill.SkillInstance.xpForNextLevel(lvl);
            if (xp >= cost) { xp -= cost; lvl++; }
            else break;
        }
        if (lvl >= 10) xp = 0;

        final int finalLvl  = lvl;
        final int finalXp   = xp;
        final int prevLvl   = target.level();
        final int skillIdx  = idx;
        final PetData oldData = currentData;

        // Persist to item stack
        updatePetItem(player, currentData.petId(), pd -> buildSkillUpdatedData(pd, skillIdx, finalLvl, finalXp));

        // Update in-memory cache so skill effects kick in immediately
        PetData newData = buildSkillUpdatedData(oldData, skillIdx, finalLvl, finalXp);
        if (isPocket) {
            pocketPets.put(playerUuid, newData);
        } else {
            activePetData.put(playerUuid, newData);
        }

        // Feedback messages
        if (finalLvl > prevLvl) {
            player.sendSystemMessage(Component.translatable("arcadia_pets.msg.skill_leveled",
                    target.skill().getDisplayName(), finalLvl).withStyle(ChatFormatting.GOLD));
            // Check if a new skill was unlocked by reaching Lv 10
            if (skillIdx + 1 < newData.skills().size()) {
                com.arcadia.pets.skill.SkillInstance next = newData.skills().get(skillIdx + 1);
                com.arcadia.pets.skill.SkillInstance wasLocked = oldData.skills().get(skillIdx + 1);
                if (wasLocked.level() == 0 && next.level() == 1) {
                    player.sendSystemMessage(Component.translatable("arcadia_pets.msg.skill_revealed",
                            next.skill().getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
        }
    }

    private static PetData buildSkillUpdatedData(PetData pd, int skillIdx, int newLevel, int newXp) {
        java.util.List<com.arcadia.pets.skill.SkillInstance> updated = new java.util.ArrayList<>(pd.skills());
        com.arcadia.pets.skill.SkillInstance s = updated.get(skillIdx);
        updated.set(skillIdx, new com.arcadia.pets.skill.SkillInstance(s.skill(), newLevel, s.effectiveness(), newXp));
        PetData result = new PetData(pd.petId(), pd.mobType(), pd.rarity(), pd.stats(), pd.modifierApplied(),
                pd.customName(), pd.hunger(), pd.happiness(), updated);
        return result.withUnlockedSkills();
    }

    /**
     * Reduces pocket HP by {@code damage}. Returns remaining HP (positive) or a
     * negative value representing pass-through damage after the pocket pet is defeated.
     */
    public static float reducePocketHp(ServerPlayer player, float damage) {
        PetData data = pocketPets.get(player.getUUID());
        if (data == null) return -damage;
        int vitality = data.stats().getOrDefault(PetStat.ENDURANCE, 1);
        float maxHp = 5.0f + vitality * 5.0f;
        float current = pocketHp.getOrDefault(data.petId(), maxHp);
        float remaining = current - damage;
        if (remaining > 0) {
            pocketHp.put(data.petId(), remaining);
            sendHpSync(player, remaining, maxHp, true);
            return remaining;
        }
        // Pet defeated - apply cooldown, recall visual, notify player
        UUID playerUuid = player.getUUID();
        pocketPets.remove(playerUuid);
        pocketHp.remove(data.petId());
        broadcastToNearby(player, S2CPocketPet.recall(playerUuid));
        PacketDistributor.sendToPlayer(player, S2CPocketPet.recall(playerUuid));
        long cooldownMs = getDeathCooldownMs(data);
        deathCooldowns.put(data.petId(), System.currentTimeMillis() + cooldownMs);
        lastDeadPet.put(playerUuid, data.petId());
        player.sendSystemMessage(Component.translatable("arcadia_pets.msg.pocket_pet_died")
                .withStyle(ChatFormatting.RED));
        sendHpSync(player, 0f, maxHp, false);
        return remaining; // negative; caller uses -remaining as pass-through damage
    }

    /**
     * Suppresses vanilla Brain-AI behaviors that would make certain mobs unusable as pets.
     * Called once at summon; also refreshed every 600 ticks by PetEventHandler.
     *
     * <ul>
     *   <li><b>Warden</b>  - sets DIG_COOLDOWN and VIBRATION_COOLDOWN (both ~1 h) so it never digs,
     *       never detects sounds, and never transitions to INVESTIGATE/ROAR/FIGHT activities.
     *       Also clears DISTURBANCE_LOCATION, ROAR_TARGET, ROAR_SOUND_DELAY, RECENT_PROJECTILE,
     *       ATTACK_TARGET, and NEAREST_ATTACKABLE on every refresh to keep it in IDLE.</li>
     *   <li><b>Sniffer</b> - erases the sniffing-target memory so it never stops to dig up seeds.</li>
     *   <li><b>Allay</b>   - erases the nearest-wanted-item memory so it does not fly off collecting drops.</li>
     * </ul>
     */
    public static void suppressSpecialBehaviors(Mob mob) {
        if (mob instanceof Warden warden) {
            // Keep DIG_COOLDOWN alive for ~1 hour; PetEventHandler refreshes it every 600 ticks.
            // While this memory is present, WardenAi.updateActivity() will never select DIG.
            warden.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 72_000L);

            // Prevent the VibrationSystem from processing any incoming sounds.
            // WardenAi checks this cooldown before registering a new vibration event.
            warden.getBrain().setMemoryWithExpiry(MemoryModuleType.VIBRATION_COOLDOWN, Unit.INSTANCE, 72_000L);

            // Clear all memories that drive INVESTIGATE -> ROAR -> FIGHT activity transitions.
            warden.getBrain().eraseMemory(MemoryModuleType.DISTURBANCE_LOCATION);
            warden.getBrain().eraseMemory(MemoryModuleType.ROAR_TARGET);
            warden.getBrain().eraseMemory(MemoryModuleType.ROAR_SOUND_DELAY);
            warden.getBrain().eraseMemory(MemoryModuleType.RECENT_PROJECTILE);
            warden.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            warden.getBrain().eraseMemory(MemoryModuleType.NEAREST_ATTACKABLE);
        }

        if (mob instanceof Sniffer sniffer) {
            // Without a sniffing target the sniffer keeps walking normally instead of stopping to dig
            sniffer.getBrain().eraseMemory(MemoryModuleType.SNIFFER_SNIFFING_TARGET);
        }

        if (mob instanceof Allay allay) {
            // Without a wanted-item target the allay wanders next to the player instead of chasing drops
            allay.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
        }
    }



    /**
     * Returns a scale normalization factor derived from each mob's natural hitbox height,
     * so all pets appear at a proportionate companion size regardless of species.
     *
     * <p>Target: ~0.70 blocks visual height at median stats (END 3*, evo 0).
     * Formula: {@code sizeNorm = 0.70 / (naturalHeight * 0.55)}</p>
     *
     * <ul>
     *   <li>Tiny mobs  (height <= 0.5) -> sizeNorm 2.5-3.5  (scaled up, appear near natural size)</li>
     *   <li>Small mobs (height 0.6-0.9) -> sizeNorm 1.4-2.0</li>
     *   <li>Standard mobs (~1.0-1.4) -> sizeNorm ~1.0 (default)</li>
     *   <li>Large mobs (height > 1.4) -> sizeNorm < 1.0  (scaled down)</li>
     * </ul>
     */
    public static float getMobSizeScale(String mobType) {
        return switch (mobType) {
            // -- Tiny mobs (natural height <= 0.5) 
            case "minecraft:silverfish"      -> 3.5f;  // h~0.30
            case "minecraft:axolotl"         -> 3.0f;  // h~0.42
            case "minecraft:turtle"          -> 3.0f;  // h~0.40 (wide but low)
            case "minecraft:rabbit"          -> 2.5f;  // h~0.50
            case "minecraft:frog"            -> 2.5f;  // h~0.50

            // -- Small mobs (natural height 0.6-0.9) 
            case "minecraft:bee"             -> 2.0f;  // h~0.60
            case "minecraft:allay"           -> 2.0f;  // h~0.60
            case "minecraft:chicken"         -> 1.8f;  // h~0.70
            case "minecraft:cat"             -> 1.8f;  // h~0.70
            case "minecraft:fox"             -> 1.8f;  // h~0.70
            case "minecraft:ocelot"          -> 1.8f;  // h~0.70
            case "minecraft:wolf"            -> 1.5f;  // h~0.85
            case "minecraft:bat"             -> 1.4f;  // h~0.90
            case "minecraft:pig"             -> 1.4f;  // h~0.90
            case "minecraft:parrot"          -> 1.4f;  // h~0.90

            // -- Standard mobs (~1.0-1.4) stay at default 1.0 
            // sheep 1.3, blaze 1.8 equivalent visual, strider 1.75, etc.

            // -- Large mobs (scaled down) 
            case "minecraft:blaze"           -> 0.75f; // h~1.80
            case "minecraft:strider"         -> 0.75f; // h~1.75
            case "minecraft:cow"             -> 0.68f; // h~1.40
            case "minecraft:mooshroom"       -> 0.68f; // h~1.40
            case "minecraft:panda"           -> 0.58f; // h~1.25
            case "minecraft:hoglin"          -> 0.56f; // h~1.40
            case "minecraft:zoglin"          -> 0.56f; // h~1.40
            case "minecraft:enderman"        -> 0.54f; // h~2.90
            case "minecraft:polar_bear"      -> 0.52f; // h~1.40
            case "minecraft:donkey"          -> 0.50f; // h~1.60
            case "minecraft:mule"            -> 0.50f; // h~1.60
            case "minecraft:horse"           -> 0.48f; // h~1.60
            case "minecraft:skeleton_horse"  -> 0.48f; // h~1.60
            case "minecraft:zombie_horse"    -> 0.48f; // h~1.60
            case "minecraft:sniffer"         -> 0.42f; // h~1.75
            case "minecraft:iron_golem"      -> 0.42f; // h~2.70
            case "minecraft:camel"           -> 0.38f; // h~2.38
            case "minecraft:wither_skeleton" -> 0.38f; // h~2.40
            case "minecraft:ravager"         -> 0.35f; // h~1.90
            case "minecraft:elder_guardian"  -> 0.40f; // h~1.99
            case "minecraft:breeze"          -> 0.60f; // h~1.70
            case "minecraft:shulker"         -> 0.65f; // h~1.00
            case "minecraft:warden"          -> 0.35f; // h~2.90
            case "minecraft:wither"          -> 0.30f; // h~3.50
            case "minecraft:ghast"           -> 0.15f; // h~4.00
            case "minecraft:ender_dragon"    -> 0.03f; // tiny — dragon is ~16 wide

            default                          -> 1.0f;
        };
    }

    /** Returns true if the given mob is currently an active summoned pet for any player. */
    public static boolean isActivePetEntity(Mob mob) {
        return activePets.containsValue(mob);
    }

    /**
     * Returns the mutable ItemStack for the given petId, checking the player's
     * inventory first, then the pet collection. Returns {@link ItemStack#EMPTY}
     * if not found in either.  Callers that modify the returned stack and it came
     * from the collection should call
     * {@code PetCollectionSavedData.getOrCreate(server).setDirty()}.
     */
    public static ItemStack findPetStackPublic(ServerPlayer player, UUID petId) {
        return findPetStack(player, petId);
    }

    static ItemStack findPetStack(ServerPlayer player, UUID petId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!(s.getItem() instanceof PetItem)) continue;
            PetData d = PetData.fromStack(s);
            if (d != null && d.petId().equals(petId)) return s;
        }
        if (player.getServer() != null) {
            ItemStack cs = PetCollectionSavedData.getOrCreate(player.getServer()).findStack(player.getUUID(), petId);
            if (!cs.isEmpty()) return cs;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Applies an updater function to the pet item identified by {@code petId},
     * checking both the player's inventory and the pet collection.
     *
     * @return {@code true} if the pet was found and updated.
     */
    public static boolean updatePetItem(ServerPlayer player, UUID petId, java.util.function.UnaryOperator<PetData> updater) {
        PetData updated = null;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!(s.getItem() instanceof PetItem)) continue;
            PetData d = PetData.fromStack(s);
            if (d == null || !d.petId().equals(petId)) continue;
            updated = updater.apply(d);
            updated.applyToStack(s);
            break;
        }
        if (updated == null && player.getServer() != null) {
            if (PetCollectionSavedData.getOrCreate(player.getServer())
                    .updatePet(player.getUUID(), petId, updater)) {
                // Re-read updated data from collection
                ItemStack cs = PetCollectionSavedData.getOrCreate(player.getServer())
                        .findStack(player.getUUID(), petId);
                if (!cs.isEmpty()) updated = PetData.fromStack(cs);
            }
        }
        if (updated == null) return false;
        // Keep in-memory caches in sync so sendHpSync reads fresh hunger/happiness
        UUID pid = player.getUUID();
        PetData active = activePetData.get(pid);
        if (active != null && active.petId().equals(petId)) activePetData.put(pid, updated);
        PetData pocket = pocketPets.get(pid);
        if (pocket != null && pocket.petId().equals(petId)) pocketPets.put(pid, updated);
        return true;
    }

    /** Returns the current movement mode for the given player (defaults to FOLLOW). */
    public static PetMovementMode getMovementMode(UUID playerUuid) {
        return petMovement.getOrDefault(playerUuid, PetMovementMode.POCKET);
    }

    /**
     * Switches the player's active pet to pocket mode automatically (e.g. when flying).
     * Does nothing if already in pocket mode or no entity is active.
     *
     * @return the previous movement mode, or {@code null} if nothing changed.
     */
    public static PetMovementMode autoPocket(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        PetMovementMode current = petMovement.getOrDefault(playerUuid, PetMovementMode.POCKET);
        if (current == PetMovementMode.POCKET) return null;
        Entity petEntity = activePets.get(playerUuid);
        if (!(petEntity instanceof Mob mob)) return null;
        PetMovementMode prev = current;
        petMovement.put(playerUuid, PetMovementMode.POCKET);
        PetData data = activePetData.remove(playerUuid);
        activePets.remove(playerUuid);
        mob.discard();
        if (data != null) activatePocketMode(player, data);
        return prev;
    }

    /**
     * Reverts the player's pet from auto-pocket back to {@code prevMode}.
     * Does nothing if the pet is no longer in pocket mode.
     */
    public static void revertFromAutoPocket(ServerPlayer player, PetMovementMode prevMode) {
        UUID playerUuid = player.getUUID();
        if (petMovement.getOrDefault(playerUuid, PetMovementMode.POCKET) != PetMovementMode.POCKET) return;
        petMovement.put(playerUuid, prevMode);
        PetData data = pocketPets.remove(playerUuid);
        if (data != null) {
            float maxHp = 5.0f + data.stats().getOrDefault(PetStat.ENDURANCE, 1) * 5.0f;
            storedPetHp.put(data.petId(), pocketHp.getOrDefault(data.petId(), maxHp));
            S2CPocketPet recall = S2CPocketPet.recall(playerUuid);
            broadcastToNearby(player, recall);
            PacketDistributor.sendToPlayer(player, recall);
            summon(player, data);
        }
    }

    /**
     * Passive HP regen: heals all active/pocket pets by 0.5 HP.
     * Called by PetEventHandler every 600 ticks (~30 s).
     */
    public static void tickRegen(ServerLevel sl) {
        for (var entry : activePets.entrySet()) {
            UUID playerUuid = entry.getKey();
            if (!(entry.getValue() instanceof Mob mob)) continue;
            if (!mob.isAlive()) continue;
            float max = mob.getMaxHealth();
            float hp  = mob.getHealth();
            if (hp >= max) continue;
            float newHp = Math.min(max, hp + 0.5f);
            mob.setHealth(newHp);
            storedPetHp.put(activePetData.get(playerUuid).petId(), newHp);
            ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(playerUuid);
            if (sp != null) sendHpSync(sp, newHp, max, true);
        }
        for (var entry : pocketPets.entrySet()) {
            UUID playerUuid = entry.getKey();
            PetData data = entry.getValue();
            int vitality = data.stats().getOrDefault(PetStat.ENDURANCE, 1);
            float max     = 5.0f + vitality * 5.0f;
            float current = pocketHp.getOrDefault(data.petId(), max);
            if (current >= max) continue;
            float newHp = Math.min(max, current + 0.5f);
            pocketHp.put(data.petId(), newHp);
            ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(playerUuid);
            if (sp != null) sendHpSync(sp, newHp, max, true);
        }
    }

    /** Sends a pet HP sync packet to the player's client, including the pet's display name. */
    public static void sendHpSync(ServerPlayer player, float current, float max, boolean active) {
        String name    = "";
        String mobType = "";
        int    hunger  = 0;
        if (active) {
            UUID pid = player.getUUID();
            PetData d = activePetData.get(pid);
            if (d == null) d = pocketPets.get(pid);
            if (d != null) {
                name = d.customName() != null && !d.customName().isEmpty()
                        ? d.customName()
                        : formatMobType(d.mobType());
                if (name.length() > 20) name = name.substring(0, 20);
                mobType = d.mobType();
                hunger  = d.hunger();
            }
        }
        PacketDistributor.sendToPlayer(player, new S2CPetHpSync(current, max, active, name, mobType, hunger));
    }

    /** Overload for call sites where PetData is known but not yet in activePetData/pocketPets maps. */
    static void sendHpSync(ServerPlayer player, float current, float max, boolean active, PetData data) {
        String name    = "";
        String mobType = "";
        int    hunger  = 0;
        if (active && data != null) {
            name = data.customName() != null && !data.customName().isEmpty()
                    ? data.customName()
                    : formatMobType(data.mobType());
            if (name.length() > 20) name = name.substring(0, 20);
            mobType = data.mobType();
            hunger  = data.hunger();
        }
        PacketDistributor.sendToPlayer(player, new S2CPetHpSync(current, max, active, name, mobType, hunger));
    }

    /** Converts "minecraft:iron_golem" -> "Iron Golem". */
    private static String formatMobType(String mobType) {
        String raw = mobType.contains(":") ? mobType.substring(mobType.indexOf(':') + 1) : mobType;
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split("_")) {
            if (!word.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return sb.toString();
    }

    // =========================================================================

    // Inner goal classes
    // =========================================================================

    /**
     * Keeps the pet near its owner. Teleports if more than 32 blocks away.
     */
    public static final class PetFollowGoal extends Goal {

        private final Mob mob;
        private final UUID ownerUuid;
        private final double speed;
        private final double startDist;
        private final double stopDist;
        private int tickDelay;

        public PetFollowGoal(Mob mob, UUID ownerUuid, double speed, double startDist, double stopDist) {
            this.mob = mob; this.ownerUuid = ownerUuid;
            this.speed = speed; this.startDist = startDist; this.stopDist = stopDist;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canUse() {
            Player owner = ((ServerLevel) mob.level()).getPlayerByUUID(ownerUuid);
            return owner != null && mob.distanceToSqr(owner) > startDist * startDist;
        }

        @Override public boolean canContinueToUse() {
            Player owner = ((ServerLevel) mob.level()).getPlayerByUUID(ownerUuid);
            return owner != null && (!mob.getNavigation().isDone() || mob.distanceToSqr(owner) > stopDist * stopDist);
        }

        @Override public void start() { tickDelay = 0; }

        @Override public void tick() {
            if (--tickDelay > 0) return;
            tickDelay = 10;
            ServerLevel sl = (ServerLevel) mob.level();
            Player owner = sl.getPlayerByUUID(ownerUuid);
            if (owner == null) { mob.getNavigation().stop(); return; }
            if (mob.distanceToSqr(owner) > 1024.0) {
                // If the owner is flying or high in the air, auto-pocket instead of
                // teleporting into thin air / the void.
                boolean ownerAirborne = !owner.onGround()
                        && (owner.getAbilities().flying || owner.isFallFlying()
                            || owner.getY() > mob.getY() + 8);
                if (ownerAirborne && owner instanceof ServerPlayer sp) {
                    PetMovementMode prev = autoPocket(sp);
                    if (prev != null) {
                        sp.sendSystemMessage(Component.literal(
                                "\uD83D\uDE80 Your pet switched to pocket mode while you\u2019re airborne.")
                                .withStyle(net.minecraft.ChatFormatting.AQUA));
                    }
                } else {
                    mob.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                }
            } else {
                mob.getNavigation().moveTo(owner, speed);
            }
        }

        @Override public void stop() { mob.getNavigation().stop(); }
    }
}
