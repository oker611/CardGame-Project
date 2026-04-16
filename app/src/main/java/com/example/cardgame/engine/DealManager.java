package com.example.cardgame.engine;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manager responsible for generating the deck, shuffling, and dealing cards.
 */
public class DealManager {

    /**
     * Shuffle and deal 13 cards to each player, and determine the opening player.
     */
    public void dealCards(GameState gameState) {
        List<Card> deck = generateDeck();
        Collections.shuffle(deck); // Shuffle the deck

        List<Player> players = gameState.getPlayers();
        int playerCount = players.size();

        // Distribute cards equally
        for (int i = 0; i < deck.size(); i++) {
            Player targetPlayer = players.get(i % playerCount);
            targetPlayer.getHandCards().add(deck.get(i));
        }

        // Find the player with the Three of Diamonds to set as the opening player
        for (Player player : players) {
            for (Card card : player.getHandCards()) {
                if (card.getSuit() == Suit.DIAMOND && card.getRank() == Rank.THREE) {
                    gameState.setCurrentPlayerId(player.getId());
                    gameState.setOpeningTurn(true);
                    break;
                }
            }
        }
    }

    /**
     * Generate a standard 52-card deck.
     */
    private List<Card> generateDeck() {
        List<Card> deck = new ArrayList<>();
        int idCounter = 1;
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                String cardId = "C_" + idCounter++;
                deck.add(new Card(cardId, suit, rank));
            }
        }
        return deck;
    }
}
