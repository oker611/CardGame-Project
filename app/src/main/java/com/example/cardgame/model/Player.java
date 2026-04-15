package com.example.cardgame.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model representing a player in the game.
 */
public class Player {
    private final String id;
    private final String name;
    private List<Card> handCards; // Hand Cards

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
        this.handCards = new ArrayList<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<Card> getHandCards() { return handCards; }
    public void setHandCards(List<Card> handCards) { this.handCards = handCards; }
}