package com.arcadia.lib.staff;

import com.arcadia.lib.player.PlayerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Staff-only chat channel. Messages are visible only to online staff members.
 */
public final class StaffChatService {

    /** Players who have staff chat toggled on (all their messages go to staff chat). */
    private static final Set<UUID> toggled = ConcurrentHashMap.newKeySet();

    private StaffChatService() {}

    /** Sends a message from a staff member to all online staff. */
    public static void broadcast(ServerPlayer sender, String message) {
        StaffRole role = StaffService.getRole(sender);
        Component msg = Component.literal("[Staff] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal(sender.getName().getString()).withStyle(role.getColor()))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));

        for (ServerPlayer sp : PlayerManager.getOnlinePlayers()) {
            if (StaffService.isStaff(sp)) sp.sendSystemMessage(msg);
        }
    }

    /** Sends a system alert to all online staff (no sender). */
    public static void broadcastAlert(String message) {
        Component msg = Component.literal("[Staff Alert] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal(message).withStyle(ChatFormatting.YELLOW));

        for (ServerPlayer sp : PlayerManager.getOnlinePlayers()) {
            if (StaffService.isStaff(sp)) sp.sendSystemMessage(msg);
        }
    }

    /** Returns true if the player has staff chat toggled on. */
    public static boolean isToggled(UUID uuid) { return toggled.contains(uuid); }

    /** Toggles staff chat mode for a player. Returns the new state. */
    public static boolean toggle(UUID uuid) {
        if (toggled.contains(uuid)) { toggled.remove(uuid); return false; }
        else { toggled.add(uuid); return true; }
    }

    /** Clears toggle state on disconnect. */
    public static void onDisconnect(UUID uuid) { toggled.remove(uuid); }
}
