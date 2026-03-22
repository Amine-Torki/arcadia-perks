package com.arcadia.pets.client;

/** Client-side settings for the pet rendering system. */
public final class PetClientSettings {

    private PetClientSettings() {}

    private static volatile boolean hideOwnEffectsFirstPerson = true;

    public static boolean isHideOwnEffectsFirstPerson() {
        return hideOwnEffectsFirstPerson;
    }

    public static void toggleHideOwnEffectsFirstPerson() {
        hideOwnEffectsFirstPerson = !hideOwnEffectsFirstPerson;
    }
}
