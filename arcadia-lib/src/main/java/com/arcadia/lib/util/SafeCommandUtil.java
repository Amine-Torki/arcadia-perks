package com.arcadia.lib.util;

import net.minecraft.server.MinecraftServer;

import java.util.Locale;
import java.util.UUID;

public final class SafeCommandUtil {
    private SafeCommandUtil() {}

    public static boolean isSafeUuid(String value) {
        if (value == null) return false;
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static String formatCoordinate(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("coordinate must be finite");
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static boolean runAsServer(MinecraftServer server, String command) {
        if (server == null || command == null || command.isBlank()) return false;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        return true;
    }
}
