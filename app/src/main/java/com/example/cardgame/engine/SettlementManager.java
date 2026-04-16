package com.example.cardgame.engine;

import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;

/**
 * Manager responsible for victory conditions and settlement.
 */
public class SettlementManager {

    /**
     * Checks all players' hand cards and updates GameState if someone has won.
     * @param gameState Current game state to be updated/
     */
    public void checkAndSettle(GameState gameState) {
        if (gameState.isGameOver()) {
            return;
        }

        for (Player player : gameState.getPlayers()) {
            // Check if hand cards are empty
            if (player.getHandCards().isEmpty()) {
                // Perform settlement: update state and winner
                gameState.setGameOver(true);
                gameState.setWinnerId(player.getPlayerId());
                return;
            }
        }
    }
}
