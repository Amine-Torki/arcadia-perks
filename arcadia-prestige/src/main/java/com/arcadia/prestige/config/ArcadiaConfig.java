package com.arcadia.prestige.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static configuration holder for the Arcadia Dashboard mod.
 * Values are set at startup and can be overridden by a config file in a future update.
 */
public final class ArcadiaConfig {

    private ArcadiaConfig() {
    }

    // ---- Server identity (cross-server AH) ----

    /** Unique ID for this server instance. Set via JVM property -Darcadia.server_id=server1 */
    public static String SERVER_ID = System.getProperty("arcadia.server_id", "server1");

    // ---- Database ----

    public static String DB_HOST = "localhost";
    public static int DB_PORT = 3306;
    public static String DB_NAME = "arcadia_prestige";
    public static String DB_USER = "arcadia_prestige";
    public static String DB_PASS = "arcadia_prestige";
    public static int MAX_POOL_SIZE = 10;

    // ---- Particle rendering ----

    /** Maximum squared distance (in blocks) at which particles are visible. 30 blocks squared. */
    public static double PARTICLE_MAX_DIST_SQ = 900.0;

    /** Squared distance (in blocks) at which particles are rendered at reduced quality. 10 blocks squared. */
    public static double PARTICLE_REDUCED_DIST_SQ = 100.0;

    /** Maximum number of particle emitters visible at once per client. */
    public static int PARTICLE_MAX_VISIBLE = 10;

    // ---- VIP Grade Permissions (LuckPerms nodes) ----

    public static final Map<String, String> GRADE_PERMISSIONS = new LinkedHashMap<>();

    static {
        GRADE_PERMISSIONS.put("VIP",     "arcadia.grade.vip");
        GRADE_PERMISSIONS.put("VIP+",    "arcadia.grade.vipplus");
        GRADE_PERMISSIONS.put("MVP",     "arcadia.grade.mvp");
        GRADE_PERMISSIONS.put("Founder", "arcadia.grade.founder"); // cosmetic add-on, not a gameplay rank
    }

    // ---- Daily Reward Streak Bonuses ----

    /** Number of bonus star essence given per streak milestone. */
    public static final Map<Integer, Integer> DAILY_STREAK_BONUSES = new LinkedHashMap<>();

    static {
        DAILY_STREAK_BONUSES.put(7, 1);    // 7-day streak: +1 bonus star essence
        DAILY_STREAK_BONUSES.put(14, 2);   // 14-day streak: +2 bonus star essence
        DAILY_STREAK_BONUSES.put(30, 3);   // 30-day streak: +3 bonus star essence
        DAILY_STREAK_BONUSES.put(60, 5);   // 60-day streak: +5 bonus star essence
        DAILY_STREAK_BONUSES.put(90, 8);   // 90-day streak: +8 bonus star essence
    }

    // ---- Star Weight Table ----

    /** Base weights for star rolling: index 0 = 1-star, index 4 = 5-star. */
    public static final int[] STAR_WEIGHTS = {40, 30, 18, 9, 3};
}
