package com.example.cardgame.engine;

import com.example.cardgame.model.GameState;

public interface ITurnManager {
    void switchPlayer(GameState gameState);
}
