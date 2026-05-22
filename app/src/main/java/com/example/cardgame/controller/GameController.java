package com.example.cardgame.controller;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import com.example.cardgame.ai.AIDecisionStrategy;
import com.example.cardgame.ai.AIEventListener;
import com.example.cardgame.ai.GreedyAIDecisionStrategy;
import com.example.cardgame.dto.GameViewData;
import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.dto.PlayerViewData;
import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.event.EventBus;
import com.example.cardgame.event.TurnChangedEvent;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.model.Player;
import com.example.cardgame.model.PlayerType;
import com.example.cardgame.rule.PlayValidator;
import com.example.cardgame.rule.RuleConfig;
import com.example.cardgame.util.HermesLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GameController implements GameActionHandler {

    private final GameEngine gameEngine;
    private final List<String> selectedCardIds = new ArrayList<>();

    private String myPlayerId = "P1";
    private boolean bluetoothMode = false;
    private boolean hostMode = false;
    private BluetoothActionHandler bluetoothActionHandler;

    private AIEventListener aiEventListener;
    private Runnable uiRefreshCallback;

    private final PlayValidator playValidator = new PlayValidator();
    private final Map<String, CountDownTimer> activeCountdowns = new HashMap<>();
    private static final long NO_PLAY_WAIT_MS = 3000;
    private CountdownUICallback countdownCallback;

    public interface CountdownUICallback {
        void showCountdown();
        void updateCountdown(int secondsLeft);
        void hideCountdown();
    }

    public void setCountdownCallback(CountdownUICallback callback) {
        this.countdownCallback = callback;
    }

    public GameController(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
    }

    @Override
    public void setUiRefreshCallback(Runnable callback) {
        this.uiRefreshCallback = callback;
    }

    @Override
    public void setBluetoothActionHandler(BluetoothActionHandler bluetoothActionHandler) {
        this.bluetoothActionHandler = bluetoothActionHandler;
    }

    @Override
    public void setBluetoothMode(boolean bluetoothMode, boolean hostMode, String localPlayerId) {
        this.bluetoothMode = bluetoothMode;
        if (!bluetoothMode) {
            this.hostMode = false;
            this.myPlayerId = "P1";
            return;
        }
        this.hostMode = hostMode;
        this.myPlayerId = localPlayerId != null ? localPlayerId : (hostMode ? "P1" : "P2");
        if (gameEngine.getGameState() != null) {
            gameEngine.configureBluetoothPlayerTypes(this.myPlayerId, "P1".equals(this.myPlayerId) ? "P2" : "P1");
        }
    }

    private void notifyUiRefresh() {
        if (uiRefreshCallback != null) uiRefreshCallback.run();
    }

    @Override
    public void startNewGame() {
        if (!bluetoothMode) myPlayerId = "P1";

        List<Player> players = new ArrayList<>();
        Player p1 = new Player("P1", "Alice");
        Player p2 = new Player("P2", "Bob");
        Player p3 = new Player("P3", "Cindy");
        Player p4 = new Player("P4", "David");

        if (bluetoothMode) {
            p1.setType(PlayerType.AI);
            p2.setType(PlayerType.AI);
            p3.setType(PlayerType.AI);
            p4.setType(PlayerType.AI);
        } else {
            p1.setType(PlayerType.HUMAN);
            p2.setType(PlayerType.AI);
            p3.setType(PlayerType.AI);
            p4.setType(PlayerType.AI);
        }
        players.add(p1);
        players.add(p2);
        players.add(p3);
        players.add(p4);

        for (Player p : players) p.resetConsecutiveNoPlayCount();

        RuleConfig ruleConfig = new RuleConfig();
        gameEngine.initializeGame(players, ruleConfig);
        gameEngine.dealCards();
        // 在 gameEngine.dealCards(); 之后添加
        if (!bluetoothMode) {
            for (Player p : gameEngine.getGameState().getPlayers()) {
                if (p.getPlayerId().equals("P1")) {
                    p.setType(PlayerType.HUMAN);
                } else {
                    p.setType(PlayerType.AI);
                }
                System.out.println("[GameController] Player " + p.getPlayerId() + " type = " + p.getType());
            }
        }

        if (bluetoothMode) {
            List<String> remoteIds = bluetoothActionHandler != null
                    ? bluetoothActionHandler.getRemotePlayerIds()
                    : null;
            if (remoteIds != null && !remoteIds.isEmpty()) {
                Map<String, String> typeMap = new HashMap<>();
                typeMap.put(myPlayerId, "HUMAN");
                for (String remoteId : remoteIds) {
                    typeMap.put(remoteId, "REMOTE");
                }
                gameEngine.configureBluetoothPlayerTypesMulti(typeMap);
                System.out.println("[CardGame][BLUETOOTH] Player types configured (multi) | "
                        + "local=" + myPlayerId + ", remote=" + remoteIds);
            } else {
                gameEngine.configureBluetoothPlayerTypes(
                        myPlayerId,
                        "P1".equals(myPlayerId) ? "P2" : "P1"
                );
                System.out.println("[CardGame][BLUETOOTH] Player types configured (legacy) | "
                        + "local=" + myPlayerId);
            }
        }

        initAIEventListener();

        if (bluetoothMode && hostMode && bluetoothActionHandler != null) {
            HermesLog.log("GAME startNewGame calling readyForGame+syncGameState");
            bluetoothActionHandler.readyForGame();
            bluetoothActionHandler.syncGameState(gameEngine.getGameState());
            HermesLog.log("GAME startNewGame syncGameState returned");
        }

        notifyUiRefresh();

        // 手动发布初始回合事件，确保 AI 收到 TurnChangedEvent
        GameState state = gameEngine.getGameState();
        if (state != null) {
            String currentId = state.getCurrentPlayerId();
            if (currentId == null) {
                Player opener = state.findOpeningPlayer();
                if (opener != null) {
                    currentId = opener.getPlayerId();
                    state.setCurrentPlayerId(currentId);
                    System.out.println("[CardGame][FIX] Set current player to opener: " + currentId);
                }
            }
            if (currentId != null) {
                EventBus.getInstance().post(new TurnChangedEvent(currentId, "GAME_START"));
                HermesLog.log("GameController: Manual TurnChangedEvent posted for " + currentId);
            }
        }

        triggerNextAction();
    }

    private void initAIEventListener() {
        if (aiEventListener != null) {
            aiEventListener.unregister();
        }
        AIDecisionStrategy strategy = new GreedyAIDecisionStrategy();
        aiEventListener = new AIEventListener(this, gameEngine, strategy);
        HermesLog.log("GameController: AIEventListener initialized");
    }

    // ==================== 接口方法：供 UI 调用（显示字符串） ====================
    @Override
    public PlayResult submitPlay(List<String> uiCardStrs) {
        GameState state = gameEngine.getGameState();
        if (state == null || state.getCurrentPlayer() == null) {
            return new PlayResult(false, "Game state not ready.", state);
        }
        Player currentPlayer = state.getCurrentPlayer();
        if (!myPlayerId.equals(currentPlayer.getPlayerId())) {
            return new PlayResult(false, "不是您的回合", state);
        }

        List<String> cardsToPlay = new ArrayList<>();
        Player me = state.getPlayerById(myPlayerId);
        if (me != null) {
            for (String uiCardStr : uiCardStrs) {
                for (Card c : me.getHandCards()) {
                    if ((c.getSuit().getSymbol() + c.getRank().getDisplayName()).equals(uiCardStr)) {
                        cardsToPlay.add(c.getCardId());
                        break;
                    }
                }
            }
        }
        if (cardsToPlay.isEmpty()) {
            return new PlayResult(false, "请先选择要出的牌", state);
        }

        PlayResult result = gameEngine.playCards(currentPlayer.getPlayerId(), cardsToPlay);
        if (result.isSuccess()) {
            selectedCardIds.clear();
            currentPlayer.resetConsecutiveNoPlayCount();
            cancelCountdown(currentPlayer);

            if (bluetoothMode && bluetoothActionHandler != null && gameEngine.getGameState() != null) {
                Play lastPlay = gameEngine.getGameState().getLastPlay();
                if (lastPlay != null) bluetoothActionHandler.sendLocalPlay(lastPlay);
                sendGameOverIfNeeded();
            }
            notifyUiRefresh();
            if (!gameEngine.isGameOver()) {
                new Handler(Looper.getMainLooper()).postDelayed(this::triggerNextAction, 100);
            }
        }
        return result;
    }

    // ==================== AI 专用方法（直接传入 Card 对象） ====================
    public PlayResult aiPlayCards(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            System.out.println("[CardGame][AI] aiPlayCards: no cards");
            return new PlayResult(false, "No cards to play", gameEngine.getGameState());
        }
        GameState state = gameEngine.getGameState();
        if (state == null || state.getCurrentPlayer() == null) {
            System.out.println("[CardGame][AI] aiPlayCards: game state not ready");
            return new PlayResult(false, "Game state not ready.", state);
        }
        Player currentPlayer = state.getCurrentPlayer();
        // 移除 myPlayerId 检查，因为 AI 调用时已经是当前玩家
        // 可选：添加类型检查确保是 AI（但非必须）
        if (currentPlayer.getType() != PlayerType.AI) {
            System.out.println("[CardGame][AI] aiPlayCards: current player is not AI");
            return new PlayResult(false, "Current player is not AI", state);
        }

        List<String> cardIds = cards.stream().map(Card::getCardId).collect(Collectors.toList());
        System.out.println("[CardGame][AI] aiPlayCards calling engine.playCards for " + currentPlayer.getPlayerId() + " with " + cardIds);
        PlayResult result = gameEngine.playCards(currentPlayer.getPlayerId(), cardIds);
        System.out.println("[CardGame][AI] aiPlayCards result: success=" + result.isSuccess() + ", message=" + result.getMessage());
        if (!result.isSuccess()) {
            System.out.println("[CardGame][AI] aiPlayCards FAILED: " + result.getMessage());
        }
        if (result.isSuccess()) {
            currentPlayer.resetConsecutiveNoPlayCount();
            cancelCountdown(currentPlayer);

            if (bluetoothMode && bluetoothActionHandler != null && gameEngine.getGameState() != null) {
                Play lastPlay = gameEngine.getGameState().getLastPlay();
                if (lastPlay != null) bluetoothActionHandler.sendLocalPlay(lastPlay);
                sendGameOverIfNeeded();
            }
            notifyUiRefresh();
            if (!gameEngine.isGameOver()) {
                new Handler(Looper.getMainLooper()).postDelayed(this::triggerNextAction, 100);
            }
        }
        return result;
    }

    @Override
    public PassResult passTurn() {
        GameState state = gameEngine.getGameState();
        if (state == null || state.getCurrentPlayer() == null) {
            return new PassResult(false, "Game state not ready.", state);
        }
        Player currentPlayer = state.getCurrentPlayer();
        if (!myPlayerId.equals(currentPlayer.getPlayerId())) {
            return new PassResult(false, "不是您的回合", state);
        }

        cancelCountdown(currentPlayer);
        PassResult result = gameEngine.passTurn(currentPlayer.getPlayerId());
        if (result.isSuccess()) {
            if (bluetoothMode && bluetoothActionHandler != null) {
                bluetoothActionHandler.sendLocalPass(currentPlayer.getPlayerId());
            }
            notifyUiRefresh();
            if (!gameEngine.isGameOver()) {
                new Handler(Looper.getMainLooper()).postDelayed(this::triggerNextAction, 100);
            }
        }
        return result;
    }

    @Override
    public void toggleCardSelection(String cardId) {
        if (selectedCardIds.contains(cardId)) {
            selectedCardIds.remove(cardId);
        } else {
            selectedCardIds.add(cardId);
        }
        notifyUiRefresh();
    }

    @Override
    public GameViewData getGameViewData() {
        GameState state = gameEngine.getGameState();
        if (state == null) return emptyViewData();
        Player currentPlayer = state.getCurrentPlayer();
        Player me = state.getPlayerById(myPlayerId);
        if (currentPlayer == null || me == null) return emptyViewData();

        List<PlayerViewData> players = new ArrayList<>();
        for (Player p : state.getPlayers()) {
            players.add(new PlayerViewData(p.getPlayerId(), p.getPlayerName(),
                    p.getHandCards().size(), p.equals(currentPlayer), p.isPassed(),
                    p.getType() == PlayerType.HUMAN));
        }

        players = reorderPlayersForSelf(players, myPlayerId);

        Player winner = state.getWinnerId() != null ? state.getPlayerById(state.getWinnerId()) : null;

        List<Card> handCardsList = new ArrayList<>(me.getHandCards());
        handCardsList.sort((c1, c2) -> {
            int rankCompare = Integer.compare(c2.getRank().getWeight(), c1.getRank().getWeight());
            if (rankCompare != 0) return rankCompare;
            return Integer.compare(c2.getSuit().getWeight(), c1.getSuit().getWeight());
        });
        List<String> myHandCards = handCardsList.stream()
                .map(c -> c.getSuit().getSymbol() + c.getRank().getDisplayName())
                .collect(Collectors.toList());

        Map<String, List<String>> playerLastPlayCards = new HashMap<>();
        Map<String, List<Card>> rawMap = state.getLastPlayByPlayer();
        if (rawMap != null) {
            for (Map.Entry<String, List<Card>> entry : rawMap.entrySet()) {
                String pid = entry.getKey();
                List<Card> cards = entry.getValue();
                if (cards != null && !cards.isEmpty()) {
                    playerLastPlayCards.put(pid, cards.stream()
                            .map(c -> c.getSuit().getSymbol() + c.getRank().getDisplayName())
                            .collect(Collectors.toList()));
                } else {
                    playerLastPlayCards.put(pid, new ArrayList<>());
                }
            }
        }

        return new GameViewData(me.getPlayerId(), me.getPlayerName(), players,
                new ArrayList<>(selectedCardIds), myHandCards,
                state.getLastPlay() == null ? "" : state.getLastPlay().toString(),
                gameEngine.isGameOver(),
                gameEngine.isGameOver() && winner != null ? winner.getPlayerName() : "",
                playerLastPlayCards);
    }

    private GameViewData emptyViewData() {
        return new GameViewData("", "", new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), "", false, "", new HashMap<>());
    }

    private List<PlayerViewData> reorderPlayersForSelf(List<PlayerViewData> original, String myPlayerId) {
        if (original == null || original.size() < 4) return original;
        int myIndex = -1;
        for (int i = 0; i < original.size(); i++) {
            if (original.get(i).getPlayerId().equals(myPlayerId)) {
                myIndex = i;
                break;
            }
        }
        if (myIndex == -1) return original;
        List<PlayerViewData> reordered = new ArrayList<>();
        for (int i = 0; i < original.size(); i++) {
            int index = (myIndex + i) % original.size();
            reordered.add(original.get(index));
        }
        return reordered;
    }

    private void checkAndStartNoPlayCountdown(Player player) {
        if (!myPlayerId.equals(player.getPlayerId()) || player.getType() != PlayerType.HUMAN) return;
        GameState state = gameEngine.getGameState();
        if (state == null) return;

        boolean isFirstRound = gameEngine.isFirstRound();
        boolean isFirstTurn = state.isOpeningTurn();
        List<Card> lastPlay = gameEngine.getLastPlayCards();

        boolean hasAnyValid = playValidator.hasAnyValidPlay(player, lastPlay, isFirstRound, isFirstTurn);
        int count = player.getConsecutiveNoPlayCount();
        System.out.println("[CardGame][COUNTDOWN] player=" + player.getPlayerId()
                + ", hasAnyValid=" + hasAnyValid
                + ", consecutiveNoPlayCount=" + count);

        if (!hasAnyValid) {
            if (count == 0) {
                startNoPlayCountdown(player);
            } else {
                forcePass(player);
            }
        } else {
            cancelCountdown(player);
        }
    }

    private void startNoPlayCountdown(Player player) {
        cancelCountdown(player);
        CountDownTimer timer = new CountDownTimer(NO_PLAY_WAIT_MS, 500) {
            int lastDisplaySecond = -1;
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                if (seconds != lastDisplaySecond) {
                    lastDisplaySecond = seconds;
                    if (countdownCallback != null) {
                        countdownCallback.updateCountdown(seconds);
                    }
                }
            }
            @Override
            public void onFinish() {
                forcePass(player);
            }
        };
        activeCountdowns.put(player.getPlayerId(), timer);
        timer.start();
        if (countdownCallback != null) {
            countdownCallback.showCountdown();
        }
        System.out.println("[CardGame][COUNTDOWN] Started for player " + player.getPlayerId());
    }

    private void forcePass(Player player) {
        player.incrementConsecutiveNoPlayCount();
        System.out.println("[CardGame][COUNTDOWN] Force pass for " + player.getPlayerId()
                + ", consecutiveNoPlayCount now = " + player.getConsecutiveNoPlayCount());

        PassResult result = gameEngine.passTurn(player.getPlayerId());
        if (result.isSuccess()) {
            if (bluetoothMode && bluetoothActionHandler != null) {
                bluetoothActionHandler.sendLocalPass(player.getPlayerId());
            }
            notifyUiRefresh();
            if (!gameEngine.isGameOver()) {
                new Handler(Looper.getMainLooper()).postDelayed(this::triggerNextAction, 100);
            }
        } else {
            System.out.println("[CardGame][COUNTDOWN] forcePass failed: " + result.getMessage());
            player.setConsecutiveNoPlayCount(player.getConsecutiveNoPlayCount() - 1);
        }

        if (countdownCallback != null) {
            countdownCallback.hideCountdown();
        }
        activeCountdowns.remove(player.getPlayerId());
    }

    private void cancelCountdown(Player player) {
        CountDownTimer timer = activeCountdowns.remove(player.getPlayerId());
        if (timer != null) {
            timer.cancel();
            if (countdownCallback != null) {
                countdownCallback.hideCountdown();
            }
            System.out.println("[CardGame][COUNTDOWN] Cancelled for player " + player.getPlayerId());
        }
    }

    private void sendGameOverIfNeeded() {
        if (!bluetoothMode || bluetoothActionHandler == null) return;
        if (!gameEngine.isGameOver()) return;
        GameState state = gameEngine.getGameState();
        if (state == null || state.getWinnerId() == null) return;
        Player winner = state.getPlayerById(state.getWinnerId());
        String winnerName = winner != null ? winner.getPlayerName() : state.getWinnerId();
        bluetoothActionHandler.sendGameOver(state.getWinnerId(), winnerName);
    }

    // 注意：如果 GameActionHandler 接口中没有 triggerNextAction，请删除下面的 @Override
    public void triggerNextAction() {
        if (gameEngine.isGameOver() || gameEngine.getGameState() == null) return;
        Player current = gameEngine.getGameState().getCurrentPlayer();
        if (current == null) return;
        if (bluetoothMode && !hostMode && current.getType() != PlayerType.HUMAN) return;

        switch (current.getType()) {
            case HUMAN:
                checkAndStartNoPlayCountdown(current);
                break;
            case AI:
                // AI 由事件总线驱动，无需额外处理
                break;
            case REMOTE:
                System.out.println("[CardGame][BLUETOOTH] 等待远程玩家出牌...");
                break;
        }
    }

    public void cleanup() {
        if (aiEventListener != null) {
            aiEventListener.unregister();
            aiEventListener = null;
        }
        for (CountDownTimer timer : activeCountdowns.values()) {
            timer.cancel();
        }
        activeCountdowns.clear();
    }
}