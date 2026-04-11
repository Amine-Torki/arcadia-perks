package com.arcadia.lib.staff;

import com.arcadia.lib.ArcadiaLib;
import com.arcadia.lib.ArcadiaMessages;
import com.arcadia.lib.text.TextFormatter;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers the /staff command tree for moderation actions.
 */
@EventBusSubscriber(modid = ArcadiaLib.MOD_ID)
public final class StaffCommands {

    private StaffCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("staff")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> ArcadiaMessages.info(
                            "Staff commands: /staff chat, /staff toggle, /staff list, /staff kick, /staff ban, /staff mute, /staff unmute"), false);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("chat")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                            if (!StaffService.requireRole(ctx.getSource(), StaffRole.HELPER)) return 0;
                            StaffChatService.broadcast(sp, StringArgumentType.getString(ctx, "message"));
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(Commands.literal("toggle")
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                        if (!StaffService.requireRole(ctx.getSource(), StaffRole.HELPER)) return 0;
                        boolean on = StaffChatService.toggle(sp.getUUID());
                        sp.sendSystemMessage(ArcadiaMessages.info(
                                "Staff chat " + (on ? "enabled" : "disabled") + ". " +
                                (on ? "All your messages will go to staff chat." : "Normal chat restored.")));
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        if (!StaffService.requireRole(ctx.getSource(), StaffRole.HELPER)) return 0;
                        var staff = StaffService.getStaffOnline();
                        if (staff.isEmpty()) {
                            ctx.getSource().sendSuccess(() -> ArcadiaMessages.info("No staff online."), false);
                        } else {
                            StringBuilder sb = new StringBuilder("Staff online (" + staff.size() + "): ");
                            for (int i = 0; i < staff.size(); i++) {
                                if (i > 0) sb.append(", ");
                                ServerPlayer s = staff.get(i);
                                sb.append(StaffService.getRole(s).getDisplayName())
                                  .append(" ").append(s.getName().getString());
                            }
                            ctx.getSource().sendSuccess(() -> ArcadiaMessages.info(sb.toString()), false);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("kick")
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                            if (!StaffService.requireRole(ctx.getSource(), StaffRole.MOD)) return 0;
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            StaffActions.kick(target, sp, null);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                                if (!StaffService.requireRole(ctx.getSource(), StaffRole.MOD)) return 0;
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                StaffActions.kick(target, sp, StringArgumentType.getString(ctx, "reason"));
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                )
                .then(Commands.literal("ban")
                    .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("duration_minutes", LongArgumentType.longArg(0))
                            .executes(ctx -> {
                                if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                                if (!StaffService.requireRole(ctx.getSource(), StaffRole.MOD)) return 0;
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                long mins = LongArgumentType.getLong(ctx, "duration_minutes");
                                StaffActions.ban(target, sp, null, mins * 60_000);
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                                    if (!StaffService.requireRole(ctx.getSource(), StaffRole.MOD)) return 0;
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                    long mins = LongArgumentType.getLong(ctx, "duration_minutes");
                                    StaffActions.ban(target, sp, StringArgumentType.getString(ctx, "reason"), mins * 60_000);
                                    return Command.SINGLE_SUCCESS;
                                })
                            )
                        )
                    )
                )
                .then(Commands.literal("mute")
                    .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("duration_minutes", LongArgumentType.longArg(1))
                            .executes(ctx -> {
                                if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                                if (!StaffService.requireRole(ctx.getSource(), StaffRole.MOD)) return 0;
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                long mins = LongArgumentType.getLong(ctx, "duration_minutes");
                                StaffActions.mute(target.getUUID(), sp, null, mins * 60_000);
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                                    if (!StaffService.requireRole(ctx.getSource(), StaffRole.MOD)) return 0;
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                    long mins = LongArgumentType.getLong(ctx, "duration_minutes");
                                    StaffActions.mute(target.getUUID(), sp,
                                            StringArgumentType.getString(ctx, "reason"), mins * 60_000);
                                    return Command.SINGLE_SUCCESS;
                                })
                            )
                        )
                    )
                )
                .then(Commands.literal("unmute")
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                            if (!StaffService.requireRole(ctx.getSource(), StaffRole.MOD)) return 0;
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            StaffActions.unmute(target.getUUID(), sp);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
        );
    }
}
