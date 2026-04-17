package com.example.cardgame.engine;

import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;

/**
<<<<<<< HEAD
 * Manager responsible for victory conditions and settlement.
=======
 * Manager responsible for determining game over conditions and settlement.
>>>>>>> origin/dev-czh-ui-zhy
 */
public class SettlementManager {

    /**
<<<<<<< HEAD
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
=======
     * Check if any player's hand card count has reached zero.
     */
    public boolean checkGameOver(GameState gameState) {
        for (Player player : gameState.getPlayers()) {
            if (player.getHandCards().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Settle the game and set the winner ID.
     */
    public void settleGame(GameState gameState) {
        for (Player player : gameState.getPlayers()) {
            if (player.getHandCards().isEmpty()) {
                gameState.setGameOver(true);
                gameState.setWinnerId(player.getId());
                break;
>>>>>>> origin/dev-czh-ui-zhy
            }
        }
    }
}
