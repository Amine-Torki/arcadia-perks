package com.arcadia.lib.util;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public final class ProfilerUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static MinecraftServer server;

    private ProfilerUtil() {}

    public static void setServer(MinecraftServer minecraftServer) {
        server = minecraftServer;
    }

    public static void clearServer() {
        server = null;
    }

    public static boolean startSection(String name) {
        if (server == null || name == null || name.isBlank()) return false;

        try {
            server.getProfiler().push(name);
            return true;
        } catch (Exception e) {
            LOGGER.error("Arcadia Core: failed to start profiler section '{}'", name, e);
            return false;
        }
    }

    public static boolean startSection(String namespace, String section) {
        return startSection(sectionName(namespace, section));
    }

    public static void endSection(boolean started) {
        if (!started || server == null) return;

        try {
            server.getProfiler().pop();
        } catch (Exception e) {
            LOGGER.error("Arcadia Core: failed to end profiler section", e);
        }
    }

    public static String sectionName(String namespace, String section) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        if (section == null || section.isBlank()) {
            throw new IllegalArgumentException("section must not be blank");
        }
        return sanitizeSegment(namespace) + "." + sanitizeSegment(section);
    }

    private static String sanitizeSegment(String segment) {
        String trimmed = segment.trim().toLowerCase(java.util.Locale.ROOT);
        StringBuilder safe = new StringBuilder(trimmed.length());

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                safe.append(c);
            } else {
                safe.append('_');
            }
        }

        return safe.toString();
    }
}
