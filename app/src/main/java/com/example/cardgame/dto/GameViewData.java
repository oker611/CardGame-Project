package com.example.cardgame.dto;

import java.util.List;
import java.util.Map;

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

    // ✅ 新增：每个玩家最后一次出的牌（key=playerId, value=牌字符串列表）
    private Map<String, List<String>> playerLastPlayCards;

    // 原构造函数保留，但为了兼容性，新增一个重载（或者直接修改原构造函数）
    // 由于其他地方可能调用旧构造函数，我们提供一个新构造函数并保留旧的（但旧的赋默认空map）
    // 为了安全，直接修改原构造函数，并增加参数（需要同步修改所有调用处）
    // 在 GameController 中调用的是新参数，所以我们直接改这个构造函数
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
        this.lastPlayCards = lastPlayCards;
    }

    // 为了兼容旧代码（如果有其他地方调用），保留一个无 playerLastPlayCards 的构造函数（不建议使用）
    // 但为了避免编译错误，我们再保留一个旧版（可标记 @Deprecated）
    @Deprecated
    public GameViewData(String currentPlayerId, String currentPlayerName,
                        List<PlayerViewData> players,
                        List<String> selectedCardIds,
                        List<String> myHandCards,
                        String lastPlayText,
                        boolean gameOver,
                        String winnerName) {
        this(currentPlayerId, currentPlayerName, players, selectedCardIds, myHandCards,
                lastPlayText, gameOver, winnerName, null);
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
}