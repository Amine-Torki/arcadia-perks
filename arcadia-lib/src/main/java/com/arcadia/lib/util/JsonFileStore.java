package com.arcadia.lib.util;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class JsonFileStore<T> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Path path;
    private final Class<T> type;
    private final Supplier<T> defaultFactory;

    public JsonFileStore(Path path, Class<T> type, Supplier<T> defaultFactory) {
        this.path = Objects.requireNonNull(path, "path");
        this.type = Objects.requireNonNull(type, "type");
        this.defaultFactory = Objects.requireNonNull(defaultFactory, "defaultFactory");
    }

    public T loadOrCreate() {
        return load().orElseGet(defaultFactory);
    }

    public Optional<T> load() {
        if (!Files.exists(path)) return Optional.empty();

        try (Reader reader = Files.newBufferedReader(path)) {
            T value = ArcadiaGson.get().fromJson(reader, type);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            LOGGER.warn("Arcadia Core: failed to read JSON file {}", path, e);
            return Optional.empty();
        }
    }

    public boolean save(T value) {
        Objects.requireNonNull(value, "value");

        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                ArcadiaGson.get().toJson(value, writer);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException atomicMoveFailure) {
            return saveWithoutAtomicMove(value);
        } catch (Exception e) {
            LOGGER.error("Arcadia Core: failed to save JSON file {}", path, e);
            return false;
        }
    }

    private boolean saveWithoutAtomicMove(T value) {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                ArcadiaGson.get().toJson(value, writer);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            LOGGER.error("Arcadia Core: failed to save JSON file {}", path, e);
            return false;
        }
    }
}
