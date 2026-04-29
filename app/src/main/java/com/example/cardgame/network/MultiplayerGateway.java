package com.example.cardgame.network;

import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;

public interface MultiplayerGateway {

    void sendPlayAction(Play play);

    void sendPassAction(String playerId);

    void syncGameState(GameState gameState);
}