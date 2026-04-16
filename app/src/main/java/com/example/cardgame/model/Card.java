package com.example.cardgame.model;

import java.util.Objects;

public class Card {
    private String cardId;
    private Suit suit;
    private Rank rank;

    public Card() {
    }

    public Card(String cardId, Suit suit, Rank rank) {
        this.cardId = cardId;
        this.suit = suit;
        this.rank = rank;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public Suit getSuit() {
        return suit;
    }

    public void setSuit(Suit suit) {
        this.suit = suit;
    }

    public Rank getRank() {
        return rank;
    }

    public void setRank(Rank rank) {
        this.rank = rank;
    }

    public String getDisplayText() {
        if (rank == null || suit == null) {
            return "";
        }
        return rank.getDisplayName() + " of " + suit.getDisplayName();
    }

    public int getRankWeight() {
        return rank == null ? 0 : rank.getWeight();
    }

    public int getSuitWeight() {
        return suit == null ? 0 : suit.getWeight();
    }

    public boolean isThreeOfDiamonds() {
        return rank == Rank.THREE && suit == Suit.DIAMONDS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Card)) {
            return false;
        }
        Card card = (Card) o;
        return Objects.equals(cardId, card.cardId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cardId);
    }

    @Override
    public String toString() {
        return "Card{" +
                "cardId='" + cardId + '\'' +
                ", suit=" + suit +
                ", rank=" + rank +
                '}';
    }
}
