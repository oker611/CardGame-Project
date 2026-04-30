package com.example.cardgame.network.payload;

public class GameOverPayload {

    private String winnerId;
    private String winnerName;

    public GameOverPayload(String winnerId, String winnerName) {
        this.winnerId = winnerId;
        this.winnerName = winnerName;
    }

    public String getWinnerId() {
        return winnerId;
    }

    public String getWinnerName() {
        return winnerName;
    }
}