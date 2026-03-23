package com.arcadia.pets.server;

import com.arcadia.pets.ArcadiaPets;
import com.arcadia.pets.item.PetBehaviourMode;
import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetItem;
import com.arcadia.pets.item.PetStat;
import com.arcadia.pets.item.StarEssenceItem;
import com.arcadia.pets.network.S2CAftershockCooldown;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.arcadia.pets.item.PetBagItem;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

@EventBusSubscriber(modid = ArcadiaPets.MOD_ID)
public final class PetEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private PetEventHandler() {}

    private static boolean applyingAftershock = false;
    // Dodge chance (%) per WIT star
    private static final int[] INT_DODGE_CHANCES = {0, 4, 8, 14, 22, 32};
    private static final Random RNG = new Random();

    private record PendingAftershock(LivingEntity target, float damage, long fireAtMs, java.util.UUID playerUuid) {}
    private static final ConcurrentLinkedQueue<PendingAftershock> pendingAftershocks = new ConcurrentLinkedQueue<>();
    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long> lastAftershockMs =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long AFTERSHOCK_COOLDOWN_MS = 5_000L;

    private static int regenTick = 0;
    private static final int REGEN_INTERVAL_TICKS = 600;
    // Warden DIG_COOLDOWN is 1200 ticks; refresh at half that to suppress dig without per-tick cost
    private static int brainSuppressTick = 0;

    // Wither skulls shot by WitherSkullSkill: explosion clears block list for these UUIDs

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Entity cause = event.getExplosion().getDirectSourceEntity();
        if (cause instanceof net.minecraft.world.entity.projectile.WitherSkull skull
                && com.arcadia.pets.skill.PetSkills.NO_GRIEF_SKULLS.remove(skull.getUUID())) {
            event.getAffectedBlocks().clear();
        }
    }

    // ── Pet death ─────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof Mob mob) {
            PetManager.onPetDeath(mob);
        }
    }

    @SubscribeEvent
    public static void onPetDamaged(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (PetManager.isActivePetEntity(mob)) {
            event.setNewDamage(0f);
        }
    }

    @SubscribeEvent
    public static void onPlayerDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (PetManager.getBehaviourMode(player.getUUID()) != PetBehaviourMode.DEFEND) return;

        float dmg = event.getNewDamage();
        if (dmg <= 0) return;

        // INTELLIGENCE dodge: chance to fully negate the hit
        int intel = PetManager.getActivePetWit(player.getUUID());
        if (intel >= 1 && intel <= 5 && RNG.nextInt(100) < INT_DODGE_CHANCES[intel]) {
            event.setNewDamage(0f);
            return;
        }

        Mob pet = PetManager.getActivePetMob(player.getUUID());

        if (pet != null && pet.isAlive()) {
            float petHp = pet.getHealth();
            if (petHp > dmg) {
                pet.setHealth(petHp - dmg);
                PetManager.syncStoredHp(player.getUUID(), pet.getHealth());
                PetManager.sendHpSync(player, pet.getHealth(), pet.getMaxHealth(), true);
                event.setNewDamage(0f);
            } else {
                float remaining = dmg - petHp;
                PetManager.killPetAsShield(player, pet);
                event.setNewDamage(remaining);
            }
        } else if (PetManager.getPocketPetData(player.getUUID()) != null) {
            float newHp = PetManager.reducePocketHp(player, dmg);
            if (newHp > 0) {
                event.setNewDamage(0f);
            } else {
                event.setNewDamage(-newHp);
            }
        }
    }

    @SubscribeEvent
    public static void onWardenPetAttack(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof Warden warden)) return;
        if (PetManager.isActivePetEntity(warden)) {
            event.setNewDamage(0f);
        }
    }

    @SubscribeEvent
    public static void onMobDamagedByPlayer(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) return;
        if (applyingAftershock) return;

        // Category-based target filter (admin-configurable)
        net.minecraft.world.entity.LivingEntity target = event.getEntity();
        if (target instanceof ServerPlayer) {
            if (!PetsGlobalFlags.AFTERSHOCK_ON_PLAYERS) return;
        } else if (target instanceof net.minecraft.world.entity.monster.Monster) {
            if (!PetsGlobalFlags.AFTERSHOCK_ON_HOSTILE) return;
        } else {
            if (!PetsGlobalFlags.AFTERSHOCK_ON_NEUTRAL) return;
        }

        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (PetManager.getBehaviourMode(player.getUUID()) != PetBehaviourMode.ATTACK) return;

        float aftershock = PetManager.getAfterShockDamage(player.getUUID());
        if (aftershock <= 0) return;

        long now = System.currentTimeMillis();
        Long lastMs = lastAftershockMs.get(player.getUUID());
        if (lastMs != null && now - lastMs < AFTERSHOCK_COOLDOWN_MS) return;
        lastAftershockMs.put(player.getUUID(), now);

        // Packet is sent when the aftershock actually fires (in onLevelTick), not here,
        // so the HUD drain starts from the real hit moment, not 0.5 s before.
        LivingEntity target = event.getEntity();
        pendingAftershocks.add(new PendingAftershock(target, aftershock, now + 500L, player.getUUID()));
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SkillHandler.removeAllPetAttributes(player);
        net.minecraft.nbt.CompoundTag nbt = player.getPersistentData();
        // Restore designated pet selection
        if (nbt.hasUUID("arcadia_active_pet")) {
            PetManager.setDesignatedPet(player.getUUID(), nbt.getUUID("arcadia_active_pet"));
        }
        // Re-equip (summon) the pet that was equipped before logout
        if (nbt.hasUUID("arcadia_last_pet")) {
            PetManager.summonByPetId(player, nbt.getUUID("arcadia_last_pet"));
        }
    }

    // Discard pet entities tagged "arcadia_pet" that survived a server crash (owner no longer in memory)
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.getPersistentData().getBoolean("arcadia_pet")) {
                    entity.discard();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            LOGGER.info("PetManager: discarded {} orphaned pet entity/entities from previous session.", removed);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        // Discard expired wither skulls (3s TTL) — runs in all dimensions
        if (!com.arcadia.pets.skill.PetSkills.SKULL_EXPIRY.isEmpty()) {
            long gameTick = sl.getGameTime();
            com.arcadia.pets.skill.PetSkills.SKULL_EXPIRY.removeIf(entry -> {
                if (gameTick >= entry.getValue()) {
                    entry.getKey().discard();
                    com.arcadia.pets.skill.PetSkills.NO_GRIEF_SKULLS.remove(entry.getKey().getUUID());
                    return true;
                }
                return false;
            });
        }

        if (!sl.dimension().equals(LevelStem.OVERWORLD)) return;

        if (++regenTick >= REGEN_INTERVAL_TICKS) {
            regenTick = 0;
            PetManager.tickRegen(sl);
        }

        if (++brainSuppressTick >= 600) {
            brainSuppressTick = 0;
            // Only suppress brain-based mobs (Warden, Sniffer) — skip the rest
            PetManager.getAllActivePetEntities()
                    .filter(mob -> mob instanceof Warden || mob instanceof net.minecraft.world.entity.animal.sniffer.Sniffer)
                    .forEach(PetManager::suppressSpecialBehaviors);
        }

        if (pendingAftershocks.isEmpty()) return;

        long now = System.currentTimeMillis();
        pendingAftershocks.removeIf(p -> {
            if (now < p.fireAtMs()) return false;
            if (p.target().isAlive()) {
                applyingAftershock = true;
                try {
                    p.target().hurt(p.target().level().damageSources().magic(), p.damage());
                } finally {
                    applyingAftershock = false;
                }
            }
            ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(p.playerUuid());
            if (sp != null) {
                SkillHandler.triggerAftershock(sp, p.target());
                PacketDistributor.sendToPlayer(sp, new S2CAftershockCooldown((int) AFTERSHOCK_COOLDOWN_MS));
            }
            return true;
        });
    }

    // -------------------------------------------------------------------------
    // Star Essence anvil upgrade
    // Left: PetItem   Right: StarEssenceItem   → upgraded pet, cost 5 XP levels
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left  = event.getLeft();
        ItemStack right = event.getRight();

        if (!(left.getItem() instanceof PetItem)) return;
        // Block all anvil operations on pets except star essence upgrade.
        // For rename-only (right = empty): setOutput to unchanged left item so the event
        // is marked handled and vanilla rename logic is skipped.
        if (!(right.getItem() instanceof StarEssenceItem)) {
            event.setOutput(left.copy()); // return unchanged item — blocks rename
            event.setCost(0);
            return;
        }

        PetData data = PetData.fromStack(left);
        if (data == null) return;

        if (data.modifierApplied()) {
            // Already upgraded — show blocked output with no name so vanilla displays nothing
            return;
        }

        // Collect upgradeable stats (< 5★)
        List<PetStat> upgradeable = new ArrayList<>();
        for (var entry : data.stats().entrySet()) {
            if (entry.getValue() < 5) upgradeable.add(entry.getKey());
        }
        if (upgradeable.isEmpty()) return;

        // Deterministic pick seeded by pet UUID so the preview is stable
        int idx = Math.abs(data.petId().hashCode()) % upgradeable.size();
        PetStat chosen = upgradeable.get(idx);
        int newVal = data.stats().get(chosen) + 1;

        var newStats = new EnumMap<>(data.stats());
        newStats.put(chosen, newVal);

        PetData upgraded = new PetData(
                data.petId(), data.mobType(), data.rarity(), newStats,
                true, data.customName(), data.hunger(), data.happiness(),
                data.skills());

        ItemStack output = left.copy();
        upgraded.applyToStack(output);

        // Block rename — the only allowed anvil operation on pets is star essence upgrade.

        event.setOutput(output);
        event.setCost(5);
        event.setMaterialCost(1); // consume 1 essence
    }

    /** Prevents other players from picking up pet-crate drops that are locked to the opener. */
    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        java.util.UUID itemId = event.getItemEntity().getUUID();
        java.util.UUID owner  = PetBagItem.LOCKED_DROPS.get(itemId);
        if (owner == null) return; // not a locked drop
        if (owner.equals(event.getPlayer().getUUID())) {
            PetBagItem.LOCKED_DROPS.remove(itemId); // consumed — clean up
        } else {
            event.setCanPickup(TriState.FALSE);
        }
    }

}
