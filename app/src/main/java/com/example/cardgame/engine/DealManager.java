package com.example.cardgame.engine;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Manager responsible for the deck lifecycle: generating, shuffling, and dealing.
 */
public class DealManager {

    /**
     * Executes the complete dealing process and identifies the first player.
     */
    public void dealCards(GameState gameState) {
        List<Card> deck = generateFullDeck();
        Collections.shuffle(deck);

        List<Player> players = gameState.getPlayers();
        int playerCount = players.size();

        for (int i = 0; i < deck.size(); i++) {
            Player targetPlayer = players.get(i % playerCount);
            targetPlayer.getHandCards().add(deck.get(i));
        }

        validateDealing(players);
        identifyOpeningPlayer(gameState);
        printDealLogs(gameState);
    }

    /**
     * Generates a standard 52-card deck.
     */
    private List<Card> generateFullDeck() {
        List<Card> deck = new ArrayList<>();
        int idCounter = 1;
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
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
        Player openingPlayer = gameState.findOpeningPlayer();
        if (openingPlayer != null) {
            gameState.setCurrentPlayerId(openingPlayer.getPlayerId());
            gameState.setOpeningTurn(true);
        }
    }

    private void printDealLogs(GameState gameState) {
        System.out.println("[CardGame][DEAL] Dealing completed.");

        for (Player player : gameState.getPlayers()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < player.getHandCards().size(); i++) {
                Card card = player.getHandCards().get(i);
                sb.append(card.getDisplayText());
                if (i < player.getHandCards().size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append("]");

            System.out.println("[CardGame][DEAL] Player "
                    + player.getPlayerId()
                    + " (" + player.getPlayerName() + ") -> "
                    + sb);
        }

        Player openingPlayer = gameState.getCurrentPlayer();
        if (openingPlayer != null) {
            System.out.println("[CardGame][TURN] Opening player: "
                    + openingPlayer.getPlayerId()
                    + " (" + openingPlayer.getPlayerName() + ")");
        }
    }
}