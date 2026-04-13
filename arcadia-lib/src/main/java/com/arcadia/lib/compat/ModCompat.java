package com.arcadia.lib.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ModCompat {
    private static final Map<String, SoftDependency> CUSTOM_DEPS = new ConcurrentHashMap<>();

    public static final SoftDependency LUCKPERMS = new SoftDependency("luckperms");
    public static final SoftDependency SPARK = new SoftDependency("spark");
    public static final SoftDependency EASY_NPC = new SoftDependency("easy_npc");
    public static final SoftDependency NUMISMATICS = new SoftDependency("numismatics");

    private ModCompat() {}

    public static boolean isLoaded(String modId) {
        if (modId == null || modId.isBlank()) return false;
        return dependency(modId).isLoaded();
    }

    public static SoftDependency dependency(String modId) {
        return CUSTOM_DEPS.computeIfAbsent(modId, SoftDependency::new);
    }
}
