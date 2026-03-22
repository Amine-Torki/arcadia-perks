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
    private static boolean debugMode = false;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    private DatabaseManager() {}

    /**
     * Called at mod startup. Uses in-memory mode when DebugMode is enabled,
     * otherwise connects to MySQL using ArcadiaConfig values.
     */
    public static void initialize() {
        if (com.arcadia.lib.DebugMode.ENABLED) {
            debugMode = true;
            LOGGER.info("[ArcadiaPrestige] DEBUG MODE — MySQL disabled, using in-memory storage.");
        } else {
            init(
                com.arcadia.lib.config.DatabaseConfig.DB_HOST,
                com.arcadia.lib.config.DatabaseConfig.DB_PORT,
                com.arcadia.lib.config.DatabaseConfig.DB_NAME,
                com.arcadia.lib.config.DatabaseConfig.DB_USER,
                com.arcadia.lib.config.DatabaseConfig.DB_PASS,
                com.arcadia.lib.config.DatabaseConfig.MAX_POOL_SIZE
            );
        }
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    public static void init(String host, int port, String dbName, String user, String pass, int maxPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&autoReconnect=true&allowPublicKeyRetrieval=true");
        config.setUsername(user);
        config.setPassword(pass);
        config.setPoolName("ArcadiaDashboard");
        config.setMaximumPoolSize(maxPoolSize);
        config.setConnectionTimeout(5000);
        config.setLeakDetectionThreshold(10000);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            dataSource = new HikariDataSource(config);
            LOGGER.info("ArcadiaDashboard database pool initialized.");
            createTables();
        } catch (Exception e) {
            LOGGER.error("Failed to initialize database connection pool", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (debugMode) {
            throw new SQLException("Debug mode active — no database connection available.");
        }
        if (dataSource == null) {
            throw new SQLException("DataSource is not initialized. Call init() first.");
        }
        return dataSource.getConnection();
    }

    private static void createTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS arcadia_prestige_player_data (
                        uuid VARCHAR(36) PRIMARY KEY,
                        grade VARCHAR(16),
                        particle_id VARCHAR(64) DEFAULT '',
                        last_claim BIGINT DEFAULT 0,
                        streak INT DEFAULT 0
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS arcadia_prestige_pet_registry (
                        pet_id VARCHAR(36) PRIMARY KEY,
                        owner_uuid VARCHAR(36),
                        mob_type VARCHAR(64),
                        rarity TINYINT,
                        total_stars TINYINT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_owner_uuid (owner_uuid)
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS arcadia_prestige_daily_milestone_claims (
                        uuid VARCHAR(36) NOT NULL,
                        cycle INT NOT NULL,
                        claims INT NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, cycle)
                    )
                    """);

            LOGGER.info("ArcadiaDashboard database tables verified.");
        } catch (SQLException e) {
            LOGGER.error("Failed to create database tables", e);
        }
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("ArcadiaDashboard database pool shut down.");
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
