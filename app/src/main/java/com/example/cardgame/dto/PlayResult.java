package com.example.cardgame.dto;

import com.example.cardgame.model.GameState;

public class PlayResult {

    private boolean success;
    private String message;
    private GameState gameState;

    public PlayResult(boolean success, String message, GameState gameState) {
        this.success = success;
        this.message = message;
        this.gameState = gameState;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public GameState getGameState() {
        return gameState;
    }
}