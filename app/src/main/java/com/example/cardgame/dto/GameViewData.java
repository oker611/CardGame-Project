package com.example.cardgame.dto;

import java.util.List;

public class GameViewData {

    private String currentPlayerId;
    private String currentPlayerName;
    private List<PlayerViewData> players;
    private List<String> selectedCardIds;
    private String lastPlayText;
    private boolean gameOver;
    private String winnerName;

    public GameViewData(String currentPlayerId, String currentPlayerName,
                        List<PlayerViewData> players,
                        List<String> selectedCardIds,
                        String lastPlayText,
                        boolean gameOver,
                        String winnerName) {
        this.currentPlayerId = currentPlayerId;
        this.currentPlayerName = currentPlayerName;
        this.players = players;
        this.selectedCardIds = selectedCardIds;
        this.lastPlayText = lastPlayText;
        this.gameOver = gameOver;
        this.winnerName = winnerName;
    }

    public String getCurrentPlayerId() {
        return currentPlayerId;
    }

    public String getCurrentPlayerName() {
        return currentPlayerName;
    }

    public List<PlayerViewData> getPlayers() {
        return players;
    }

    public List<String> getSelectedCardIds() {
        return selectedCardIds;
    }

    public String getLastPlayText() {
        return lastPlayText;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public String getWinnerName() {
        return winnerName;
    }
}