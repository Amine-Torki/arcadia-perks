package com.arcadia.prestige;

import com.arcadia.lib.data.PlayerDataHandler;
import com.arcadia.prestige.network.PacketHandler;
import com.arcadia.prestige.server.DashboardMenu;
import com.mojang.brigadier.Command;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers core Arcadia Prestige commands.
 * /pets, /fuse  → arcadia-pets  (PetsCommands)
 * /ah           → arcadia-ah   (AhCommands)
 */
@EventBusSubscriber(modid = ArcadiaDashboard.MOD_ID)
public final class ModCommands {

    private ModCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {

        event.getDispatcher().register(
            Commands.literal("arcadia_prestige")
                .executes(ctx -> {
                    if (!checkEnabled(ctx.getSource())) return 0;
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        com.arcadia.lib.network.ArcadiaLibNet.sendOpenHub(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("enable")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        PrestigeGlobalFlags.PRESTIGE_ENABLED = true;
                        ctx.getSource().sendSuccess(() -> Component.translatable(
                                "arcadia_prestige.cmd.enabled"), true);
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("disable")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        PrestigeGlobalFlags.PRESTIGE_ENABLED = false;
                        ctx.getSource().sendSuccess(() -> Component.translatable(
                                "arcadia_prestige.cmd.disabled"), true);
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("cosmetics")
                    .executes(ctx -> {
                        if (!checkEnabled(ctx.getSource())) return 0;
                        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                            DashboardMenu.openFor(player, 0);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("daily")
                    .executes(ctx -> {
                        if (!checkEnabled(ctx.getSource())) return 0;
                        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                            DashboardMenu.openFor(player, 2);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.literal("simday")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer self)) return 0;
                            PlayerDataHandler.advanceDay(self.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "arcadia_prestige.cmd.simday_self"), false);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("target", EntityArgument.player())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                PlayerDataHandler.advanceDay(target.getUUID());
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "[Debug] +24h applied to " + target.getName().getString() + "'s daily timer."), false);
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                )
        );
    }

    static boolean checkEnabled(CommandSourceStack src) {
        if (!PrestigeGlobalFlags.PRESTIGE_ENABLED
                && src.getEntity() instanceof ServerPlayer p
                && !p.hasPermissions(2)) {
            src.sendFailure(Component.translatable(
                    "arcadia_prestige.cmd.disabled_msg"));
            return false;
        }
        return true;
    }
}
