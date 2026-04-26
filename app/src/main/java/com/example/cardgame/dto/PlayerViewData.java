package com.example.cardgame.dto;

public class PlayerViewData {

    private String playerId;
    private String playerName;
    private int remainingCardCount;
    private boolean currentTurn;
    private boolean passed;
    private boolean isHuman;

    public PlayerViewData(String playerId, String playerName,
                          int remainingCardCount, boolean currentTurn, boolean passed,boolean isHuman) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.remainingCardCount = remainingCardCount;
        this.currentTurn = currentTurn;
        this.passed = passed;
        this.isHuman = isHuman;
    }

    public boolean isHuman() {
        return isHuman;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getRemainingCardCount() {
        return remainingCardCount;
    }

    public boolean isCurrentTurn() {
        return currentTurn;
    }

    public boolean isPassed() {
        return passed;
    }
}