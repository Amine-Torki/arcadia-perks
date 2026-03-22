package com.arcadia.prestige;

import com.arcadia.ah.auction.AuctionManager;
import com.arcadia.lib.data.PlayerDataHandler;
import com.arcadia.lib.DebugMode;
import com.arcadia.pets.PetsModItems;
import com.arcadia.pets.item.*;
import com.arcadia.pets.item.PetRoller;
import com.arcadia.pets.item.PetStat;
import com.arcadia.prestige.network.PacketHandler;
import com.arcadia.prestige.network.S2COpenHub;
import com.arcadia.ah.server.AhLeaderboardMenu;
import com.arcadia.prestige.server.DashboardMenu;
import com.arcadia.pets.server.PetHistorySavedData;
import com.arcadia.pets.skill.PetSkill;
import com.arcadia.pets.skill.PetSkills;
import com.arcadia.pets.skill.SkillInstance;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers all Arcadia commands.
 *
 * /prestige              — opens the hub launcher
 * /prestige daily        — claim daily reward from chat
 * /pets                  — opens the Pets tab
 * /pets bag <tier>       — [DEBUG] give a pet bag
 * /pets essence          — [DEBUG] give a Star Essence
 * /pets pet <mob> [rar]  — [DEBUG] give a specific pet
 * /pets skill <id> [lv]  — [DEBUG] add skill to active pet
 * /pets roll <rarity>    — [DEBUG] real roll, cheated stats
 * /pets history <player> — [OP] open pet history GUI
 * /pets restore <p> <id> — [OP] restore a logged pet
 * /cosmetics             — opens the Cosmetics tab
 * /daily                 — opens the Daily Reward tab
 * /ah                    — opens the Auction House tab
 * /ah sell <price>       — list held item on the AH
 * /fuse                  — opens the Pet Fusion altar
 */
@EventBusSubscriber(modid = ArcadiaDashboard.MOD_ID)
public final class ModCommands {

