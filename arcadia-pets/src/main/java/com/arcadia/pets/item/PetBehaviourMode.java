package com.arcadia.pets.item;

public enum PetBehaviourMode {
    IDLE("arcadia_pets.pet.mode.idle"),
    DEFEND("arcadia_pets.pet.mode.defend"),
    ATTACK("arcadia_pets.pet.mode.attack");

    private final String label;

    PetBehaviourMode(String label) { this.label = label; }

    public String getTranslationKey() { return label; }
}
