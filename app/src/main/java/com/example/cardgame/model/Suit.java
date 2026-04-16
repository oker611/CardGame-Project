package com.example.cardgame.model;

public enum Suit {
    DIAMONDS("Diamonds", 1),
    CLUBS("Clubs", 2),
    HEARTS("Hearts", 3),
    SPADES("Spades", 4);

    private final String displayName;
    private final int weight;

    Suit(String displayName, int weight) {
        this.displayName = displayName;
        this.weight = weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWeight() {
        return weight;
    }
}