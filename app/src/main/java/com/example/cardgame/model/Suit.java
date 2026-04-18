package com.example.cardgame.model;

public enum Suit {
    DIAMONDS("Diamonds", "♦", 1),
    CLUBS("Clubs", "♣", 2),
    HEARTS("Hearts", "♥", 3),
    SPADES("Spades", "♠", 4);

    private final String displayName;
    private final String symbol;  // 新增
    private final int weight;

    Suit(String displayName, String symbol, int weight) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.weight = weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSymbol() {  // 新增
        return symbol;
    }

    public int getWeight() {
        return weight;
    }
}