package com.arcadia.pets.item;

public enum PetMovementMode {
    FOLLOW("arcadia_prestige.pet.mode.follow"),
    POCKET("arcadia_prestige.pet.mode.pocket");

    private final String label;

    PetMovementMode(String label) { this.label = label; }

    public String getTranslationKey() { return label; }
}
