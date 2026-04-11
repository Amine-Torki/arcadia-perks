package com.arcadia.lib.staff;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side TOML config for staff permission nodes.
 * Loaded from {@code config/arcadia/lib/staff.toml}.
 */
public final class StaffConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> PERM_ADMIN;
    public static final ModConfigSpec.ConfigValue<String> PERM_MOD;
    public static final ModConfigSpec.ConfigValue<String> PERM_HELPER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Staff permission nodes for moderation features.").push("staff");
        PERM_ADMIN  = b.comment("Permission node for Admin role").define("admin", "arcadia.staff.admin");
        PERM_MOD    = b.comment("Permission node for Moderator role").define("mod", "arcadia.staff.mod");
        PERM_HELPER = b.comment("Permission node for Helper role").define("helper", "arcadia.staff.helper");
        b.pop();
        SPEC = b.build();
    }

    public static String NODE_ADMIN  = "arcadia.staff.admin";
    public static String NODE_MOD    = "arcadia.staff.mod";
    public static String NODE_HELPER = "arcadia.staff.helper";

    private StaffConfig() {}

    public static void apply() {
        NODE_ADMIN  = PERM_ADMIN.get();
        NODE_MOD    = PERM_MOD.get();
        NODE_HELPER = PERM_HELPER.get();
    }
}
