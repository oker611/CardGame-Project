package com.example.cardgame.dto;

import com.example.cardgame.model.GameState;

public class PassResult {

    private boolean success;
    private String message;
    private GameState gameState;

    public PassResult(boolean success, String message, GameState gameState) {
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

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setMessage(String message) {
        this.message = message;
    }
    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }
}
