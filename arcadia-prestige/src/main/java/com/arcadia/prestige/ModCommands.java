package com.arcadia.prestige;

import com.arcadia.pets.PetsGlobalFlags;
import com.arcadia.prestige.network.PacketHandler;
import com.arcadia.prestige.network.S2COpenHub;
import com.arcadia.prestige.server.DashboardMenu;
import com.mojang.brigadier.Command;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
            Commands.literal("prestige")
                .executes(ctx -> {
                    if (!checkEnabled(ctx.getSource())) return 0;
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        PacketHandler.sendToPlayer(player, new S2COpenHub());
                    }
                    return Command.SINGLE_SUCCESS;
                })
        );

        event.getDispatcher().register(
            Commands.literal("cosmetics")
                .executes(ctx -> {
                    if (!checkEnabled(ctx.getSource())) return 0;
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        DashboardMenu.openFor(player, 0);
                    }
                    return Command.SINGLE_SUCCESS;
                })
        );

        event.getDispatcher().register(
            Commands.literal("daily")
                .executes(ctx -> {
                    if (!checkEnabled(ctx.getSource())) return 0;
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        DashboardMenu.openFor(player, 2);
                    }
                    return Command.SINGLE_SUCCESS;
                })
        );
    }

    static boolean checkEnabled(CommandSourceStack src) {
        if (!PetsGlobalFlags.PETS_ENABLED
                && src.getEntity() instanceof ServerPlayer p
                && !p.hasPermissions(2)) {
            src.sendFailure(Component.literal(
                    "§c[Arcadia] This feature is currently disabled on this server."));
            return false;
        }
        return true;
    }
}
