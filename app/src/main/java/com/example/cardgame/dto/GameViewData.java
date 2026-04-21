package com.example.cardgame.dto;

import java.util.List;

public class GameViewData {

    private String currentPlayerId;
    private String currentPlayerName;
    private List<PlayerViewData> players;
    private List<String> selectedCardIds;
    private List<String> myHandCards;  // ✅ 新增：当前玩家手牌
    private String lastPlayText;
    private List<String> lastPlayCards;
    private boolean gameOver;
    private String winnerName;

    // ✅ 修改构造函数，添加 myHandCards 参数
    public GameViewData(String currentPlayerId, String currentPlayerName,
                        List<PlayerViewData> players,
                        List<String> selectedCardIds,
                        List<String> myHandCards,  // 新增参数
                        String lastPlayText,
                        boolean gameOver,
                        String winnerName) {
        this.currentPlayerId = currentPlayerId;
        this.currentPlayerName = currentPlayerName;
        this.players = players;
        this.selectedCardIds = selectedCardIds;
        this.myHandCards = myHandCards;  // 新增赋值
        this.lastPlayText = lastPlayText;
        this.gameOver = gameOver;
        this.winnerName = winnerName;
        this.lastPlayCards = lastPlayCards;
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

    // ✅ 新增方法
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
}