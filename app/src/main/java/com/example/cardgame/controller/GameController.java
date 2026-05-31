package com.example.cardgame.controller;
import com.example.cardgame.ai.MonteCarloAIDecisionStrategy;
import android.content.Context;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.example.cardgame.ai.AIDecisionStrategy;
import com.example.cardgame.ai.AdaptiveAIDecisionStrategy;
import com.example.cardgame.ai.AIEventListener;
import com.example.cardgame.ai.AIPlayerProfile;
import com.example.cardgame.ai.GreedyAIDecisionStrategy;
import com.example.cardgame.ai.HumanStyleAnalyzer;
import com.example.cardgame.dto.GameViewData;
import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.dto.PlayerViewData;
import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.event.EventBus;
import com.example.cardgame.event.TurnChangedEvent;
import com.example.cardgame.llm.OpponentStyleAnalyzer;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.HumanStyleProfile;
import com.example.cardgame.model.Player;
import com.example.cardgame.model.PlayerType;
import com.example.cardgame.rule.PlayValidator;
import com.example.cardgame.rule.RuleConfig;
import com.example.cardgame.util.CardTracker;
import com.example.cardgame.util.CrossGameMemoryManager;
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
    private AIDecisionStrategy aiStrategy;

    // 自适应AI相关字段
    private HumanStyleAnalyzer styleAnalyzer;
    private CrossGameMemoryManager memoryManager;
    private AdaptiveAIDecisionStrategy adaptiveAI;
    private List<String> currentHumanActions = new ArrayList<>();
    private Context appContext;

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

    public void initAdaptiveAI(Context context) {
        this.appContext = context.getApplicationContext();
        this.styleAnalyzer = new HumanStyleAnalyzer();
        this.memoryManager = new CrossGameMemoryManager(appContext);
        this.adaptiveAI = new AdaptiveAIDecisionStrategy();
        this.aiStrategy = adaptiveAI;
        
        HumanStyleProfile savedProfile = memoryManager.loadHumanStyleProfile(myPlayerId);
        if (savedProfile != null) {
            adaptiveAI.setHumanStyleProfile(savedProfile);
            HermesLog.log("GameController: Loaded saved human style: " + savedProfile.getStyleLabel());
        }
    }

    /**
     * 清理自适应AI资源
     * 应在 Activity/Fragment 销毁时调用
     */
    public void cleanupAdaptiveAI() {
        HermesLog.log("GameController: cleanupAdaptiveAI() called");
        
        if (styleAnalyzer != null) {
            HermesLog.log("GameController: Shutting down styleAnalyzer...");
            styleAnalyzer.shutdown();
            HermesLog.log("GameController: styleAnalyzer shutdown complete");
        }
        
        int actionCount = currentHumanActions.size();
        currentHumanActions.clear();
        HermesLog.log("GameController: Cleared " + actionCount + " human actions");
        HermesLog.log("GameController: cleanupAdaptiveAI() finished");
    }

    public void setAIDifficulty(com.example.cardgame.ai.AIDifficulty difficulty) {
        switch (difficulty) {
            case GREEDY:
                aiStrategy = new GreedyAIDecisionStrategy();
                break;
            case MONTE_CARLO:
                aiStrategy = new MonteCarloAIDecisionStrategy();
                break;
            case ADAPTIVE:
                if (adaptiveAI == null) {
                    adaptiveAI = new AdaptiveAIDecisionStrategy();
                }
                aiStrategy = adaptiveAI;
                break;
        }
        HermesLog.log("GameController: AI difficulty set to " + difficulty);
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

    @Override
    public void startNewGame() {
        if (!bluetoothMode) myPlayerId = "P1";
        
        if (currentHumanActions == null) {
            currentHumanActions = new ArrayList<>();
        } else {
            currentHumanActions.clear();
        }
        HermesLog.log("GameController: Reset currentHumanActions for new game, size: " + currentHumanActions.size());

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
        if (aiEventListener != null) aiEventListener.unregister();
        aiStrategy = new MonteCarloAIDecisionStrategy();
        aiEventListener = new AIEventListener(this, gameEngine, aiStrategy);
        HermesLog.log("GameController: MonteCarlo AI Strategy initialized");
    }

    public CardTracker getCardTracker() {
        if (aiStrategy instanceof MonteCarloAIDecisionStrategy) {
            return ((MonteCarloAIDecisionStrategy) aiStrategy).getCardTracker();
        }
        return null;
    }

    private void recordPlayToTracker(String playerId, List<Card> cards) {
        HermesLog.log("GameController: recordPlayToTracker called for playerId=" + playerId);
        CardTracker tracker = getCardTracker();
        HermesLog.log("GameController: tracker is null? " + (tracker == null));
        if (tracker == null || cards == null || cards.isEmpty()) {
            HermesLog.log("GameController: recordPlayToTracker early return - tracker=null?" + (tracker == null) + ", cards=null?" + (cards == null) + ", cards empty?" + (cards != null && cards.isEmpty()));
            return;
        }
        StringBuilder desc = new StringBuilder();
        for (Card c : cards) {
            if (desc.length() > 0) desc.append(",");
            desc.append(c.getSuit().getSymbol()).append(c.getRank().getDisplayName());
        }
        tracker.recordPlay(playerId, desc.toString());
        
        // 新增：记录人类出牌动作（用于自适应AI）
        GameState state = gameEngine.getGameState();
        if (state != null) {
            Player player = state.getPlayerById(playerId);
            if (player != null && player.getType() == PlayerType.HUMAN && currentHumanActions != null) {
                currentHumanActions.add(desc.toString());
                HermesLog.log("GameController: Recorded human action: " + desc.toString());
            }
        }
    }

    public void triggerOpponentAnalysis() {
        CardTracker tracker = getCardTracker();
        if (tracker == null) return;
        
        if (aiStrategy instanceof MonteCarloAIDecisionStrategy) {
            MonteCarloAIDecisionStrategy mcStrategy = (MonteCarloAIDecisionStrategy) aiStrategy;
            OpponentStyleAnalyzer analyzer = new OpponentStyleAnalyzer();
            GameState state = gameEngine.getGameState();
            if (state == null) return;
            for (Player p : state.getPlayers()) {
                if (p.getType() == PlayerType.AI) {
                    String history = tracker.getHistorySummary(p.getPlayerId());
                    if (history.isEmpty()) continue;
                    AIPlayerProfile profile = mcStrategy.getOpponentProfile(p.getPlayerId());
                    if (profile == null) {
                        profile = new AIPlayerProfile(AIPlayerProfile.LEVEL_NORMAL);
                        mcStrategy.setOpponentProfile(p.getPlayerId(), profile);
                    }
                    analyzer.analyzeAndUpdate(p.getPlayerId(), tracker, profile);
                }
            }
        }
        
        analyzeHumanStyleAndAdapt();
    }

    private void analyzeHumanStyleAndAdapt() {
        if (styleAnalyzer == null || memoryManager == null || adaptiveAI == null) {
            HermesLog.log("GameController: Adaptive AI not initialized, skipping human style analysis");
            return;
        }
        
        if (currentHumanActions.isEmpty()) {
            HermesLog.log("GameController: No human actions recorded, skipping analysis");
            return;
        }
        
        styleAnalyzer.setCallback(new HumanStyleAnalyzer.StyleAnalysisCallback() {
            @Override
            public void onAnalysisComplete(HumanStyleProfile profile) {
                HermesLog.log("GameController: onAnalysisComplete called");
                
                if (appContext != null) {
                    memoryManager.saveHumanStyleProfile(myPlayerId, profile);
                }
                adaptiveAI.setHumanStyleProfile(profile);
                
                String tactic = profile.getCounterTactic();
                String styleLabel = profile.getStyleLabel();
                HermesLog.log("GameController: Human style analyzed: " + styleLabel);
                HermesLog.log("GameController: Recommended tactic: " + tactic);
                
                // 显示 Toast 提示
                if (appContext != null) {
                    HermesLog.log("GameController: Showing Toast for style analysis");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        String message = "对手风格：" + styleLabel + "\nAI采用" + tactic + "策略";
                        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show();
                        HermesLog.log("GameController: Toast message: " + message);
                    });
                } else {
                    HermesLog.log("GameController: appContext is null, cannot show Toast");
                }
            }

            @Override
            public void onAnalysisFailed(String error) {
                HermesLog.log("GameController: Human style analysis failed: " + error);
            }
        });
        
        HumanStyleProfile existingProfile = memoryManager.loadHumanStyleProfile(myPlayerId);
        styleAnalyzer.analyzeStyleAsync(myPlayerId, currentHumanActions, existingProfile);
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
        List<Card> playedCards = new ArrayList<>(); // 在 playCards 之前提取
        Player me = state.getPlayerById(myPlayerId);
        if (me != null) {
            for (String uiCardStr : uiCardStrs) {
                for (Card c : me.getHandCards()) {
                    if ((c.getSuit().getSymbol() + c.getRank().getDisplayName()).equals(uiCardStr)) {
                        cardsToPlay.add(c.getCardId());
                        playedCards.add(c); // 关键：在出牌前保存 Card 对象
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
            recordPlayToTracker(currentPlayer.getPlayerId(), playedCards);
            
            // 【直接记录人类动作】使用出牌前提取的 Card 对象
            if (currentHumanActions == null) {
                currentHumanActions = new ArrayList<>();
                HermesLog.log("GameController: Created new currentHumanActions list");
            }
            
            HermesLog.log("GameController: playedCards size = " + playedCards.size());
            
            StringBuilder desc = new StringBuilder();
            for (Card c : playedCards) {
                if (desc.length() > 0) desc.append(",");
                String suitSymbol = (c.getSuit() != null && c.getSuit().getSymbol() != null) ? c.getSuit().getSymbol() : "?";
                String rankName = (c.getRank() != null && c.getRank().getDisplayName() != null) ? c.getRank().getDisplayName() : c.getCardId();
                desc.append(suitSymbol).append(rankName);
            }
            
            // 如果 desc 仍然为空，则使用 cardsToPlay 的 ID 列表
            if (desc.length() == 0 && cardsToPlay != null && !cardsToPlay.isEmpty()) {
                desc.append(String.join(",", cardsToPlay));
                HermesLog.log("GameController: Using cardsToPlay as fallback, desc: " + desc.toString());
            }
            
            String actionDesc = desc.toString();
            if (!actionDesc.isEmpty()) {
                currentHumanActions.add(actionDesc);
                HermesLog.log("GameController: DIRECT record human action: " + actionDesc + " | currentHumanActions size: " + currentHumanActions.size());
            } else {
                HermesLog.log("GameController: WARNING - action description is empty, skipping record");
            }
            selectedCardIds.clear();
            currentPlayer.resetConsecutiveNoPlayCount();
            cancelCountdown(currentPlayer);

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
            recordPlayToTracker(currentPlayer.getPlayerId(), cards);
            currentPlayer.resetConsecutiveNoPlayCount();
            cancelCountdown(currentPlayer);

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