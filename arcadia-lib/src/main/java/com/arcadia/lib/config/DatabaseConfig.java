package com.arcadia.lib.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side TOML config for MySQL connection settings.
 *
 * <p>Loaded from {@code config/arcadia-database.toml}. Edit that file on the
 * server to set your DB credentials — no recompilation needed.</p>
 */
public final class DatabaseConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> HOST;
    public static final ModConfigSpec.IntValue            PORT;
    public static final ModConfigSpec.ConfigValue<String> NAME;
    public static final ModConfigSpec.ConfigValue<String> USER;
    public static final ModConfigSpec.ConfigValue<String> PASS;
    public static final ModConfigSpec.IntValue            CFG_MAX_POOL_SIZE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("MySQL connection settings for the Arcadia Prestige mod.").push("database");

        HOST = b.comment("Database host address")
                .define("host", "localhost");
        PORT = b.comment("Database port")
                .defineInRange("port", 3306, 1, 65535);
        NAME = b.comment("Database name")
                .define("name", "arcadia_prestige");
        USER = b.comment("Database username")
                .define("user", "arcadia_prestige");
        PASS = b.comment("Database password")
                .define("password", "arcadia_prestige");
        CFG_MAX_POOL_SIZE = b.comment("HikariCP connection pool size")
                .defineInRange("max_pool_size", 10, 1, 64);

        b.pop();
        SPEC = b.build();
    }

    // ── Applied values (populated by apply()) ────────────────────────────────

    public static String  DB_HOST       = "localhost";
    public static int     DB_PORT       = 3306;
    public static String  DB_NAME       = "arcadia_prestige";
    public static String  DB_USER       = "arcadia_prestige";
    public static String  DB_PASS       = "arcadia_prestige";
    public static int     MAX_POOL_SIZE = 10;

    private DatabaseConfig() {}

    /** Applies loaded config values into static fields for DatabaseManager. */
    public static void apply() {
        DB_HOST       = HOST.get();
        DB_PORT       = PORT.get();
        DB_NAME       = NAME.get();
        DB_USER       = USER.get();
        DB_PASS       = PASS.get();
        MAX_POOL_SIZE = CFG_MAX_POOL_SIZE.get();
    }
}
