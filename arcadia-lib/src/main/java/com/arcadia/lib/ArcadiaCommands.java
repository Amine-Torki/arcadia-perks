package com.arcadia.lib;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers the root /arcadia command that opens the main hub.
 * Available to all players as long as a hub opener is registered.
 */
@EventBusSubscriber(modid = ArcadiaLib.MOD_ID)
public final class ArcadiaCommands {

    private ArcadiaCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var arcadiaNode = event.getDispatcher().register(
            Commands.literal("arcadia")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        if (ArcadiaModRegistry.isHubAvailable()) {
                            ArcadiaModRegistry.openHub(player);
                        } else {
                            player.sendSystemMessage(Component.translatable("arcadia_lib.msg.hub_not_available"));
                        }
                    }
                    return Command.SINGLE_SUCCESS;
                })
        );
        // /acd is a shorthand alias for /arcadia — all subcommands are shared
        event.getDispatcher().register(Commands.literal("acd").redirect(arcadiaNode));
    }
}
