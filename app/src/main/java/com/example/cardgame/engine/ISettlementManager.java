package com.example.cardgame.engine;

import com.example.cardgame.model.GameState;

public interface ISettlementManager {
    boolean checkGameOver(GameState gameState);
    void settleGame(GameState gameState);
    void checkAndSettle(GameState gameState);
}
