package com.arcadia.lib.permissions;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side TOML config for grade permission nodes.
 * Loaded from {@code config/arcadia/lib/permissions.toml}.
 * Centralizes all rank/grade definitions so any Arcadia mod can check grades
 * without depending on a specific mod.
 */
public final class PermissionConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> PERM_VIP;
    public static final ModConfigSpec.ConfigValue<String> PERM_VIP_PLUS;
    public static final ModConfigSpec.ConfigValue<String> PERM_MVP;
    public static final ModConfigSpec.ConfigValue<String> PERM_FOUNDER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Grade permission nodes used by all Arcadia mods.",
                  "These are LuckPerms permission nodes checked to determine a player's rank.")
         .push("grades");

        PERM_VIP      = b.comment("Permission node for VIP grade")
                         .define("vip", "arcadia.grade.vip");
        PERM_VIP_PLUS = b.comment("Permission node for VIP+ grade")
                         .define("vip_plus", "arcadia.grade.vipplus");
        PERM_MVP      = b.comment("Permission node for MVP grade")
                         .define("mvp", "arcadia.grade.mvp");
        PERM_FOUNDER  = b.comment("Permission node for Founder cosmetic rank")
                         .define("founder", "arcadia.grade.founder");

        b.pop();
        SPEC = b.build();
    }

    // Applied values
    public static String GRADE_PERM_VIP      = "arcadia.grade.vip";
    public static String GRADE_PERM_VIP_PLUS = "arcadia.grade.vipplus";
    public static String GRADE_PERM_MVP      = "arcadia.grade.mvp";
    public static String GRADE_PERM_FOUNDER  = "arcadia.grade.founder";

    private PermissionConfig() {}

    public static void apply() {
        GRADE_PERM_VIP      = PERM_VIP.get();
        GRADE_PERM_VIP_PLUS = PERM_VIP_PLUS.get();
        GRADE_PERM_MVP      = PERM_MVP.get();
        GRADE_PERM_FOUNDER  = PERM_FOUNDER.get();
    }
}
