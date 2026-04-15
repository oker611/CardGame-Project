package com.example.cardgame.model;

/**
 * Data model representing a single playing card.
 */
public class Card {
    private final String id;
    private final Suit suit;
    private final Rank rank;

    public Card(String id, Suit suit, Rank rank) {
        this.id = id;
        this.suit = suit;
        this.rank = rank;
    }

    public String getId() { return id; }
    public Suit getSuit() { return suit; }
    public Rank getRank() { return rank; }
}
