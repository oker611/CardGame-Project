package com.example.cardgame.controller;

import android.os.Handler;
import android.os.Looper;

import com.example.cardgame.dto.GameViewData;
import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.dto.PlayerViewData;
import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.rule.RuleConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GameController implements GameActionHandler {

    private final GameEngine gameEngine;
    private final List<String> selectedCardIds = new ArrayList<>();

    // 固定“我”的ID（当前阶段先写死，后续可改为登录/房间分配）
    private static final String MY_PLAYER_ID = "P1";

    public GameController(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
    }

    @Override
    public void startNewGame() {
        System.out.println("[CardGame][CONTROLLER] startNewGame called");

        List<Player> players = new ArrayList<>();
        players.add(new Player("P1", "Alice"));
        players.add(new Player("P2", "Bob"));
        players.add(new Player("P3", "Cindy"));
        players.add(new Player("P4", "David"));

        RuleConfig ruleConfig = new RuleConfig();

        gameEngine.initializeGame(players, ruleConfig);
        gameEngine.dealCards();

        // 发牌后，如果当前玩家是 AI，则自动出牌
        triggerAITurn();

        GameState state = gameEngine.getGameState();
        if (state != null && state.getCurrentPlayer() != null) {
            System.out.println("[CardGame][CONTROLLER] startNewGame finished, currentPlayer="
                    + state.getCurrentPlayer().getPlayerId());
        }
    }

    @Override
    public PlayResult submitPlay(List<String> selectedCardIds) {
        System.out.println("[CardGame][CONTROLLER] submitPlay called, selectedCardIds=" + selectedCardIds);

        GameState state = gameEngine.getGameState();
        if (state == null || state.getCurrentPlayer() == null) {
            System.out.println("[CardGame][CONTROLLER] submitPlay failed: game state not ready");
            return new PlayResult(false, "Game state not ready.", state);
        }

        String currentPlayerId = state.getCurrentPlayer().getPlayerId();
        PlayResult result = gameEngine.playCards(currentPlayerId, selectedCardIds);

        System.out.println("[CardGame][CONTROLLER] submitPlay result="
                + (result != null ? result.getMessage() : "null"));

        // 出牌成功后清空选中的牌列表
        if (result != null && result.isSuccess()) {
            this.selectedCardIds.clear();

            // 出牌成功且游戏未结束时，延迟触发 AI 行动（让 UI 刷新）
            if (!gameEngine.isGameOver()) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> triggerAITurn(), 100);
            }
        }

        return result;
    }

    @Override
    public PassResult passTurn() {
        System.out.println("[CardGame][CONTROLLER] passTurn called");

        GameState state = gameEngine.getGameState();
        if (state == null || state.getCurrentPlayer() == null) {
            System.out.println("[CardGame][CONTROLLER] passTurn failed: game state not ready");
            return new PassResult(false, "Game state not ready.", state);
        }

        String currentPlayerId = state.getCurrentPlayer().getPlayerId();
        PassResult result = gameEngine.passTurn(currentPlayerId);

        System.out.println("[CardGame][CONTROLLER] passTurn result="
                + (result != null ? result.getMessage() : "null"));

        // Pass 成功后，如果游戏未结束，延迟触发 AI 行动
        if (result != null && result.isSuccess() && !gameEngine.isGameOver()) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> triggerAITurn(), 100);
        }

        return result;
    }

    @Override
    public void toggleCardSelection(String cardId) {
        System.out.println("[CardGame][CONTROLLER] toggleCardSelection called, cardId=" + cardId);

        if (selectedCardIds.contains(cardId)) {
            selectedCardIds.remove(cardId);
        } else {
            selectedCardIds.add(cardId);
        }

        System.out.println("[CardGame][CONTROLLER] current selectedCardIds=" + selectedCardIds);
    }

    @Override
    public GameViewData getGameViewData() {
        System.out.println("[CardGame][CONTROLLER] getGameViewData called");

        GameState state = gameEngine.getGameState();
        if (state == null) {
            System.out.println("[CardGame][CONTROLLER] getGameViewData failed: state is null");
            return emptyViewData();
        }

        // 当前行动玩家（用于高亮）
        Player currentPlayer = state.getCurrentPlayer();
        if (currentPlayer == null) {
            System.out.println("[CardGame][CONTROLLER] getGameViewData failed: currentPlayer is null");
            return emptyViewData();
        }

        // 固定“我”的视角
        Player me = state.getPlayerById(MY_PLAYER_ID);
        if (me == null) {
            System.out.println("[CardGame][CONTROLLER] getGameViewData failed: me is null");
            return emptyViewData();
        }

        // 玩家列表（用于UI显示：名字、剩余牌数、是否当前回合）
        List<PlayerViewData> players = new ArrayList<>();
        for (Player p : state.getPlayers()) {
            players.add(new PlayerViewData(
                    p.getPlayerId(),
                    p.getPlayerName(),
                    p.getHandCards().size(),
                    p.equals(currentPlayer),
                    p.isPassed()
            ));
        }

        // 胜利者
        Player winner = state.getWinnerId() != null
                ? state.getPlayerById(state.getWinnerId())
                : null;

        // 手牌必须来自“我”
        List<Card> handCardsList = new ArrayList<>(me.getHandCards());

        // 排序（点数大→小，同点数花色大→小）
        handCardsList.sort((c1, c2) -> {
            int rankCompare = Integer.compare(c2.getRank().getWeight(), c1.getRank().getWeight());
            if (rankCompare != 0) return rankCompare;
            return Integer.compare(c2.getSuit().getWeight(), c1.getSuit().getWeight());
        });

        List<String> myHandCards = handCardsList.stream()
                .map(card -> card.getSuit().getSymbol() + card.getRank().getDisplayName())
                .collect(Collectors.toList());

        return new GameViewData(
                me.getPlayerId(),
                me.getPlayerName(),
                players,
                new ArrayList<>(selectedCardIds),
                myHandCards,
                state.getLastPlay() == null ? "" : state.getLastPlay().toString(),
                gameEngine.isGameOver(),
                gameEngine.isGameOver() && winner != null ? winner.getPlayerName() : ""
        );
    }

    private GameViewData emptyViewData() {
        return new GameViewData(
                "",
                "",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                "",
                false,
                ""
        );
    }

    // ==================== AI 自动出牌逻辑 ====================

    /**
     * 自动为当前玩家出两张随机牌（仅用于 AI）
     */
    private void autoPlayForCurrentPlayer() {
        GameState state = gameEngine.getGameState();
        if (state == null || gameEngine.isGameOver()) {
            return;
        }
        Player currentPlayer = state.getCurrentPlayer();
        if (currentPlayer == null) {
            return;
        }
        // 如果是真人玩家，不自动出牌
        if (MY_PLAYER_ID.equals(currentPlayer.getPlayerId())) {
            return;
        }
        // 获取随机两张牌（复用 Player 中已有的 getRandomCards 方法）
        List<Card> randomCards = currentPlayer.getRandomCards(2);
        if (randomCards.isEmpty()) {
            // 无牌可出，自动 Pass（实际上游戏应该结束，但这里做保护）
            passTurn();
            return;
        }
        List<String> cardIds = new ArrayList<>();
        for (Card card : randomCards) {
            cardIds.add(card.getCardId());
        }
        System.out.println("[CardGame][AI] 自动出牌: " + cardIds);
        // 调用出牌
        submitPlay(cardIds);
    }

    /**
     * 递归触发 AI 行动（当游戏未结束且当前玩家为 AI 时）
     */
    public void triggerAITurn() {
        if (gameEngine.isGameOver()) {
            return;
        }
        GameState state = gameEngine.getGameState();
        if (state == null) return;
        Player current = state.getCurrentPlayer();
        if (current == null) return;
        if (!MY_PLAYER_ID.equals(current.getPlayerId())) {
            autoPlayForCurrentPlayer();
        }
    }
}