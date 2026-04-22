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

    private static final String MY_PLAYER_ID = "P1";

    // UI 刷新回调
    private Runnable uiRefreshCallback;

    public GameController(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
    }

    public void setUiRefreshCallback(Runnable callback) {
        this.uiRefreshCallback = callback;
    }

    private void notifyUiRefresh() {
        if (uiRefreshCallback != null) {
            uiRefreshCallback.run();
        }
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

        triggerAITurn();

        GameState state = gameEngine.getGameState();
        if (state != null && state.getCurrentPlayer() != null) {
            System.out.println("[CardGame][CONTROLLER] startNewGame finished, currentPlayer="
                    + state.getCurrentPlayer().getPlayerId());
        }
        notifyUiRefresh();
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
        List<String> cardsToPlay;

        if (currentPlayerId.equals(MY_PLAYER_ID)) {
            // 将 UI 层的展示字符串映射回底层真实的 CardId
            cardsToPlay = new ArrayList<>();
            Player me = state.getPlayerById(MY_PLAYER_ID);
            if (me != null && this.selectedCardIds != null) {
                for (String uiCardStr : this.selectedCardIds) {
                    for (Card c : me.getHandCards()) {
                        String matchStr = c.getSuit().getSymbol() + c.getRank().getDisplayName();
                        if (matchStr.equals(uiCardStr)) {
                            cardsToPlay.add(c.getCardId());
                            break; 
                        }
                    }
                }
            }
        } else {
            Player aiPlayer = state.getCurrentPlayer();
            List<Card> randomCards = aiPlayer.getRandomCards(2);
            cardsToPlay = new ArrayList<>();
            for (Card card : randomCards) {
                cardsToPlay.add(card.getCardId());
            }
            System.out.println("[CardGame][AI] 自动出牌: " + cardsToPlay);
        }

        if (cardsToPlay == null || cardsToPlay.isEmpty()) {
            System.out.println("[CardGame][CONTROLLER] No cards to play, auto pass");
            passTurn();
            return new PlayResult(true, "Auto pass", gameEngine.getGameState());
        }

        PlayResult result = gameEngine.playCards(currentPlayerId, cardsToPlay);

        System.out.println("[CardGame][CONTROLLER] submitPlay result="
                + (result != null ? result.getMessage() : "null"));

        if (result != null && result.isSuccess()) {
            if (currentPlayerId.equals(MY_PLAYER_ID)) {
                this.selectedCardIds.clear();
            }
            notifyUiRefresh();

            if (!gameEngine.isGameOver()) {
                // 真人出牌后延迟很短（100ms）触发下一个回合检查；AI 出牌后延迟 0 毫秒（因为 AI 回合内的延迟已在 triggerAITurn 中处理）
                long delay = currentPlayerId.equals(MY_PLAYER_ID) ? 100 : 0;
                new Handler(Looper.getMainLooper()).postDelayed(() -> triggerAITurn(), delay);
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

        if (result != null && result.isSuccess()) {
            notifyUiRefresh();
            if (!gameEngine.isGameOver()) {
                // 类似出牌，真人 Pass 后延迟短，AI Pass 后延迟 0
                long delay = currentPlayerId.equals(MY_PLAYER_ID) ? 100 : 0;
                new Handler(Looper.getMainLooper()).postDelayed(() -> triggerAITurn(), delay);
            }
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
        notifyUiRefresh();
    }

    @Override
    public GameViewData getGameViewData() {
        System.out.println("[CardGame][CONTROLLER] getGameViewData called");

        GameState state = gameEngine.getGameState();
        if (state == null) {
            System.out.println("[CardGame][CONTROLLER] getGameViewData failed: state is null");
            return emptyViewData();
        }

        Player currentPlayer = state.getCurrentPlayer();
        if (currentPlayer == null) {
            System.out.println("[CardGame][CONTROLLER] getGameViewData failed: currentPlayer is null");
            return emptyViewData();
        }

        Player me = state.getPlayerById(MY_PLAYER_ID);
        if (me == null) {
            System.out.println("[CardGame][CONTROLLER] getGameViewData failed: me is null");
            return emptyViewData();
        }

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

        Player winner = state.getWinnerId() != null
                ? state.getPlayerById(state.getWinnerId())
                : null;

        List<Card> handCardsList = new ArrayList<>(me.getHandCards());
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

    private void autoPlayForCurrentPlayer() {
        GameState state = gameEngine.getGameState();
        if (state == null || gameEngine.isGameOver()) {
            return;
        }
        Player currentPlayer = state.getCurrentPlayer();
        if (currentPlayer == null) {
            return;
        }
        if (MY_PLAYER_ID.equals(currentPlayer.getPlayerId())) {
            return;
        }
        List<Card> randomCards = currentPlayer.getRandomCards(2);
        if (randomCards.isEmpty()) {
            passTurn();
            return;
        }
        List<String> cardIds = new ArrayList<>();
        for (Card card : randomCards) {
            cardIds.add(card.getCardId());
        }
        System.out.println("[CardGame][AI] 自动出牌: " + cardIds);
        submitPlay(cardIds);
    }

    /**
     * 触发 AI 行动。如果当前玩家是 AI，则延迟 5 秒后再出牌，让高亮停留更长时间。
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
            // AI 玩家：延迟 5 秒后再出牌，让 UI 高亮显示足够长时间
            long aiThinkTime = 5000; // 5 秒
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // 延迟后再次检查游戏状态和当前玩家，避免状态变化导致错误
                if (!gameEngine.isGameOver() && gameEngine.getGameState() != null
                        && current.equals(gameEngine.getGameState().getCurrentPlayer())) {
                    autoPlayForCurrentPlayer();
                } else {
                    System.out.println("[CardGame][AI] 延迟出牌时状态已变化，取消出牌");
                }
            }, aiThinkTime);
        }
        // 如果当前玩家是真人，什么都不做，等待用户手动操作
    }
}