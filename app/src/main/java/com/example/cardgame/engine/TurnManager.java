// 【已修改，引入观察者模式基础框架】
package com.example.cardgame.engine;

import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.event.EventBus;
import com.example.cardgame.event.TurnChangedEvent;

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

        int nextIndex = (currentIndex + 1) % players.size();
        Player nextPlayer = players.get(nextIndex);
        gameState.setCurrentPlayerId(nextPlayer.getPlayerId());

        // ===== [事件驱动重构] 发布回合切换事件 =====
        EventBus.getInstance().post(new TurnChangedEvent(nextPlayer.getPlayerId(), "PLAY"));
        // ===== 结束 =====

        gameState.setOpeningTurn(false);

        System.out.println("[CardGame][TURN] Next player: "
                + nextPlayer.getPlayerId()
                + " (" + nextPlayer.getPlayerName() + ")");
        try {
            EventBus.getInstance().post(new TurnChangedEvent(nextPlayer.getPlayerId(), "PLAY"));
        } catch (Exception e) {
            System.err.println("[TurnManager] Failed to post TurnChangedEvent: " + e.getMessage());
            e.printStackTrace();
        }
    }
}