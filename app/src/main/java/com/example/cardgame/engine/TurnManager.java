package com.example.cardgame.engine;

import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;

import java.util.List;

/**
 * Manager responsible for turn rotation.
 */
public class TurnManager {

    /**
     * Switches the turn to the next player in the list
     */
    public void switchPlayer(GameState gameState) {
        List<Player> players = gameState.getPlayers();
        String currentId = gameState.getCurrentPlayerId();

        int currentIndex = -1;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getPlayerId().equals(currentId)) {
                currentIndex = i;
                break;
            }
        }

        // Circular rotation
        int nextIndex = (currentIndex + 1) % players.size();
        gameState.setCurrentPlayerId(players.get(nextIndex).getPlayerId());

        // After the first switch, it's no longer the opening move
        gameState.setOpeningTurn(false);
    }
}