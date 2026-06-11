package com.example.cardgame.engine;

import com.example.cardgame.dto.PassResult;
import com.example.cardgame.model.GameState;

public interface GameStateAccess {
    GameState getGameState();
    PassResult passTurn(String playerId);
}
