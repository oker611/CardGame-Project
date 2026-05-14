package com.example.cardgame.network.payload;

public class PlayerLeftPayload {

    private String playerId;
    private String playerName;

    public PlayerLeftPayload() {
    }

    public PlayerLeftPayload(String playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
}
