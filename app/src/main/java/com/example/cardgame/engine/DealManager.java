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
 * Manager responsible for the deck lifecycle: generating, shuffling, and dealing.
 */
public class DealManager {

    /**
     * Executes the complete dealing process and identifies the first player.
     */
    public void dealCards(GameState gameState) {
        List<Card> deck = generateFullDeck();
        Collections.shuffle(deck); // Shuffle

        List<Player> players = gameState.getPlayers();
        int playerCount = players.size();

        // Distribute cards equally
        for (int i = 0; i < deck.size(); i++) {
            Player targetPlayer = players.get(i % playerCount);
            targetPlayer.getHandCards().add(deck.get(i));
        }

        // Acceptance check: Ensure each player has exactly 13 cards
        validateDealing(players);

        // Mark first player: Find the holder of Diamond 3
        identifyOpeningPlayer(gameState);
    }

    /**
     * Generates a standard 52-card deck.
     */
    private List<Card> generateFullDeck() {
        List<Card> deck = new ArrayList<>();
        int idCounter = 1;
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                // Strictly use cardId from model.Card
                deck.add(new Card("C_" + idCounter++, suit, rank));
            }
        }
        return deck;
    }

    /**
     * Verifies if each player received exactly 13 cards.
     */
    private void validateDealing(List<Player> players) {
        for (Player p : players) {
            if (p.getHandCards().size() != 13) {
                throw new IllegalStateException("Dealing Validation Failed: Player "
                        + p.getPlayerName() + " (ID: " + p.getPlayerId() + ") has "
                        + p.getHandCards().size() + " cards.");
            }
        }
    }

    /**
     * Sets the initial current player to the one holding Suit.DIAMONDS and Rank.THREE.
     */
    private void identifyOpeningPlayer(GameState gameState) {
        for (Player player : gameState.getPlayers()) {
            for (Card card : player.getHandCards()) {
                if (card.getSuit() == Suit.DIAMONDS && card.getRank() == Rank.THREE) {
                    gameState.setCurrentPlayerId(player.getPlayerId());
                    gameState.setOpeningTurn(true); // Mark as the first turn
                    return;
                }
            }
        }
    }
}