package com.example.cardgame.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class Player {
    private String playerId;
    private String playerName;
    private List<Card> handCards;
    private boolean passed;

    public Player() {
        this.handCards = new ArrayList<>();
        this.passed = false;
    }

    public Player(String playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.handCards = new ArrayList<>();
        this.passed = false;
    }

    public Player(String playerId, String playerName, List<Card> handCards, boolean passed) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.handCards = handCards != null ? new ArrayList<>(handCards) : new ArrayList<>();
        this.passed = passed;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public List<Card> getHandCards() {
        return handCards;
    }

    public void setHandCards(List<Card> handCards) {
        this.handCards = handCards != null ? new ArrayList<>(handCards) : new ArrayList<>();
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public void addCard(Card card) {
        if (card != null) {
            handCards.add(card);
        }
    }

    public void addCards(List<Card> cards) {
        if (cards == null) {
            return;
        }
        for (Card card : cards) {
            addCard(card);
        }
    }

    public boolean removeCard(Card targetCard) {
        if (targetCard == null) {
            return false;
        }
        return handCards.remove(targetCard);
    }

    public boolean removeCardById(String cardId) {
        if (cardId == null) {
            return false;
        }

        Iterator<Card> iterator = handCards.iterator();
        while (iterator.hasNext()) {
            Card card = iterator.next();
            if (cardId.equals(card.getCardId())) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public List<Card> findCardsByIds(List<String> cardIds) {
        List<Card> result = new ArrayList<>();
        if (cardIds == null) {
            return result;
        }

        for (String cardId : cardIds) {
            Card card = findCardById(cardId);
            if (card != null) {
                result.add(card);
            }
        }
        return result;
    }

    public Card findCardById(String cardId) {
        if (cardId == null) {
            return null;
        }

        for (Card card : handCards) {
            if (cardId.equals(card.getCardId())) {
                return card;
            }
        }
        return null;
    }

    public boolean hasCard(String cardId) {
        return findCardById(cardId) != null;
    }

    public int getRemainingCardCount() {
        return handCards == null ? 0 : handCards.size();
    }

    public void clearPassStatus() {
        this.passed = false;
    }

    @Override
    public String toString() {
        return "Player{" +
                "playerId='" + playerId + '\'' +
                ", playerName='" + playerName + '\'' +
                ", handCards=" + handCards +
                ", passed=" + passed +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Player)) {
            return false;
        }
        Player player = (Player) o;
        return Objects.equals(playerId, player.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId);
    }
}