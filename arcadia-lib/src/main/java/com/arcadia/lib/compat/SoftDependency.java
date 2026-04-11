package com.arcadia.lib.compat;

import net.neoforged.fml.ModList;

import java.util.Objects;

public final class SoftDependency {
    private final String modId;
    private final boolean loaded;

    public SoftDependency(String modId) {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.loaded = ModList.get().isLoaded(modId);
    }

    public String modId() {
        return modId;
    }

    public boolean isLoaded() {
        return loaded;
    }
}
