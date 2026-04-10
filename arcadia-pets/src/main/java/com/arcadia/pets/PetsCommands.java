package com.arcadia.pets;

import com.arcadia.lib.DebugMode;
import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetRarity;
import com.arcadia.pets.item.PetRoller;
import com.arcadia.pets.item.PetStat;
import com.arcadia.lib.ArcadiaModRegistry;
import com.arcadia.pets.server.FusionMenu;
import com.arcadia.pets.server.PetHistoryMenu;
import com.arcadia.pets.server.PetHistorySavedData;
import com.arcadia.pets.server.PetManager;
import com.arcadia.pets.skill.PetSkill;
import com.arcadia.pets.skill.PetSkills;
import com.arcadia.pets.skill.SkillInstance;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = ArcadiaPets.MOD_ID)
public final class PetsCommands {

    private PetsCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("arcadia_pets")
                .executes(ctx -> {
                    if (!checkEnabled(ctx.getSource())) return 0;
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        ArcadiaModRegistry.openTab(player, 1);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("enable")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        PetsGlobalFlags.PETS_ENABLED = true;
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "§a[Arcadia] Pets enabled for all players."), true);
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("disable")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        PetsGlobalFlags.PETS_ENABLED = false;
                        // Recall all active pets for every online non-op player immediately
                        if (ctx.getSource().getServer() != null) {
                            for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                if (!p.hasPermissions(2)) PetManager.despawn(p);
                            }
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "§c[Arcadia] Pets disabled. All active pets recalled. Operators are unaffected."), true);
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("bag")
                    .requires(src -> DebugMode.ENABLED || src.hasPermission(2))
                    .then(Commands.argument("tier", StringArgumentType.word())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                            String tier = StringArgumentType.getString(ctx, "tier").toLowerCase();
                            ItemStack bag = switch (tier) {
                                case "uncommon"  -> new ItemStack(PetsModItems.UNCOMMON_PET_BAG.get());
                                case "rare"      -> new ItemStack(PetsModItems.RARE_PET_BAG.get());
                                case "epic"      -> new ItemStack(PetsModItems.EPIC_PET_BAG.get());
                                case "legendary" -> new ItemStack(PetsModItems.LEGENDARY_GUARANTEED_BAG.get());
                                default          -> new ItemStack(PetsModItems.COMMON_PET_BAG.get());
                            };
                            giveOrDrop(player, bag);
                            player.sendSystemMessage(Component.literal("[Debug] Given: " + bag.getHoverName().getString()));
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(Commands.literal("essence")
                    .requires(src -> DebugMode.ENABLED || src.hasPermission(2))
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                        giveOrDrop(player, new ItemStack(PetsModItems.STAR_ESSENCE.get()));
                        player.sendSystemMessage(Component.literal("[Debug] Given: Star Essence"));
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("pet")
                    .requires(src -> DebugMode.ENABLED || src.hasPermission(2))
                    .then(Commands.argument("mobtype", StringArgumentType.word())
                        .executes(ctx -> giveDebugPet(ctx.getSource().getPlayerOrException(),
                                StringArgumentType.getString(ctx, "mobtype"), "mythic"))
                        .then(Commands.argument("rarity", StringArgumentType.word())
                            .executes(ctx -> giveDebugPet(ctx.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(ctx, "mobtype"),
                                    StringArgumentType.getString(ctx, "rarity")))
                        )
                    )
                )
                .then(Commands.literal("skill")
                    .requires(s -> DebugMode.ENABLED && DebugMode.isDebugSource(s))
                    .then(Commands.argument("id", StringArgumentType.string())
                        .executes(ctx -> addSkill(ctx.getSource(), StringArgumentType.getString(ctx, "id"), 1))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                            .executes(ctx -> addSkill(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "id"),
                                    IntegerArgumentType.getInteger(ctx, "level")))
                        )
                    )
                )
                .then(Commands.literal("roll")
                    .requires(src -> DebugMode.ENABLED || src.hasPermission(2))
                    .then(Commands.argument("rarity", StringArgumentType.word())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                            String rarityArg = StringArgumentType.getString(ctx, "rarity").toUpperCase();
                            PetRarity floor;
                            try { floor = PetRarity.valueOf(rarityArg); }
                            catch (IllegalArgumentException e) {
                                player.sendSystemMessage(Component.literal(
                                        "[Debug] Unknown rarity. Use: common/uncommon/rare/epic/legendary/mythic"));
                                return 0;
                            }
                            PetData rolled = PetRoller.roll(floor);
                            EnumMap<PetStat, Integer> maxStats = new EnumMap<>(PetStat.class);
                            for (PetStat s : PetStat.values()) maxStats.put(s, 5);
                            PetData pet = new PetData(rolled.petId(), rolled.mobType(), rolled.rarity(),
                                    maxStats, rolled.modifierApplied(), rolled.customName(),
                                    rolled.hunger(), rolled.happiness(), rolled.skills());
                            ItemStack stack = new ItemStack(PetsModItems.PET_ITEM.get());
                            pet.applyToStack(stack);
                            giveOrDrop(player, stack);
                            player.sendSystemMessage(Component.literal(
                                    "[Debug] Rolled: " + pet.rarity().getDisplayName() + " " + pet.mobType() + " (30★ / cheated)"));
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(Commands.literal("rank")
                    .requires(src -> DebugMode.ENABLED || src.hasPermission(2))
                    .then(Commands.argument("grade", StringArgumentType.word())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                            String grade = StringArgumentType.getString(ctx, "grade").toLowerCase();
                            if (!java.util.Set.of("none","default","vip","vip+","mvp","founder").contains(grade)) {
                                player.sendSystemMessage(Component.literal(
                                        "[Debug] Unknown grade. Use: none | vip | vip+ | mvp | founder"));
                                return 0;
                            }
                            DebugMode.setDebugGrade(player.getUUID(), grade);
                            player.sendSystemMessage(Component.literal("[Debug] Simulated rank set to: §e" + grade));
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(Commands.literal("history")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> petHistory(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))
                    )
                )
                .then(Commands.literal("restore")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("petId", StringArgumentType.word())
                            .executes(ctx -> petRestore(ctx.getSource(),
                                    EntityArgument.getPlayer(ctx, "target"),
                                    StringArgumentType.getString(ctx, "petId")))
                        )
                    )
                )
                .then(Commands.literal("aftershock")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("hostile")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> {
                                PetsGlobalFlags.AFTERSHOCK_ON_HOSTILE = BoolArgumentType.getBool(ctx, "value");
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "§6[Arcadia] Aftershock on hostile mobs: §e" + PetsGlobalFlags.AFTERSHOCK_ON_HOSTILE), true);
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                    .then(Commands.literal("neutral")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> {
                                PetsGlobalFlags.AFTERSHOCK_ON_NEUTRAL = BoolArgumentType.getBool(ctx, "value");
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "§6[Arcadia] Aftershock on neutral/passive mobs: §e" + PetsGlobalFlags.AFTERSHOCK_ON_NEUTRAL), true);
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                    .then(Commands.literal("players")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> {
                                PetsGlobalFlags.AFTERSHOCK_ON_PLAYERS = BoolArgumentType.getBool(ctx, "value");
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "§6[Arcadia] Aftershock on players: §e" + PetsGlobalFlags.AFTERSHOCK_ON_PLAYERS), true);
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                )
        );

        // Keep /fuse as alias for convenience
        event.getDispatcher().register(
            Commands.literal("fuse")
                .executes(ctx -> {
                    if (!checkEnabled(ctx.getSource())) return 0;
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        FusionMenu.openFor(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean checkEnabled(CommandSourceStack src) {
        if (!PetsGlobalFlags.PETS_ENABLED
                && src.getEntity() instanceof ServerPlayer p
                && !p.hasPermissions(2)) {
            src.sendFailure(Component.literal("§c[Arcadia] This feature is currently disabled on this server."));
            return false;
        }
        return true;
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static int giveDebugPet(ServerPlayer player, String mobTypeArg, String rarityArg) {
        String mobType = mobTypeArg.contains(":") ? mobTypeArg : "minecraft:" + mobTypeArg;
        PetRarity rarity;
        try { rarity = PetRarity.valueOf(rarityArg.toUpperCase()); }
        catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal(
                    "[Debug] Unknown rarity. Use: common/uncommon/rare/epic/legendary/mythic"));
            return 0;
        }
        EnumMap<PetStat, Integer> maxStats = new EnumMap<>(PetStat.class);
        for (PetStat s : PetStat.values()) maxStats.put(s, 5);
        PetData pet = new PetData(UUID.randomUUID(), mobType, rarity, maxStats, false, null, 100, 100);
        ItemStack stack = new ItemStack(PetsModItems.PET_ITEM.get());
        pet.applyToStack(stack);
        giveOrDrop(player, stack);
        player.sendSystemMessage(Component.literal("[Debug] Given: " + rarity.getDisplayName() + " " + mobType + " (5* all stats)"));
        return Command.SINGLE_SUCCESS;
    }

    private static int addSkill(CommandSourceStack source, String skillId, int level)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PetData active = PetManager.getActivePetData(player.getUUID());
        if (active == null) { source.sendFailure(Component.literal("You need an active pet!")); return 0; }
        PetSkill skill = PetSkills.get(skillId);
        if (skill == null) { source.sendFailure(Component.literal("Skill not found: " + skillId)); return 0; }
        List<SkillInstance> skills = new ArrayList<>(active.skills());
        skills.add(new SkillInstance(skill, level, 1.0f));
        PetData newData = new PetData(active.petId(), active.mobType(), active.rarity(), active.stats(),
                active.modifierApplied(), active.customName(), active.hunger(), active.happiness(), skills);
        PetManager.forceUpdateActivePetData(player.getUUID(), newData);
        source.sendSuccess(() -> Component.literal("Added skill " + skillId + " (Lvl " + level + ") to active pet."), true);
        return 1;
    }

    private static int petHistory(CommandSourceStack src, ServerPlayer target)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!(src.getEntity() instanceof ServerPlayer op)) {
            src.sendFailure(Component.literal("Must be run by a player.")); return 0;
        }
        PetHistorySavedData hist = PetHistorySavedData.getOrCreate(src.getServer());
        int total = hist.totalCount(target.getUUID());
        if (total == 0) {
            src.sendSuccess(() -> Component.literal("§7No pet history for §e" + target.getGameProfile().getName()), false);
            return Command.SINGLE_SUCCESS;
        }
        com.arcadia.pets.server.PetHistoryMenu.openFor(op, hist.getAll(target.getUUID()));
        src.sendSuccess(() -> Component.literal("§6[Arcadia] Opening pet history for §e"
                + target.getGameProfile().getName() + " §7(" + total + " total)"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int petRestore(CommandSourceStack src, ServerPlayer target, String petIdStr)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        UUID petId;
        try { petId = UUID.fromString(petIdStr); }
        catch (IllegalArgumentException e) { src.sendFailure(Component.literal("Invalid UUID: " + petIdStr)); return 0; }
        PetHistorySavedData hist = PetHistorySavedData.getOrCreate(src.getServer());
        Optional<PetHistorySavedData.HistoryEntry> found = hist.findByPetId(target.getUUID(), petId);
        if (found.isEmpty()) {
            src.sendFailure(Component.literal("No pet with ID " + petIdStr + " found in " + target.getGameProfile().getName() + "'s history."));
            return 0;
        }
        PetData pet = PetData.fromTag(found.get().petTag());
        if (pet == null) { src.sendFailure(Component.literal("Pet data is corrupt for ID " + petIdStr)); return 0; }
        ItemStack restored = new ItemStack(PetsModItems.PET_ITEM.get());
        pet.applyToStack(restored);
        giveOrDrop(target, restored);
        String label = pet.rarity().getDisplayName() + " " + pet.mobType();
        src.sendSuccess(() -> Component.literal("§6[Arcadia] Restored §f" + label + " §6to §e" + target.getGameProfile().getName()), true);
        target.sendSystemMessage(Component.literal("§6[Arcadia] An admin has restored your pet: §f" + label));
        return Command.SINGLE_SUCCESS;
    }
}
