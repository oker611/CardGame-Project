package com.example.cardgame.network.payload;

public class PassActionPayload {

    private String playerId;

    public PassActionPayload(String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerId() {
        return playerId;
    }
}