package com.example.cardgame.network.payload;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;

import java.util.List;
import java.util.Map;

public class InitGamePayload {

    // ——— 旧字段（2-player兼容，保留以免破坏序列化） ———
    private String currentPlayerId;
    private String localPlayerId;
    private String remotePlayerId;

    private List<Card> localHandCards;
    private List<Card> remoteHandCards;

    private GameState gameState;

    // ——— 新字段（4-player支持） ———
    /** playerId → 该玩家手牌列表 */
    private Map<String, List<Card>> playerHandCards;
    /** 出牌顺序（按玩家ID排列） */
    private List<String> playerOrder;

    public InitGamePayload() {
    }

    // ===== 旧构造函数（2-player兼容） =====
    public InitGamePayload(
            String currentPlayerId,
            String localPlayerId,
            String remotePlayerId,
            List<Card> localHandCards,
            List<Card> remoteHandCards,
            GameState gameState
    ) {
        this.currentPlayerId = currentPlayerId;
        this.localPlayerId = localPlayerId;
        this.remotePlayerId = remotePlayerId;
        this.localHandCards = localHandCards;
        this.remoteHandCards = remoteHandCards;
        this.gameState = gameState;
    }

    // ===== 新构造函数（N-player通用） =====
    public InitGamePayload(
            Map<String, List<Card>> playerHandCards,
            List<String> playerOrder,
            String currentPlayerId,
            GameState gameState
    ) {
        this.playerHandCards = playerHandCards;
        this.playerOrder = playerOrder;
        this.currentPlayerId = currentPlayerId;
        this.gameState = gameState;
    }

    // ===== 旧 getter / setter（保留兼容） =====

    public String getCurrentPlayerId() {
        return currentPlayerId;
    }

    public void setCurrentPlayerId(String currentPlayerId) {
        this.currentPlayerId = currentPlayerId;
    }

    public String getLocalPlayerId() {
        return localPlayerId;
    }

    public void setLocalPlayerId(String localPlayerId) {
        this.localPlayerId = localPlayerId;
    }

    public String getRemotePlayerId() {
        return remotePlayerId;
    }

    public void setRemotePlayerId(String remotePlayerId) {
        this.remotePlayerId = remotePlayerId;
    }

    public List<Card> getLocalHandCards() {
        return localHandCards;
    }

    public void setLocalHandCards(List<Card> localHandCards) {
        this.localHandCards = localHandCards;
    }

    public List<Card> getRemoteHandCards() {
        return remoteHandCards;
    }

    public void setRemoteHandCards(List<Card> remoteHandCards) {
        this.remoteHandCards = remoteHandCards;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    // ===== 新 getter / setter（N-player） =====

    public Map<String, List<Card>> getPlayerHandCards() {
        return playerHandCards;
    }

    public void setPlayerHandCards(Map<String, List<Card>> playerHandCards) {
        this.playerHandCards = playerHandCards;
    }

    public List<String> getPlayerOrder() {
        return playerOrder;
    }

    public void setPlayerOrder(List<String> playerOrder) {
        this.playerOrder = playerOrder;
    }
}