    private ModCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // /prestige — open hub launcher (client-side full-screen menu)
        event.getDispatcher().register(
            Commands.literal("prestige")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        PacketHandler.sendToPlayer(player, new S2COpenHub());
                    }
                    return Command.SINGLE_SUCCESS;
                })
        );

        // /pets — opens the Pets tab; pet-related debug/admin subcommands live here
        event.getDispatcher().register(
            Commands.literal("pets")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        DashboardMenu.openFor(player, 1);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                // /pets bag <tier> — debug: give a pet bag
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
                            player.sendSystemMessage(Component.literal(
                                "[Debug] Given: " + bag.getHoverName().getString()));
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                // /pets essence — debug: give Star Essence
                .then(Commands.literal("essence")
                    .requires(src -> DebugMode.ENABLED || src.hasPermission(2))
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                        ItemStack essence = new ItemStack(PetsModItems.STAR_ESSENCE.get());
                        giveOrDrop(player, essence);
                        player.sendSystemMessage(Component.literal("[Debug] Given: Star Essence"));
                        return Command.SINGLE_SUCCESS;
                    })
                )
                // /pets pet <mobtype> [rarity] — debug: give a specific mob as a pet
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
                // /pets skill <id> [level] — debug: add a skill to the active pet
                .then(Commands.literal("skill")
                    .requires(s -> DebugMode.ENABLED && DebugMode.isDebugSource(s))
                    .then(Commands.argument("id", StringArgumentType.string())
                        .executes(ctx -> addSkill(ctx.getSource(), StringArgumentType.getString(ctx, "id"), 1))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                            .executes(ctx -> addSkill(ctx.getSource(), StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "level")))
                        )
                    )
                )
                // /pets roll <rarity> — debug: real roll (mob+skills) with all stats at 5★
                .then(Commands.literal("roll")
                    .requires(src -> DebugMode.ENABLED || src.hasPermission(2))
                    .then(Commands.argument("rarity", StringArgumentType.word())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                            String rarityArg = StringArgumentType.getString(ctx, "rarity").toUpperCase();
                            PetRarity floor;
                            try {
                                floor = PetRarity.valueOf(rarityArg);
                            } catch (IllegalArgumentException e) {
                                player.sendSystemMessage(Component.literal(
                                    "[Debug] Unknown rarity. Use: common/uncommon/rare/epic/legendary/mythic"));
                                return 0;
                            }
                            PetData rolled = PetRoller.roll(floor);
                            java.util.EnumMap<PetStat, Integer> maxStats = new java.util.EnumMap<>(PetStat.class);
                            for (PetStat s : PetStat.values()) maxStats.put(s, 5);
                            PetData pet = new PetData(rolled.petId(), rolled.mobType(), rolled.rarity(),
                                    maxStats, rolled.modifierApplied(), rolled.customName(),
                                    rolled.hunger(), rolled.happiness(), rolled.skills());
                            ItemStack stack = new ItemStack(PetsModItems.PET_ITEM.get());
                            pet.applyToStack(stack);
                            giveOrDrop(player, stack);
                            player.sendSystemMessage(Component.literal(
                                "[Debug] Rolled: " + pet.rarity().getDisplayName() + " " + pet.mobType()
                                + " (30★ / cheated)"));
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                // /pets rank <grade> — debug: simulate a gameplay rank (none/vip/vip+/mvp/founder)
                .then(Commands.literal("rank")
                    .requires(src -> DebugMode.ENABLED || src.hasPermission(2))
                    .then(Commands.argument("grade", StringArgumentType.word())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                            String grade = StringArgumentType.getString(ctx, "grade").toLowerCase();
                            java.util.Set<String> valid = java.util.Set.of("none", "default", "vip", "vip+", "mvp", "founder");
                            if (!valid.contains(grade)) {
                                player.sendSystemMessage(Component.literal(
                                    "[Debug] Unknown grade. Use: none | vip | vip+ | mvp | founder"));
                                return 0;
                            }
                            DebugMode.setDebugGrade(player.getUUID(), grade);
                            player.sendSystemMessage(Component.literal(
                                "[Debug] Simulated rank set to: §e" + grade));
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                // /pets simday [target] — op: subtract 24h from lastClaim so daily is claimable again
                .then(Commands.literal("simday")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer self)) return 0;
                        PlayerDataHandler.advanceDay(self.getUUID());
                        ctx.getSource().sendSuccess(() -> Component.literal("[Debug] +24h applied to your daily timer."), false);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            PlayerDataHandler.advanceDay(target.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.literal("[Debug] +24h applied to " + target.getName().getString() + "'s daily timer."), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                // /pets history <player> — op: open pet history GUI for a player
                .then(Commands.literal("history")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> petHistory(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "target"), 1))
                    )
                )
                // /pets restore <player> <petId> — op: give a logged pet back to a player
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
        );

        // /cosmetics — opens the dashboard on the Cosmetics tab
        event.getDispatcher().register(
            Commands.literal("cosmetics")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        DashboardMenu.openFor(player, 0);
                    }
                    return Command.SINGLE_SUCCESS;
                })
        );

        // /daily — opens the dashboard on the Daily Reward tab
        event.getDispatcher().register(
            Commands.literal("daily")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        DashboardMenu.openFor(player, 2);
                    }
                    return Command.SINGLE_SUCCESS;
                })
        );

        // /ah — opens the Auction House tab; /ah sell <price> to list held item
        event.getDispatcher().register(
            Commands.literal("ah")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        AuctionManager.refreshCache();
                        DashboardMenu.openFor(player, 3);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("top")
                    .executes(ctx -> {
                        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                            AhLeaderboardMenu.openFor(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("sell")
                    .then(Commands.argument("price", com.mojang.brigadier.arguments.LongArgumentType.longArg(1))
                        .executes(ctx -> executeSell(ctx, -1))
                        .then(Commands.argument("quantity", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                            .executes(ctx -> executeSell(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "quantity")))
                        )
                    )
                )
        );

        // /fuse — opens the Pet Fusion altar
        event.getDispatcher().register(
            Commands.literal("fuse")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        com.arcadia.pets.server.FusionMenu.openFor(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })
        );
    }

    /**
     * [Debug] Gives the player a pet of the specified mob type and rarity floor with all stats at 5 stars.
     * mobtype may be a short name ("warden") or a full namespaced id ("minecraft:warden").
     */
    private static int giveDebugPet(ServerPlayer player, String mobTypeArg, String rarityArg) {
        String mobType = mobTypeArg.contains(":") ? mobTypeArg : "minecraft:" + mobTypeArg;
        PetRarity rarity;
        try {
            rarity = PetRarity.valueOf(rarityArg.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal(
                "[Debug] Unknown rarity. Use: common/uncommon/rare/epic/legendary/mythic"));
            return 0;
        }
        java.util.EnumMap<PetStat, Integer> maxStats = new java.util.EnumMap<>(PetStat.class);
        for (PetStat s : PetStat.values()) maxStats.put(s, 5);
        PetData pet = new PetData(UUID.randomUUID(), mobType, rarity,
                maxStats, false, null, 100, 100);
        ItemStack stack = new ItemStack(PetsModItems.PET_ITEM.get());
        pet.applyToStack(stack);
        giveOrDrop(player, stack);
        player.sendSystemMessage(Component.literal(
            "[Debug] Given: " + rarity.getDisplayName() + " " + mobType + " (5* all stats)"));
        return Command.SINGLE_SUCCESS;
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static int petHistory(CommandSourceStack src, ServerPlayer target, int page)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!(src.getEntity() instanceof ServerPlayer op)) {
            src.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        PetHistorySavedData hist = PetHistorySavedData.getOrCreate(src.getServer());
        int total = hist.totalCount(target.getUUID());
        if (total == 0) {
            src.sendSuccess(() -> Component.literal(
                "§7No pet history for §e" + target.getGameProfile().getName()), false);
            return Command.SINGLE_SUCCESS;
        }
        // Show up to 54 entries newest-first (one GUI page)
        java.util.List<PetHistorySavedData.HistoryEntry> entries = hist.getAll(target.getUUID());
        com.arcadia.pets.server.PetHistoryMenu.openFor(op, entries);
        src.sendSuccess(() -> Component.literal(
            "§6[Arcadia] Opening pet history for §e" + target.getGameProfile().getName()
            + " §7(" + total + " total)"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int petRestore(CommandSourceStack src, ServerPlayer target, String petIdStr)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        UUID petId;
        try {
            petId = UUID.fromString(petIdStr);
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Invalid UUID: " + petIdStr));
            return 0;
        }

        PetHistorySavedData hist = PetHistorySavedData.getOrCreate(src.getServer());
        java.util.Optional<PetHistorySavedData.HistoryEntry> found = hist.findByPetId(target.getUUID(), petId);
        if (found.isEmpty()) {
            src.sendFailure(Component.literal("No pet with ID " + petIdStr
                + " found in " + target.getGameProfile().getName() + "'s history."));
            return 0;
        }

        PetHistorySavedData.HistoryEntry entry = found.get();
        PetData pet = PetData.fromTag(entry.petTag());
        if (pet == null) {
            src.sendFailure(Component.literal("Pet data is corrupt for ID " + petIdStr));
            return 0;
        }

        ItemStack restored = new ItemStack(PetsModItems.PET_ITEM.get());
        pet.applyToStack(restored);
        giveOrDrop(target, restored);

        String label = pet.rarity().getDisplayName() + " " + pet.mobType();
        src.sendSuccess(() -> Component.literal(
            "§6[Arcadia] Restored §f" + label + " §6to §e" + target.getGameProfile().getName()), true);
        target.sendSystemMessage(Component.literal(
            "§6[Arcadia] An admin has restored your pet: §f" + label));
        return Command.SINGLE_SUCCESS;
    }

    private static int addSkill(CommandSourceStack source, String skillId, int level) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PetData active = com.arcadia.pets.server.PetManager.getActivePetData(player.getUUID());
        if (active == null) {
            source.sendFailure(Component.literal("You need an active pet to add a skill!"));
            return 0;
        }

        PetSkill skill = PetSkills.get(skillId);
        if (skill == null) {
            source.sendFailure(Component.literal("Skill not found: " + skillId));
            return 0;
        }

        List<SkillInstance> skills = new ArrayList<>(active.skills());
        skills.add(new SkillInstance(skill, level, 1.0f));
        
        PetData newData = new PetData(
            active.petId(), active.mobType(), active.rarity(), active.stats(),
            active.modifierApplied(), active.customName(), active.hunger(), active.happiness(),
            skills
        );
        
        com.arcadia.pets.server.PetManager.forceUpdateActivePetData(player.getUUID(), newData);
        source.sendSuccess(() -> Component.literal("Added skill " + skillId + " (Lvl " + level + ") to your active pet."), true);
        return 1;
    }

    /** Shared handler for /ah sell <price> [quantity]. quantity=-1 means default to 1. */
    private static int executeSell(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx, int quantity) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        long price = com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "price");
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Hold the item you want to sell."));
            return 0;
        }
        int count = (quantity == -1) ? 1 : Math.min(quantity, held.getCount());
        ItemStack toList = held.copyWithCount(count);
        if (AuctionManager.listItem(player, toList, price, player.getServer())) {
            held.shrink(count);
        }
        return Command.SINGLE_SUCCESS;
    }
}
