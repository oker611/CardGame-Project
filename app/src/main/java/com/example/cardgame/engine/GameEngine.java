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

    private static final boolean DEBUG_AUTO_PLAY = false;

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

        // 根据 cardId 正确移除手牌
        player.getHandCards().removeIf(card -> selectedCardIds.contains(card.getCardId()));

        gameState.setLastPlay(currentPlay);
        player.setPassed(false);

        // ✅ 新增：记录该玩家最后一次出的牌
        gameState.updateLastPlayByPlayer(playerId, selectedCards);

        System.out.println("[CardGame][PLAY] success playerId=" + playerId
                + ", cards=" + selectedCardIds
                + ", pattern=" + currentPlay.getPattern());

        settlementManager.checkAndSettle(gameState);
        if (!gameState.isGameOver()) {
            turnManager.switchPlayer(gameState);
        } else {
            Logger.win("游戏结束，获胜者: " + gameState.getWinnerId());
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
            // ✅ 新增：连续Pass重置桌面时，清空所有玩家的出牌记录
            gameState.clearAllLastPlayRecords();
            System.out.println("[CardGame][PASS] all other players passed, reset round state and clear last play records");
        }

        System.out.println("[CardGame][PASS] success playerId=" + playerId);
        return createPassResult(true, "PASS_OK", gameState);
    }

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

    // ========== 为 AI 提供的状态查询接口 ==========
    public List<Card> getLastPlayCards() {
        if (gameState == null || gameState.getLastPlay() == null) {
            return null;
        }
        return gameState.getLastPlay().getCards();
    }

    public boolean isFirstRound() {
        return gameState != null && gameState.isOpeningTurn();
    }

    public boolean isFirstTurnOfCurrentRound() {
        if (gameState == null) return true;
        List<Card> lastPlayCards = getLastPlayCards();
        return lastPlayCards == null || lastPlayCards.isEmpty();
    }

    public String getCurrentPlayerId() {
        return gameState != null ? gameState.getCurrentPlayerId() : null;
    }

    public List<Card> getPlayerHand(String playerId) {
        Player player = gameState != null ? gameState.getPlayerById(playerId) : null;
        return player != null ? player.getHandCards() : null;
    }
    // ===========================================

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

    /**
     * 接收远程（蓝牙）出牌指令的入口
     */
    public void executeRemotePlay(Play play) {
        // TODO: 周一晚或周二再实现，现在只留空
    }

    /**
     * 接收远程（蓝牙）过牌指令的入口
     */
    public void executeRemotePass(String playerId) {
        // TODO: 周一晚或周二再实现，现在只留空
    }
}
