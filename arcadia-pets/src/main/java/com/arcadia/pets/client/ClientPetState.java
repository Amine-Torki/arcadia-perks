package com.arcadia.pets.client;

import com.arcadia.pets.item.PetBehaviourMode;

/** Stores the local player's active pet state, updated by S2CPetHpSync and S2CPetPanel. */
public final class ClientPetState {

    private static float            currentHp     = 0f;
    private static float            maxHp         = 0f;
    private static float            displayHp     = 0f; // smoothed for HUD animation
    private static boolean          active        = false;
    private static PetBehaviourMode behaviourMode = PetBehaviourMode.IDLE;
    private static String           petName       = "";
    private static String           mobType       = "";
    private static int              hunger        = 100;

    private ClientPetState() {}

    /**
     * Updates state from a server HP sync packet.
     * @return event code: 0=no change, 1=summoned, 2=recalled, 3=died
     */
    public static int updateAndGetEvent(float hp, float max, boolean petActive, String name, String mob, int hungerVal) {
        boolean wasActive = active;
        float   wasHp     = currentHp;

        currentHp = hp;
        maxHp     = max;
        petName   = name != null ? name : "";
        mobType   = mob  != null ? mob  : "";
        hunger    = hungerVal;

        if (!wasActive && petActive) {
            active    = true;
            displayHp = hp; // snap to avoid lerping from 0
            return 1;
        }
        if (wasActive && !petActive) {
            active = false;
            return wasHp > 0f ? 2 : 3;
        }
        active = petActive;
        return 0;
    }

    /** Smoothly lerp displayHp toward currentHp. Call once per render frame. */
    public static void tickDisplayHp() {
        if (!active || maxHp == 0f) { displayHp = 0f; return; }
        displayHp += (currentHp - displayHp) * 0.13f;
    }

    public static void updateBehaviour(int behavOrdinal) {
        PetBehaviourMode[] modes = PetBehaviourMode.values();
        if (behavOrdinal >= 0 && behavOrdinal < modes.length) {
            behaviourMode = modes[behavOrdinal];
        }
    }

    public static boolean isActive()          { return active && maxHp > 0f; }
    public static float   getCurrentHp()      { return currentHp; }
    public static float   getMaxHp()          { return maxHp; }
    public static float   getDisplayHp()      { return displayHp; }
    public static int     getHunger()         { return hunger; }
    public static String  getPetName()        { return petName; }
    public static String  getMobType()        { return mobType; }
    public static boolean isAttackMode()      { return behaviourMode == PetBehaviourMode.ATTACK; }
}
