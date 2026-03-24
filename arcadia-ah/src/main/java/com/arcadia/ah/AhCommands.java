package com.arcadia.ah;

import com.arcadia.ah.auction.AuctionManager;
import com.arcadia.ah.server.AhLeaderboardMenu;
import com.arcadia.ah.server.AhDashboardBridge;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = ArcadiaAH.MOD_ID)
public final class AhCommands {

    private AhCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("ah")
                .executes(ctx -> {
                    if (!checkEnabled(ctx.getSource())) return 0;
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        AuctionManager.refreshCache();
                        AhDashboardBridge.openAhTab(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("top")
                    .executes(ctx -> {
                        if (!checkEnabled(ctx.getSource())) return 0;
                        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                            AhLeaderboardMenu.openFor(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("enable")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        AhGlobalFlags.AH_ENABLED = true;
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "§a[Arcadia] Auction House enabled for all players."), true);
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("disable")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        AhGlobalFlags.AH_ENABLED = false;
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "§c[Arcadia] Auction House disabled. Operators are unaffected."), true);
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("sell")
                    .then(Commands.argument("price", LongArgumentType.longArg(1))
                        .executes(ctx -> executeSell(ctx, -1))
                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 64))
                            .executes(ctx -> executeSell(ctx,
                                    IntegerArgumentType.getInteger(ctx, "quantity")))
                        )
                    )
                )
        );
    }

    private static boolean checkEnabled(net.minecraft.commands.CommandSourceStack src) {
        if (!AhGlobalFlags.AH_ENABLED
                && src.getEntity() instanceof ServerPlayer p
                && !p.hasPermissions(2)) {
            src.sendFailure(Component.literal("§c[Arcadia] The Auction House is currently disabled on this server."));
            return false;
        }
        return true;
    }

    private static int executeSell(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            int quantity) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        long price = LongArgumentType.getLong(ctx, "price");
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
