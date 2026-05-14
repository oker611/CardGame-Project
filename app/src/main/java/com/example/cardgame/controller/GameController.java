package com.example.cardgame.controller;

import android.os.CountDownTimer;
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
import com.example.cardgame.model.Play;
import com.example.cardgame.model.Player;
import com.example.cardgame.model.PlayerType;
import com.example.cardgame.rule.PlayValidator;
import com.example.cardgame.rule.RuleConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GameController implements GameActionHandler {

    private final GameEngine gameEngine;
    private final List<String> selectedCardIds = new ArrayList<>();

    private String myPlayerId = "P1";
    private boolean bluetoothMode = false;
    private boolean hostMode = false;
    private BluetoothActionHandler bluetoothActionHandler;

    private final Map<String, AIPlayer> aiPlayerCache = new ConcurrentHashMap<>();

    private Runnable uiRefreshCallback;

    // 倒计时相关
    private final PlayValidator playValidator = new PlayValidator();
    private final Map<String, CountDownTimer> activeCountdowns = new HashMap<>();
    private static final long NO_PLAY_WAIT_MS = 3000; // 3秒
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
            p1.setType("P1".equals(myPlayerId) ? PlayerType.HUMAN : PlayerType.REMOTE);
            p2.setType("P2".equals(myPlayerId) ? PlayerType.HUMAN : PlayerType.REMOTE);
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

        // 重置所有玩家的连续无牌可出计数
        for (Player p : players) p.resetConsecutiveNoPlayCount();

        RuleConfig ruleConfig = new RuleConfig();
        gameEngine.initializeGame(players, ruleConfig);
        gameEngine.dealCards();

        if (bluetoothMode) {
            gameEngine.configureBluetoothPlayerTypes(myPlayerId, "P1".equals(myPlayerId) ? "P2" : "P1");
        }
        aiPlayerCache.clear();

        if (bluetoothMode && hostMode && bluetoothActionHandler != null) {
            bluetoothActionHandler.syncGameState(gameEngine.getGameState());
        }
        notifyUiRefresh();
        triggerNextAction();
    }

    @Override
    public PlayResult submitPlay(List<String> selectedCardIds) {
        GameState state = gameEngine.getGameState();
        if (state == null || state.getCurrentPlayer() == null) {
            return new PlayResult(false, "Game state not ready.", state);
        }
        Player currentPlayer = state.getCurrentPlayer();
        if (!myPlayerId.equals(currentPlayer.getPlayerId()) || currentPlayer.getType() != PlayerType.HUMAN) {
            return new PlayResult(false, "不是您的回合", state);
        }

        List<String> cardsToPlay = new ArrayList<>();
        Player me = state.getPlayerById(myPlayerId);
        if (me != null && this.selectedCardIds != null) {
            for (String uiCardStr : this.selectedCardIds) {
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
            this.selectedCardIds.clear();
            // 出牌成功，重置计数并取消倒计时
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
        if (!myPlayerId.equals(currentPlayer.getPlayerId()) || currentPlayer.getType() != PlayerType.HUMAN) {
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

    // ========== 倒计时核心方法 ==========
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
        // 使用 500ms 间隔，提高刷新精度，确保显示 3,2,1
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
        // 先增加计数（必须在 pass 之前，避免重复进入倒计时）
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
            // 理论上不会失败，但若失败则回滚计数（可选）
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

    // ========== AI和蓝牙辅助方法 ==========
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
        if (state == null || gameEngine.isGameOver()) return;
        Player currentPlayer = state.getCurrentPlayer();
        if (currentPlayer == null || myPlayerId.equals(currentPlayer.getPlayerId())) return;

        AIPlayer aiPlayer = getOrCreateAIPlayer(currentPlayer);
        if (aiPlayer == null) {
            List<Card> randomCards = currentPlayer.getRandomCards(2);
            if (randomCards == null || randomCards.isEmpty()) {
                PassResult passResult = gameEngine.passTurn(currentPlayer.getPlayerId());
                syncAiPassIfNeeded(currentPlayer, passResult);
            } else {
                List<String> cardIds = randomCards.stream().map(Card::getCardId).collect(Collectors.toList());
                PlayResult playResult = gameEngine.playCards(currentPlayer.getPlayerId(), cardIds);
                syncAiPlayIfNeeded(playResult);
            }
        } else {
            aiPlayer.setHand(currentPlayer.getHandCards());
            List<Card> lastPlayCards = gameEngine.getLastPlayCards();
            boolean isFirstRound = gameEngine.isFirstRound();
            boolean isFirstTurn = gameEngine.isFirstTurnOfCurrentRound();
            List<Card> chosenCards = aiPlayer.choosePlay(lastPlayCards, isFirstRound, isFirstTurn);
            if (chosenCards == null || chosenCards.isEmpty()) {
                PassResult passResult = gameEngine.passTurn(currentPlayer.getPlayerId());
                syncAiPassIfNeeded(currentPlayer, passResult);
            } else {
                List<String> cardIds = chosenCards.stream().map(Card::getCardId).collect(Collectors.toList());
                PlayResult playResult = gameEngine.playCards(currentPlayer.getPlayerId(), cardIds);
                syncAiPlayIfNeeded(playResult);
            }
        }
        notifyUiRefresh();
        if (!gameEngine.isGameOver()) {
            new Handler(Looper.getMainLooper()).postDelayed(this::triggerNextAction, 100);
        }
    }

    private void syncAiPassIfNeeded(Player currentPlayer, PassResult passResult) {
        if (bluetoothMode && hostMode && bluetoothActionHandler != null && passResult != null && passResult.isSuccess()) {
            bluetoothActionHandler.sendLocalPass(currentPlayer.getPlayerId());
        }
    }

    private void syncAiPlayIfNeeded(PlayResult playResult) {
        if (bluetoothMode && hostMode && bluetoothActionHandler != null && playResult != null && playResult.isSuccess()) {
            Play lastPlay = gameEngine.getGameState() != null ? gameEngine.getGameState().getLastPlay() : null;
            if (lastPlay != null) bluetoothActionHandler.sendLocalPlay(lastPlay);
            sendGameOverIfNeeded();
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

    @Override
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
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (gameEngine.getGameState() != null && current.equals(gameEngine.getGameState().getCurrentPlayer())) {
                        autoPlayForCurrentPlayer();
                    }
                }, 3000);
                break;
            case REMOTE:
                System.out.println("[CardGame][BLUETOOTH] 等待远程玩家出牌...");
                break;
        }
    }
}