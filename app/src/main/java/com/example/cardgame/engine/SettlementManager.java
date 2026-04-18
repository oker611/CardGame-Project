package com.example.cardgame.engine;

import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;

/**
 * Manager responsible for determining game over conditions and settlement.
 */
public class SettlementManager {

    /**
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
                gameState.setWinnerId(player.getPlayerId());

                System.out.println("[CardGame][WIN] Winner: "
                        + player.getPlayerId()
                        + " (" + player.getPlayerName() + ")");
                break;
            }
        }
    }

    /**
     * Checks all players' hand cards and updates GameState if someone has won.
     */
    public void checkAndSettle(GameState gameState) {
        if (gameState.isGameOver()) {
            return;
        }

        for (Player player : gameState.getPlayers()) {
            if (player.getHandCards().isEmpty()) {
                gameState.setGameOver(true);
                gameState.setWinnerId(player.getPlayerId());

                System.out.println("[CardGame][WIN] Winner: "
                        + player.getPlayerId()
                        + " (" + player.getPlayerName() + ")");
                return;
            }
        }
    }
}