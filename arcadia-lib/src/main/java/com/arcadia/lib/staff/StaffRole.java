package com.arcadia.lib.staff;

import net.minecraft.ChatFormatting;

/**
 * Staff role hierarchy for all Arcadia mods.
 * Separate from the player grade system (VIP/VIP+/MVP) — staff roles
 * represent moderation authority, not gameplay perks.
 */
public enum StaffRole {
    NONE(0, "Player", ChatFormatting.GRAY),
    HELPER(1, "Helper", ChatFormatting.AQUA),
    MOD(2, "Moderator", ChatFormatting.GREEN),
    ADMIN(3, "Admin", ChatFormatting.RED);

    private final int level;
    private final String displayName;
    private final ChatFormatting color;

    StaffRole(int level, String displayName, ChatFormatting color) {
        this.level = level;
        this.displayName = displayName;
        this.color = color;
    }

    public int getLevel() { return level; }
    public String getDisplayName() { return displayName; }
    public ChatFormatting getColor() { return color; }

    /** Returns true if this role is at least the required level. */
    public boolean atLeast(StaffRole required) {
        return this.level >= required.level;
    }

    /** Returns true if this is any staff role (not NONE). */
    public boolean isStaff() {
        return this != NONE;
    }
}
