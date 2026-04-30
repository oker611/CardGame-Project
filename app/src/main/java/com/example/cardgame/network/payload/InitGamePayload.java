package com.example.cardgame.network.payload;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;

import java.util.List;

public class InitGamePayload {

    private String currentPlayerId;
    private String localPlayerId;
    private String remotePlayerId;

    private List<Card> localHandCards;
    private List<Card> remoteHandCards;

    private GameState gameState;

    public InitGamePayload(
            String currentPlayerId,
            String localPlayerId,
            String remotePlayerId,
            List<Card> localHandCards,
            List<Card> remoteHandCards,
            GameState gameState
    ) {
        this.currentPlayerId = currentPlayerId;
        this.localPlayerId = localPlayerId;
        this.remotePlayerId = remotePlayerId;
        this.localHandCards = localHandCards;
        this.remoteHandCards = remoteHandCards;
        this.gameState = gameState;
    }

    public String getCurrentPlayerId() {
        return currentPlayerId;
    }

    public String getLocalPlayerId() {
        return localPlayerId;
    }

    public String getRemotePlayerId() {
        return remotePlayerId;
    }

    public List<Card> getLocalHandCards() {
        return localHandCards;
    }

    public List<Card> getRemoteHandCards() {
        return remoteHandCards;
    }

    public GameState getGameState() {
        return gameState;
    }
}