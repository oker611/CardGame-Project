package com.example.cardgame.model;

public enum Rank {
    THREE("3", 3),
    FOUR("4", 4),
    FIVE("5", 5),
    SIX("6", 6),
    SEVEN("7", 7),
    EIGHT("8", 8),
    NINE("9", 9),
    TEN("10", 10),
    JACK("J", 11),
    QUEEN("Q", 12),
    KING("K", 13),
    ACE("A", 14),
    TWO("2", 15);

    private final String displayName;
    private final int weight;

    Rank(String displayName, int weight) {
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