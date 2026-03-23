package com.arcadia.prestige.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side TOML config for Arcadia Prestige grade and branding settings.
 *
 * <p>Loaded from {@code config/arcadia-prestige.toml}. Edit that file to match
 * your server's LuckPerms permission nodes without recompiling.</p>
 *
 * <h3>Sections</h3>
 * <ul>
 *   <li>{@code [grades]} — LuckPerms permission nodes for each rank tier</li>
 *   <li>{@code [server]} — server identity for cross-server features (Auction House)</li>
 * </ul>
 */
public final class PrestigeConfig {

    public static final ModConfigSpec SPEC;

    // ── [grades] ──────────────────────────────────────────────────────────────

    /** LuckPerms permission node that identifies a VIP player. */
    public static final ModConfigSpec.ConfigValue<String> PERM_VIP;
    /** LuckPerms permission node that identifies a VIP+ player. */
    public static final ModConfigSpec.ConfigValue<String> PERM_VIP_PLUS;
    /** LuckPerms permission node that identifies an MVP player. */
    public static final ModConfigSpec.ConfigValue<String> PERM_MVP;
    /** LuckPerms permission node that identifies a Founder player (cosmetic add-on). */
    public static final ModConfigSpec.ConfigValue<String> PERM_FOUNDER;

    // ── [server] ──────────────────────────────────────────────────────────────

    /** Unique ID for this server instance; used in the AH to tag listings by origin server. */
    public static final ModConfigSpec.ConfigValue<String> SERVER_ID;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        // ── [grades] ──────────────────────────────────────────────────────────
        b.comment(
                "LuckPerms permission nodes for each Arcadia rank.",
                "Change these to match your server's existing permission setup.",
                "Players need the listed permission (directly or via group inheritance) to be recognized as that rank."
        ).push("grades");

        PERM_VIP = b
                .comment("Permission node for VIP rank.")
                .define("perm_vip", "arcadia.grade.vip");

        PERM_VIP_PLUS = b
                .comment("Permission node for VIP+ rank.")
                .define("perm_vip_plus", "arcadia.grade.vipplus");

        PERM_MVP = b
                .comment("Permission node for MVP rank.")
                .define("perm_mvp", "arcadia.grade.mvp");

        PERM_FOUNDER = b
                .comment("Permission node for Founder cosmetic rank (particle effects only, not a gameplay tier).")
                .define("perm_founder", "arcadia.grade.founder");

        b.pop();

        // ── [server] ──────────────────────────────────────────────────────────
        b.comment(
                "Server identity settings.",
                "These only matter when running multiple servers that share the same AH database."
        ).push("server");

        SERVER_ID = b
                .comment(
                        "Unique identifier for this server.",
                        "AH listings are tagged with this ID so buyers can see which server the item is from.",
                        "Must be unique across all servers sharing the same database."
                )
                .define("server_id", "server1");

        b.pop();
        SPEC = b.build();
    }

    // ── Applied values ────────────────────────────────────────────────────────

    public static String GRADE_PERM_VIP      = "arcadia.grade.vip";
    public static String GRADE_PERM_VIP_PLUS = "arcadia.grade.vipplus";
    public static String GRADE_PERM_MVP      = "arcadia.grade.mvp";
    public static String GRADE_PERM_FOUNDER  = "arcadia.grade.founder";
    public static String CACHED_SERVER_ID    = "server1";

    private PrestigeConfig() {}

    /** Applies loaded config values into static fields. Called by ArcadiaDashboard on config load. */
    public static void apply() {
        GRADE_PERM_VIP      = PERM_VIP.get();
        GRADE_PERM_VIP_PLUS = PERM_VIP_PLUS.get();
        GRADE_PERM_MVP      = PERM_MVP.get();
        GRADE_PERM_FOUNDER  = PERM_FOUNDER.get();
        CACHED_SERVER_ID    = SERVER_ID.get();
        // Propagate server ID to arcadia-lib so modules without prestige dependency can read it
        com.arcadia.lib.ServerContext.SERVER_ID = CACHED_SERVER_ID;
    }
}
