package com.arcadia.pets.item;

public enum PetBehaviourMode {
    IDLE("arcadia_prestige.pet.mode.idle"),
    DEFEND("arcadia_prestige.pet.mode.defend"),
    ATTACK("arcadia_prestige.pet.mode.attack");

    private final String label;

    PetBehaviourMode(String label) { this.label = label; }

    public String getTranslationKey() { return label; }
}
