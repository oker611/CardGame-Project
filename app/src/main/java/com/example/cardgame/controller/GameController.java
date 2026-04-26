package com.example.cardgame.controller;

import android.os.Handler;
import android.os.Looper;

import com.example.cardgame.ai.AIPlayer;
import com.example.cardgame.dto.GameViewData;
import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.dto.PlayerViewData;
import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.model.PlayerType;
import com.example.cardgame.rule.RuleConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GameController implements GameActionHandler {

    private final GameEngine gameEngine;
    private final List<String> selectedCardIds = new ArrayList<>();

    private static final String MY_PLAYER_ID = "P1";

    // 缓存 AI 玩家实例
    private final Map<String, AIPlayer> aiPlayerCache = new ConcurrentHashMap<>();

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
        // 创建玩家并设置是否为 AI
        Player p1 = new Player("P1", "Alice");
        p1.setType(PlayerType.HUMAN); // 真人玩家
        players.add(p1);

        Player p2 = new Player("P2", "Bob");
        p2.setType(PlayerType.AI);    // AI 机器人
        players.add(p2);

        Player p3 = new Player("P3", "Cindy");
        p3.setType(PlayerType.AI);    // AI 机器人
        players.add(p3);

        Player p4 = new Player("P4", "David");
        p4.setType(PlayerType.AI);    // AI 机器人
        players.add(p4);

        RuleConfig ruleConfig = new RuleConfig();

        gameEngine.initializeGame(players, ruleConfig);
        gameEngine.dealCards();

        // 清空 AI 缓存（新游戏重新创建）
        aiPlayerCache.clear();

        triggerNextAction();

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
            // 真人：将 UI 层的展示字符串映射回底层真实的 CardId
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
            // AI：调用者直接传入了 cardId 列表，无需映射
            cardsToPlay = new ArrayList<>(selectedCardIds);
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
                new Handler(Looper.getMainLooper()).postDelayed(() -> triggerNextAction(), delay);
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
                long delay = currentPlayerId.equals(MY_PLAYER_ID) ? 100 : 0;
                new Handler(Looper.getMainLooper()).postDelayed(() -> triggerNextAction(), delay);
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
                    p.isPassed(),
                    p.getType() == com.example.cardgame.model.PlayerType.HUMAN
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

    // ==================== AI 自动出牌逻辑（使用 AIPlayer 合法决策） ====================

    /**
     * 获取或创建 AI 玩家实例（缓存）
     */
    private AIPlayer getOrCreateAIPlayer(Player player) {
        if (player.getType() != PlayerType.AI) return null;
        return aiPlayerCache.computeIfAbsent(player.getPlayerId(), id -> {
            AIPlayer ai = new AIPlayer(id);
            ai.setHand(player.getHandCards());
            return ai;
        });
    }

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
            return; // 真人玩家不自动出牌
        }

        // 确保 AI 玩家的手牌是最新的
        AIPlayer aiPlayer = getOrCreateAIPlayer(currentPlayer);
        if (aiPlayer == null) {
            // 如果玩家不是 AI（理论上不会进入），降级使用随机
            List<Card> randomCards = currentPlayer.getRandomCards(2);
            if (randomCards.isEmpty()) {
                passTurn();
                return;
            }
            List<String> cardIds = randomCards.stream().map(Card::getCardId).collect(Collectors.toList());
            submitPlay(cardIds);
            return;
        }

        // 同步 AI 手牌（因为手牌可能变化）
        aiPlayer.setHand(currentPlayer.getHandCards());

        // 获取决策所需状态
        List<Card> lastPlayCards = gameEngine.getLastPlayCards();
        boolean isFirstRound = gameEngine.isFirstRound();
        boolean isFirstTurn = gameEngine.isFirstTurnOfCurrentRound();

        // AI 选择出牌
        List<Card> chosenCards = aiPlayer.choosePlay(lastPlayCards, isFirstRound, isFirstTurn);

        if (chosenCards == null || chosenCards.isEmpty()) {
            // 无合法牌，Pass
            System.out.println("[CardGame][AI] " + currentPlayer.getPlayerId() + " 选择 Pass");
            passTurn();
        } else {
            // 出牌：将 Card 对象转为 cardId 列表
            List<String> cardIds = chosenCards.stream().map(Card::getCardId).collect(Collectors.toList());
            System.out.println("[CardGame][AI] " + currentPlayer.getPlayerId() + " 出牌: " + cardIds);
            submitPlay(cardIds);
        }
    }

    /**
     * 触发 AI 行动。如果当前玩家是 AI，则延迟 3 秒后再出牌，让高亮停留更长时间。
     */
//    public void triggerAITurn() {
//        if (gameEngine.isGameOver()) {
//            return;
//        }
//        GameState state = gameEngine.getGameState();
//        if (state == null) return;
//        Player current = state.getCurrentPlayer();
//        if (current == null) return;
//
//        if (!MY_PLAYER_ID.equals(current.getPlayerId())) {
//            // AI 玩家：延迟 3 秒后再出牌，让 UI 高亮显示足够长时间
//            long aiThinkTime = 3000; // 3 秒（可根据需要调整）
//            new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                // 延迟后再次检查游戏状态和当前玩家，避免状态变化导致错误
//                if (!gameEngine.isGameOver() && gameEngine.getGameState() != null
//                        && current.equals(gameEngine.getGameState().getCurrentPlayer())) {
//                    autoPlayForCurrentPlayer();
//                } else {
//                    System.out.println("[CardGame][AI] 延迟出牌时状态已变化，取消出牌");
//                }
//            }, aiThinkTime);
//        }
//        // 如果当前玩家是真人，什么都不做，等待用户手动操作
//    }

    public void triggerNextAction() {
        if (gameEngine.isGameOver()) return;
        Player current = gameEngine.getGameState().getCurrentPlayer();
        if (current == null) return;


        switch (current.getType()) {
            case HUMAN:
                // 什么都不做，等待真人玩家点击界面的出牌/过牌按钮
                break;

            case AI:
                // 触发 AI 出牌（带延迟，模拟思考）
                long aiThinkTime = 3000;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (current.equals(gameEngine.getGameState().getCurrentPlayer())) {
                        autoPlayForCurrentPlayer();
                    }
                }, aiThinkTime);
                break;

            case REMOTE:
                // 触发蓝牙等待
                // 什么都不做，等待接收局域网内另一台手机发来的蓝牙指令
                // 收到指令后，蓝牙模块会去调用 gameEngine.executeRemotePlay()
                System.out.println("[CardGame][BLUETOOTH] 等待远程玩家出牌...");
                break;
        }
    }
}