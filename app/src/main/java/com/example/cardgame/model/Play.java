package com.example.cardgame.model;

import java.util.ArrayList;
import java.util.List;

public class Play {
    private String playerId;
    private List<Card> cards;
    private CardPattern pattern;

    public Play() {
        this.cards = new ArrayList<>();
        this.pattern = CardPattern.INVALID;
    }

    public Play(String playerId, List<Card> cards, CardPattern pattern) {
        this.playerId = playerId;
        this.cards = cards != null ? new ArrayList<>(cards) : new ArrayList<>();
        this.pattern = pattern != null ? pattern : CardPattern.INVALID;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public List<Card> getCards() {
        return cards;
    }

    public void setCards(List<Card> cards) {
        this.cards = cards != null ? new ArrayList<>(cards) : new ArrayList<>();
    }

    public CardPattern getPattern() {
        return pattern;
    }

    public void setPattern(CardPattern pattern) {
        this.pattern = pattern != null ? pattern : CardPattern.INVALID;
    }

    public boolean isEmpty() {
        return cards == null || cards.isEmpty();
    }

    public int getCardCount() {
        return cards == null ? 0 : cards.size();
    }

    public boolean containsThreeOfDiamonds() {
        if (cards == null) {
            return false;
        }
        for (Card card : cards) {
            if (card != null && card.isThreeOfDiamonds()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "Play{" +
                "playerId='" + playerId + '\'' +
                ", cards=" + cards +
                ", pattern=" + pattern +
                '}';
    }
}
