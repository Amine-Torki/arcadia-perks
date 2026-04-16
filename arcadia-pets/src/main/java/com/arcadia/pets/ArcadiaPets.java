package com.arcadia.pets;

import com.arcadia.pets.config.PetPoolConfig;
import com.arcadia.pets.network.PetPacketHandler;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

@Mod("arcadia_pets")
public final class ArcadiaPets {
    public static final String MOD_ID = "arcadia_pets";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArcadiaPets(IEventBus modBus, ModContainer container) {
        PetsModItems.ITEMS.register(modBus);
        PetsModItems.CREATIVE_TABS.register(modBus);
        PetsModMenus.MENUS.register(modBus);

        modBus.addListener(PetPacketHandler::onRegisterPayloadHandlers);
        modBus.addListener(this::onConfigLoad);
        modBus.addListener(this::onCommonSetup);

        container.registerConfig(ModConfig.Type.SERVER, PetPoolConfig.SPEC, "arcadia/pets/pets.toml");
    }

    private void onCommonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        // Register database tables
        com.arcadia.lib.data.DatabaseManager.registerTables(new com.arcadia.pets.server.PetsTableDefinition());

        // Register pets tab handler via the central lib registry
        com.arcadia.lib.ArcadiaModRegistry.registerTabHandler(1,
                com.arcadia.pets.server.PetsDashboardTab::new);

        // Register hub card
        com.arcadia.lib.ArcadiaModRegistry.registerCard(
                new com.arcadia.lib.client.ArcadiaModCard("pets", "♦",
                        "arcadia_pets.hub.pets.label", "arcadia_pets.hub.pets.sub",
                        0x4ECCA3, 1, 1, true));

        // Register server-side actions (so prestige can call them without importing us)
        com.arcadia.lib.ArcadiaModRegistry.registerPetItemProvider((uuid, mobType) -> {
            if (mobType == null || mobType.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
            try {
                var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server == null) return net.minecraft.world.item.ItemStack.EMPTY;
                var col = com.arcadia.pets.server.PetCollectionSavedData.getOrCreate(server).getCollection(uuid);
                for (var stack : col) {
                    com.arcadia.pets.item.PetData pd = com.arcadia.pets.item.PetData.fromStack(stack);
                    if (pd != null && mobType.equals(pd.mobType())) return stack.copy();
                }
            } catch (Exception ignored) {}
            return net.minecraft.world.item.ItemStack.EMPTY;
        });

        com.arcadia.lib.ArcadiaModRegistry.registerServerAction("pets.despawn",
                p -> com.arcadia.pets.server.PetManager.despawn(p));
        com.arcadia.lib.ArcadiaModRegistry.registerServerActionWithPayload("pets.summon",
                (p, slot) -> {
                    try { com.arcadia.pets.server.PetManager.summonFromInventory(p, Integer.parseInt(slot)); }
                    catch (NumberFormatException ignored) {}
                });
        com.arcadia.lib.ArcadiaModRegistry.registerServerActionWithPayload("pets.star_essence",
                (p, slot) -> {
                    try { com.arcadia.pets.server.PetManager.applyStarEssence(p, Integer.parseInt(slot)); }
                    catch (NumberFormatException ignored) {}
                });
        com.arcadia.lib.ArcadiaModRegistry.registerServerActionWithPayload("pets.add_skill_xp_pet",
                (p, payload) -> {
                    // payload format: "petUuid:xpAmount"
                    try {
                        String[] parts = payload.split(":", 2);
                        java.util.UUID petId = java.util.UUID.fromString(parts[0]);
                        int xp = Integer.parseInt(parts[1]);
                        com.arcadia.pets.server.PetManager.addSkillXpToPet(p, petId, xp);
                    } catch (Exception ignored) {}
                });

        // Client-side actions are registered in PetsClientEvents (CLIENT dist only)

        // Register reward items (so DailyRewardHandler can give pet items without importing us)
        com.arcadia.lib.ArcadiaModRegistry.registerRewardItem("common_pet_bag",
                () -> new net.minecraft.world.item.ItemStack(PetsModItems.COMMON_PET_BAG.get()));
        com.arcadia.lib.ArcadiaModRegistry.registerRewardItem("uncommon_pet_bag",
                () -> new net.minecraft.world.item.ItemStack(PetsModItems.UNCOMMON_PET_BAG.get()));
        com.arcadia.lib.ArcadiaModRegistry.registerRewardItem("rare_pet_bag",
                () -> new net.minecraft.world.item.ItemStack(PetsModItems.RARE_PET_BAG.get()));
        com.arcadia.lib.ArcadiaModRegistry.registerRewardItem("epic_pet_bag",
                () -> new net.minecraft.world.item.ItemStack(PetsModItems.EPIC_PET_BAG.get()));
        com.arcadia.lib.ArcadiaModRegistry.registerRewardItem("legendary_pet_bag",
                () -> new net.minecraft.world.item.ItemStack(PetsModItems.LEGENDARY_GUARANTEED_BAG.get()));
        com.arcadia.lib.ArcadiaModRegistry.registerRewardItem("star_essence",
                () -> new net.minecraft.world.item.ItemStack(PetsModItems.STAR_ESSENCE.get()));
        com.arcadia.lib.ArcadiaModRegistry.registerRewardItem("pet_treat",
                () -> new net.minecraft.world.item.ItemStack(PetsModItems.PET_TREAT.get()));
        com.arcadia.lib.ArcadiaModRegistry.registerRewardItem("pet_snack",
                () -> new net.minecraft.world.item.ItemStack(PetsModItems.PET_SNACK.get()));

        // Register player logout callback — CRITICAL: despawns the pet entity on disconnect
        // Without this, the pet stays alive in the world and attacks players after relog
        com.arcadia.lib.player.PlayerManager.onQuit(
                p -> com.arcadia.pets.server.PetManager.handlePlayerLogout(p));

        LOGGER.info("[ArcadiaPets] Registered in ArcadiaModRegistry (tab, cards, actions, items).");
    }

    private void onConfigLoad(ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) return;
        if (event.getConfig().getSpec() == PetPoolConfig.SPEC) {
            PetPoolConfig.applyToRarities();
            LOGGER.info("[ArcadiaPets] Pet pool config loaded.");
        }
    }
}
