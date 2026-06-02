package com.example.cardgame.controller;
import com.example.cardgame.ai.MonteCarloAIDecisionStrategy;
import android.content.Context;
import android.content.SharedPreferences;
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
    private RuleConfig ruleConfig;
    private final List<String> selectedCardIds = new ArrayList<>();

    private String myPlayerId = "P1";
    private boolean bluetoothMode = false;
    private boolean hostMode = false;
    private String selectedRuleType = "南方规则";
    private BluetoothActionHandler bluetoothActionHandler;

    private AIEventListener aiEventListener;
    private AIDecisionStrategy aiStrategy;

    // 自适应AI相关字段
    private HumanStyleAnalyzer styleAnalyzer;
    private CrossGameMemoryManager memoryManager;
    private AdaptiveAIDecisionStrategy adaptiveAI;
    private List<String> currentHumanActions = new ArrayList<>();
    private Context appContext;
    private com.example.cardgame.llm.OpponentStyleAnalyzer opponentStyleAnalyzer;
    private String styleAnalysisSummary;

    private PlayValidator playValidator;
    private final Map<String, CountDownTimer> activeCountdowns = new HashMap<>();

    public void setSelectedRuleType(String ruleType) {
        this.selectedRuleType = ruleType != null ? ruleType : "南方规则";
        this.ruleConfig = resolveRuleConfig();
        this.playValidator = new PlayValidator(ruleConfig);
    }

    private RuleConfig ensureRuleConfigReady() {
        if (ruleConfig == null) {
            ruleConfig = resolveRuleConfig();
        }
        if (playValidator == null) {
            playValidator = new PlayValidator(ruleConfig);
        }
        return ruleConfig;
    }

    private RuleConfig resolveRuleConfig() {
        return "北方规则".equals(selectedRuleType)
                ? RuleConfig.NORTHERN : RuleConfig.SOUTHERN;
    }
  
    private static final long NO_PLAY_WAIT_MS = 3000;
    private CountdownUICallback countdownCallback;

    private long lastTriggerTime = 0;
    private static final long TRIGGER_COOLDOWN_MS = 1000;

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
        this.opponentStyleAnalyzer = new com.example.cardgame.llm.OpponentStyleAnalyzer();
        
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
                aiStrategy = new GreedyAIDecisionStrategy(ensureRuleConfigReady());
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
        cleanup();
        selectedCardIds.clear();
        lastTriggerTime = 0;
        if (!bluetoothMode) myPlayerId = "P1";

        // 清空规则引擎缓存（避免内存堆积）
        gameEngine.clearRuleCache();
        HermesLog.log("GameController: Cleared rule engine cache for new game");
        
        if (currentHumanActions == null) {
            currentHumanActions = new ArrayList<>();
        } else {
            currentHumanActions.clear();
        }
        HermesLog.log("GameController: Reset currentHumanActions for new game, size: " + currentHumanActions.size());

        List<Player> players = new ArrayList<>();
        Map<String, String> bluetoothNames = bluetoothMode && bluetoothActionHandler != null
                ? bluetoothActionHandler.getPlayerNamesById()
                : new HashMap<>();
        Player p1 = new Player("P1", playerNameFor("P1", "Alice", bluetoothNames));
        Player p2 = new Player("P2", playerNameFor("P2", "Bob", bluetoothNames));
        Player p3 = new Player("P3", playerNameFor("P3", "Cindy", bluetoothNames));
        Player p4 = new Player("P4", playerNameFor("P4", "David", bluetoothNames));

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

        this.ruleConfig = "北方规则".equals(selectedRuleType)
                ? RuleConfig.NORTHERN : RuleConfig.SOUTHERN;
        this.playValidator = new PlayValidator(ruleConfig);
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
                // 没有真实远程玩家（纯 AI 局），P1=HUMAN，其余=AI
                for (Player p : gameEngine.getGameState().getPlayers()) {
                    if (p.getPlayerId().equals(myPlayerId)) {
                        p.setType(PlayerType.HUMAN);
                    } else {
                        p.setType(PlayerType.AI);
                    }
                }
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

        // 获取规则配置
        RuleConfig ruleConfig = ensureRuleConfigReady();
        
        // 从SharedPreferences读取难度和策略
        String difficulty = getAIDifficulty();
        String strategy = getSelectedAIStrategy();
        
        // 调试日志
        HermesLog.log("GameController: initAIEventListener() - strategy=" + strategy + ", difficulty=" + difficulty);

        // 根据难度选择AI策略
        switch (difficulty) {
            case "EASY":
                // 简单模式：贪心算法 + 风格参数
                switch (strategy) {
                    case "AGGRESSIVE":
                        aiStrategy = new com.example.cardgame.ai.AggressiveAIDecisionStrategy(ruleConfig);
                        HermesLog.log("GameController: Easy-Aggressive (Greedy) AI Strategy initialized");
                        break;
                    case "DEFENSIVE":
                        aiStrategy = new com.example.cardgame.ai.DefensiveAIDecisionStrategy(ruleConfig);
                        HermesLog.log("GameController: Easy-Defensive (Greedy) AI Strategy initialized");
                        break;
                    default:
                        aiStrategy = new com.example.cardgame.ai.NormalAIDecisionStrategy(ruleConfig);
                        HermesLog.log("GameController: Easy-Normal (Greedy) AI Strategy initialized");
                        break;
                }
                break;
                
            case "MEDIUM":
                // 中等模式：标准蒙特卡洛
                aiStrategy = new MonteCarloAIDecisionStrategy();
                HermesLog.log("GameController: Medium (MonteCarlo) AI Strategy initialized");
                break;
                
            case "HARD":
                // 困难模式：蒙特卡洛（固定策略，不学习）
                aiStrategy = new MonteCarloAIDecisionStrategy();
                HermesLog.log("GameController: Hard (MonteCarlo) AI Strategy initialized");
                break;
                
            case "ADAPTIVE":
                // 智能模式：自适应AI（加载保存的风格，越玩越聪明）
                if (adaptiveAI == null) {
                    adaptiveAI = new AdaptiveAIDecisionStrategy();
                }
                
                // 始终加载保存的风格（如果有）
                HumanStyleProfile savedProfile = memoryManager.loadHumanStyleProfile(myPlayerId);
                if (savedProfile != null) {
                    adaptiveAI.setHumanStyleProfile(savedProfile);
                    HermesLog.log("GameController: Loaded saved human style: " + savedProfile.getStyleLabel());
                }
                
                aiStrategy = adaptiveAI;
                HermesLog.log("GameController: Adaptive AI Strategy initialized");
                break;
                
            default:
                // 默认使用中等难度
                aiStrategy = new MonteCarloAIDecisionStrategy();
                HermesLog.log("GameController: Default (MonteCarlo) AI Strategy initialized");
                break;
        }
        
        // 非蓝牙模式或蓝牙 HOST 模式才运行 AI；CLIENT 端 AI 由网络消息驱动
        boolean aiHost = !bluetoothMode || hostMode;
        aiEventListener = new AIEventListener(this, gameEngine, aiStrategy, aiHost);
        HermesLog.log("GameController: AIEventListener created isHost=" + aiHost);
    }
    
    /**
     * 从SharedPreferences读取用户选择的AI难度
     * 根据ai_strategy推断难度等级：NORMAL=EASY, AGGRESSIVE=MEDIUM, DEFENSIVE=HARD
     */
    private String getAIDifficulty() {
        if (appContext == null) {
            return "MEDIUM";
        }
        SharedPreferences prefs = appContext.getSharedPreferences("game_prefs", Context.MODE_PRIVATE);
        String strategy = prefs.getString("ai_strategy", "NORMAL");
        
        // 根据策略推断难度等级
        switch (strategy) {
            case "NORMAL":
                return "EASY";      // 普通模式 → 简单难度（贪心AI）
            case "AGGRESSIVE":
                return "HARD";      // 激进模式 → 困难难度（蒙特卡洛AI）
            case "DEFENSIVE":
                return "ADAPTIVE";  // 保守模式 → 智能难度（自适应AI）
            default:
                return "MEDIUM";
        }
    }
    
    /**
     * 从SharedPreferences读取用户选择的AI策略风格
     */
    private String getSelectedAIStrategy() {
        if (appContext == null) {
            return "NORMAL";
        }
        SharedPreferences prefs = appContext.getSharedPreferences("game_prefs", Context.MODE_PRIVATE);
        return prefs.getString("ai_strategy", "NORMAL");
    }

    public CardTracker getCardTracker() {
        if (aiStrategy instanceof MonteCarloAIDecisionStrategy) {
            return ((MonteCarloAIDecisionStrategy) aiStrategy).getCardTracker();
        }
        return null;
    }
    
    /**
     * 获取当前玩家的手牌
     */
    public List<Card> getCurrentPlayerHand() {
        if (gameEngine == null) {
            return null;
        }
        GameState state = gameEngine.getGameState();
        if (state == null) {
            return null;
        }
        Player currentPlayer = state.getCurrentPlayer();
        if (currentPlayer == null) {
            return null;
        }
        return currentPlayer.getHandCards();
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
        
        // 只有智能模式才进行人类风格分析
        if (aiStrategy instanceof AdaptiveAIDecisionStrategy) {
            analyzeHumanStyleAndAdapt();
        }
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

    private String playerNameFor(String playerId, String fallback, Map<String, String> namesById) {
        String name = namesById != null ? namesById.get(playerId) : null;
        return name != null && !name.trim().isEmpty() ? name.trim() : fallback;
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
            // 出牌失败，自动过牌
            gameEngine.passTurn(currentPlayer.getPlayerId());
            System.out.println("[CardGame][AI] AI " + currentPlayer.getPlayerId() + " passed after failed play");
            
            if (!gameEngine.isGameOver()) {
                new Handler(Looper.getMainLooper()).postDelayed(this::triggerNextAction, 100);
            }
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
        RuleConfig activeRuleConfig = ensureRuleConfigReady();
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
            // 使用 ruleConfig 中的权重
            int w1 = activeRuleConfig.rankWeights.get(c1.getRank());
            int w2 = activeRuleConfig.rankWeights.get(c2.getRank());
            int rankCompare = Integer.compare(w2, w1);  // 降序
            if (rankCompare != 0) return rankCompare;
            int s1 = activeRuleConfig.suitWeights.get(c1.getSuit());
            int s2 = activeRuleConfig.suitWeights.get(c2.getSuit());
            return Integer.compare(s2, s1);  // 降序
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

        String currentPlayerName = currentPlayer.getPlayerName();
        return new GameViewData(currentPlayer.getPlayerId(), currentPlayerName, players,
                new ArrayList<>(selectedCardIds), myHandCards,
                state.getLastPlay() == null ? "" : state.getLastPlay().toString(),
                gameEngine.isGameOver(),
                gameEngine.isGameOver() && winner != null ? winner.getPlayerName() : "",
                playerLastPlayCards,
                gameEngine.getAllPlayedCards());
    }

    private GameViewData emptyViewData() {
        return new GameViewData("", "", new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), "", false, "", new HashMap<>(), null);
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
        boolean isFirstTurn = gameEngine.isFirstTurnOfCurrentRound();
        List<Card> lastPlay = gameEngine.getLastPlayCards();

        boolean hasAnyValid = playValidator.hasAnyValidPlay(player, lastPlay, isFirstRound, isFirstTurn);
        int count = player.getConsecutiveNoPlayCount();
        System.out.println("[CardGame][COUNTDOWN] player=" + player.getPlayerId()
                + ", hasAnyValid=" + hasAnyValid
                + ", consecutiveNoPlayCount=" + count);

        if (!hasAnyValid) {
            if (activeCountdowns.containsKey(player.getPlayerId())) {
                return;
            }
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
                long now = System.currentTimeMillis();
                if (now - lastTriggerTime < TRIGGER_COOLDOWN_MS) {
                    return; // 1秒内重复调用则忽略
                }
                lastTriggerTime = now;
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
    
    /**
     * 更新AI提示文本（供AI策略调用）
     */
    public void updateAiHint(String hintText) {
        if (appContext != null && appContext instanceof com.example.cardgame.ui.GameActivity) {
            com.example.cardgame.ui.GameActivity activity = (com.example.cardgame.ui.GameActivity) appContext;
            activity.runOnUiThread(() -> activity.updateAiHint(hintText));
        }
    }
    
    /**
     * 获取风格分析结果（供UI调用）
     */
    public String getStyleAnalysisSummary() {
        return styleAnalysisSummary;
    }
    
    /**
     * 分析对手风格并生成总结
     */
    public void analyzeOpponentStyles() {
        if (opponentStyleAnalyzer == null) {
            opponentStyleAnalyzer = new com.example.cardgame.llm.OpponentStyleAnalyzer();
        }
        
        GameState gameState = gameEngine.getGameState();
        if (gameState == null) {
            styleAnalysisSummary = "游戏状态无效";
            return;
        }
        
        StringBuilder summary = new StringBuilder();
        
        // 分析人类玩家风格
        if (styleAnalyzer != null && adaptiveAI != null) {
            HumanStyleProfile humanProfile = adaptiveAI.getHumanStyleProfile();
            if (humanProfile != null) {
                summary.append("您的风格：").append(humanProfile.getStyleLabel()).append("\n\n");
            } else {
                summary.append("您的风格：均衡\n\n");
            }
        } else {
            summary.append("您的风格：均衡\n\n");
        }
        
        // 分析AI对手风格（根据对手的出牌历史简单判断）
        for (Player player : gameState.getPlayers()) {
            if (player.getType() == com.example.cardgame.model.PlayerType.AI && !player.getPlayerId().equals(myPlayerId)) {
                String style = "均衡";
                // 根据对手剩余手牌和出牌情况简单判断风格
                int handSize = player.getHandCards().size();
                if (handSize <= 3) {
                    style = "激进";
                } else if (handSize >= 10) {
                    style = "保守";
                }
                
                String playerName = player.getPlayerId();
                // 简化：直接使用playerId作为名字
                summary.append(player.getPlayerId()).append("（").append(playerName).append("）：").append(style).append("\n");
            }
        }
        
        styleAnalysisSummary = summary.toString();
        HermesLog.log("GameController: Style analysis summary generated: " + styleAnalysisSummary);
    }
}
