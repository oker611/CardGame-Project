package com.example.cardgame.engine;

import android.util.Log;
import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.CardPattern;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.model.Player;
import com.example.cardgame.rule.RuleConfig;
import com.example.cardgame.rule.RuleEngine;
import com.example.cardgame.util.Logger;
import java.util.List;

/**
 * Core game engine class that coordinates game flow and managers.
 */
public class GameEngine {

    // ==================== 调试开关（已关闭，自动出牌由 Controller 统一调度） ====================
    private static final boolean DEBUG_AUTO_PLAY = false;   // 关闭 Engine 自动出牌
    // =================================================

    private GameState gameState;
    private RuleConfig ruleConfig;
    private RuleEngine ruleEngine;

    private final DealManager dealManager;
    private final TurnManager turnManager;
    private final SettlementManager settlementManager;

    public GameEngine() {
        this.dealManager = new DealManager();
        this.turnManager = new TurnManager();
        this.settlementManager = new SettlementManager();
        this.ruleEngine = null;
    }

    public void initializeGame(List<Player> players, RuleConfig ruleConfig) {
        this.ruleConfig = ruleConfig;
        this.gameState = new GameState();
        this.gameState.setPlayers(players);
        this.gameState.setGameOver(false);
        this.gameState.setOpeningTurn(true);
    }

    public void dealCards() {
        if (gameState != null) {
            dealManager.dealCards(gameState);
            // 不再主动调用自动出牌，由 Controller 控制
        }
    }

    public PlayResult playCards(String playerId, List<String> selectedCardIds) {
        System.out.println("[CardGame][PLAY] request playerId=" + playerId + ", selectedCardIds=" + selectedCardIds);

        Player player = gameState.getPlayerById(playerId);
        if (player == null || !playerId.equals(gameState.getCurrentPlayerId())) {
            System.out.println("[CardGame][PLAY] rejected: not current player, currentPlayerId="
                    + (gameState != null ? gameState.getCurrentPlayerId() : "null"));
            return createPlayResult(false, "Not your turn", gameState);
        }
        if (selectedCardIds == null || selectedCardIds.isEmpty()) {
            System.out.println("[CardGame][PLAY] rejected: selectedCardIds is empty");
            return createPlayResult(false, "Please select cards first", gameState);
        }
        List<Card> selectedCards = player.findCardsByIds(selectedCardIds);
        Play currentPlay = new Play(playerId, selectedCards, CardPattern.INVALID);

        // 临时逻辑：假设出牌永远合法，强制放行以测试 Engine 主流程
        currentPlay.setPattern(CardPattern.SINGLE);

        // 根据 cardId 正确移除手牌（使用你的 removeIf 方式，保留它）
        player.getHandCards().removeIf(card -> selectedCardIds.contains(card.getCardId()));

        gameState.setLastPlay(currentPlay);
        player.setPassed(false);

        System.out.println("[CardGame][PLAY] success playerId=" + playerId
                + ", cards=" + selectedCardIds
                + ", pattern=" + currentPlay.getPattern());

        settlementManager.checkAndSettle(gameState);
        if (!gameState.isGameOver()) {
            turnManager.switchPlayer(gameState);
            // 不再主动调用自动出牌，由 Controller 控制
        }
        return createPlayResult(true, "PLAY_OK", gameState);
    }

    public PassResult passTurn(String playerId) {
        System.out.println("[CardGame][PASS] request playerId=" + playerId);

        if (gameState == null || gameState.isGameOver()) {
            System.out.println("[CardGame][PASS] rejected: game is over");
            return createPassResult(false, "Game is over", gameState);
        }

        Player player = gameState.getPlayerById(playerId);
        if (player == null || !playerId.equals(gameState.getCurrentPlayerId())) {
            System.out.println("[CardGame][PASS] rejected: not current player, currentPlayerId="
                    + gameState.getCurrentPlayerId());
            return createPassResult(false, "Not your turn", gameState);
        }
        if (gameState.isOpeningTurn()) {
            System.out.println("[CardGame][PASS] rejected: opening turn cannot pass");
            return createPassResult(false, "Cannot pass on opening turn", gameState);
        }
        player.setPassed(true);
        turnManager.switchPlayer(gameState);

        if (gameState.areAllOtherPlayersPassed(gameState.getCurrentPlayerId())) {
            gameState.setLastPlay(null);
            gameState.clearAllPassStatus();
            System.out.println("[CardGame][PASS] all other players passed, reset round state");
        }

        // 合并后的 return：保留你的注释，同时使用 main 的日志和返回消息格式
        System.out.println("[CardGame][PASS] success playerId=" + playerId);
        // 不再主动调用自动出牌，由 Controller 控制
        return createPassResult(true, "PASS_OK", gameState);
    }

    // ==================== 以下方法不再被调用，但保留以防万一 ====================
    // private void autoPlayForCurrentPlayer() { ... }   // 已注释，不再使用
    // ====================================================

    private PlayResult createPlayResult(boolean success, String msg, GameState state) {
        return new PlayResult(success, msg, state);
    }

    private PassResult createPassResult(boolean success, String msg, GameState state) {
        return new PassResult(success, msg, state);
    }

    public boolean isGameOver() {
        return gameState != null && gameState.isGameOver();
    }

    public String getWinnerId() {
        return (gameState != null) ? gameState.getWinnerId() : null;
    }

    public GameState getGameState() {
        return gameState;
    }

    private boolean containsThreeOfDiamonds(List<Card> cards) {
        for (Card card : cards) {
            if (card != null && card.isThreeOfDiamonds()) {
                return true;
            }
        }
        return false;
    }

    private CardPattern guessPattern(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return CardPattern.INVALID;
        }
        if (cards.size() == 1) {
            return CardPattern.SINGLE;
        }
        if (cards.size() == 2 && sameRank(cards)) {
            return CardPattern.PAIR;
        }
        if (cards.size() == 3 && sameRank(cards)) {
            return CardPattern.TRIPLE;
        }
        return CardPattern.INVALID;
    }

    private boolean sameRank(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return false;
        }
        Card first = cards.get(0);
        for (Card card : cards) {
            if (card == null || first.getRank() != card.getRank()) {
                return false;
            }
        }
        return true;
    }

    private String formatCards(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < cards.size(); i++) {
            sb.append(cards.get(i).getDisplayText());
            if (i < cards.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}