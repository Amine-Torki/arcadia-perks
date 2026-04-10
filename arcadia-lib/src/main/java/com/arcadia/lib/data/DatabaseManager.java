package com.arcadia.lib.data;

import com.mojang.logging.LogUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class DatabaseManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static HikariDataSource dataSource;
    private static boolean inMemoryMode = false;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    /** Registered table definitions from all Arcadia modules. */
    private static final java.util.List<TableDefinition> registeredTables = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Register a table definition. Call during FMLCommonSetupEvent. */
    public static void registerTables(TableDefinition def) {
        registeredTables.add(def);
    }

    private DatabaseManager() {}

    /**
     * Called at server startup. Determines the storage backend:
     * <ul>
     *   <li>DebugMode enabled → in-memory (no MySQL)</li>
     *   <li>Integrated server (singleplayer) → in-memory (no MySQL)</li>
     *   <li>Database config disabled → in-memory (no MySQL)</li>
     *   <li>Otherwise → connects to MySQL via HikariCP</li>
     * </ul>
     *
     * @param isDedicatedServer true when running on a dedicated server, false for integrated (singleplayer)
     */
    public static void initialize(boolean isDedicatedServer) {
        if (com.arcadia.lib.DebugMode.ENABLED) {
            inMemoryMode = true;
            LOGGER.info("[ArcadiaPrestige] DEBUG MODE — MySQL disabled, using in-memory storage.");
            return;
        }

        if (!isDedicatedServer) {
            inMemoryMode = true;
            LOGGER.info("[ArcadiaPrestige] Integrated server (singleplayer) detected — MySQL disabled, using world save data.");
            return;
        }

        if (!com.arcadia.lib.config.DatabaseConfig.DB_ENABLED) {
            inMemoryMode = true;
            LOGGER.info("[ArcadiaPrestige] Database disabled in config — using in-memory storage.");
            return;
        }

        init(
            com.arcadia.lib.config.DatabaseConfig.DB_HOST,
            com.arcadia.lib.config.DatabaseConfig.DB_PORT,
            com.arcadia.lib.config.DatabaseConfig.DB_NAME,
            com.arcadia.lib.config.DatabaseConfig.DB_USER,
            com.arcadia.lib.config.DatabaseConfig.DB_PASS,
            com.arcadia.lib.config.DatabaseConfig.MAX_POOL_SIZE
        );
    }

    /** Returns true when running without a MySQL connection (debug, singleplayer, or config disabled). */
    public static boolean isDebugMode() {
        return inMemoryMode;
    }

    /** Returns true when a live MySQL connection pool is active. */
    public static boolean isDatabaseActive() {
        return !inMemoryMode && dataSource != null && !dataSource.isClosed();
    }

    public static void init(String host, int port, String dbName, String user, String pass, int maxPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&autoReconnect=true&allowPublicKeyRetrieval=true");
        config.setUsername(user);
        config.setPassword(pass);
        config.setPoolName("ArcadiaLib");
        config.setMaximumPoolSize(maxPoolSize);
        config.setConnectionTimeout(5000);
        config.setLeakDetectionThreshold(10000);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            dataSource = new HikariDataSource(config);
            LOGGER.info("ArcadiaLib database pool initialized.");
            createTables();
        } catch (Exception e) {
            LOGGER.error("Failed to initialize database connection pool", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (inMemoryMode) {
            throw new SQLException("Debug mode active — no database connection available.");
        }
        if (dataSource == null) {
            throw new SQLException("DataSource is not initialized. Call init() first.");
        }
        return dataSource.getConnection();
    }

    /**
     * Creates all tables registered by Arcadia modules via {@link #registerTables(TableDefinition)}.
     * Called automatically after the connection pool initializes.
     */
    private static void createTables() {
        if (registeredTables.isEmpty()) {
            LOGGER.info("[ArcadiaLib] No table definitions registered.");
            return;
        }
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            for (TableDefinition def : registeredTables) {
                for (String sql : def.createTableStatements()) {
                    stmt.executeUpdate(sql);
                }
                LOGGER.info("[ArcadiaLib] Tables verified for module: {}", def.moduleId());
            }
        } catch (SQLException e) {
            LOGGER.error("[ArcadiaLib] Failed to create database tables", e);
        }
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("ArcadiaLib database pool shut down.");
        }
        EXECUTOR.shutdown();
    }

    public static void executeAsync(Runnable task) {
        CompletableFuture.runAsync(task, EXECUTOR).exceptionally(ex -> {
            LOGGER.error("Async database task failed", ex);
            return null;
        });
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, EXECUTOR).exceptionally(ex -> {
            LOGGER.error("Async database supply task failed", ex);
            return null;
        });
    }
}
