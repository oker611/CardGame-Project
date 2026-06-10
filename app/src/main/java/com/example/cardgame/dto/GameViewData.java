package com.example.cardgame.dto;

import java.util.List;
import java.util.Map;
import com.example.cardgame.model.Rank;

public class GameViewData {

    private String currentPlayerId;
    private String currentPlayerName;
    private List<PlayerViewData> players;
    private List<String> selectedCardIds;
    private List<String> myHandCards;
    private String lastPlayText;
    private List<String> lastPlayCards;
    private boolean gameOver;
    private String winnerName;

    private Map<String, List<String>> playerLastPlayCards;
    private Map<Rank, Integer> remainingCountByRank;

    public GameViewData(String currentPlayerId, String currentPlayerName,
                        List<PlayerViewData> players,
                        List<String> selectedCardIds,
                        List<String> myHandCards,
                        String lastPlayText,
                        boolean gameOver,
                        String winnerName,
                        Map<String, List<String>> playerLastPlayCards) {
        this.currentPlayerId = currentPlayerId;
        this.currentPlayerName = currentPlayerName;
        this.players = players;
        this.selectedCardIds = selectedCardIds;
        this.myHandCards = myHandCards;
        this.lastPlayText = lastPlayText;
        this.gameOver = gameOver;
        this.winnerName = winnerName;
        this.playerLastPlayCards = playerLastPlayCards;
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

    public List<String> getMyHandCards() {
        return myHandCards;
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

    public List<String> getLastPlayCards() {
        return lastPlayCards;
    }

    public Map<String, List<String>> getPlayerLastPlayCards() {
        return playerLastPlayCards;
    }

    public Map<Rank, Integer> getRemainingCountByRank() {
        return remainingCountByRank;
    }

    public void setRemainingCountByRank(Map<Rank, Integer> remainingCountByRank) {
        this.remainingCountByRank = remainingCountByRank;
    }
}
