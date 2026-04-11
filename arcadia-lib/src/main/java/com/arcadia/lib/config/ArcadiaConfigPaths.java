package com.arcadia.lib.config;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ArcadiaConfigPaths {
    private ArcadiaConfigPaths() {}

    public static Path arcadiaRoot() {
        return FMLPaths.CONFIGDIR.get().resolve("arcadia");
    }

    public static Path modRoot(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        return arcadiaRoot().resolve(namespace);
    }

    public static Path dataFile(String namespace, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        return modRoot(namespace).resolve(fileName);
    }
}
