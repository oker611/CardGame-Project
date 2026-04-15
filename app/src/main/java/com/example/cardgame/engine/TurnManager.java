package com.example.cardgame.engine;

import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;

import java.util.List;

/**
 * Manager responsible for handling turn switching and pass logic.
 */
public class TurnManager {

    /**
     * Switch to the next valid player's turn in a circular manner.
     */
    public void switchPlayer(GameState gameState) {
        List<Player> players = gameState.getPlayers();
        String currentId = gameState.getCurrentPlayerId();
        int currentIndex = -1;

        // Find the index of the current player
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getId().equals(currentId)) {
                currentIndex = i;
                break;
            }
        }

        // Move to the next player
        int nextIndex = (currentIndex + 1) % players.size();
        gameState.setCurrentPlayerId(players.get(nextIndex).getId());
    }

    /**
     * Handle the logic when all other players pass, making the current player the start of a new round.
     */
    public void resetTurnCycle(GameState gameState) {
        // Clear the previous play so the new player can play any valid pattern
        gameState.setLastPlay(null);
        // Note: isOpeningTurn is typically strictly for the first move of the game with Diamond 3.
        // For a new cycle, lastPlay == null is enough to indicate a free play.
    }
}
