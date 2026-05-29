package com.example.cardgame.ai;

import android.os.Handler;
import android.os.Looper;
import com.example.cardgame.model.*;
import com.example.cardgame.rule.RuleEngine;
import java.util.*;
import java.util.concurrent.*;

public class MonteCarloAIDecisionStrategy implements AIDecisionStrategy {

    // 可调参数（后续遗传算法优化）
    private static final int NUM_SAMPLES = 30;           // 蒙特卡洛模拟世界数量
    private static final int TOP_K_CANDIDATES = 15;      // 候选动作截断数
    private static final long DECISION_TIMEOUT_MS = 800; // 决策超时（毫秒）

    private final CandidateGenerator candidateGenerator;
    private final OpponentHandSampler opponentHandSampler;
    private final MonteCarloSimulator monteCarloSimulator;
    private final PhaseManager phaseManager;
    private final RuleEngine ruleEngine;

    public MonteCarloAIDecisionStrategy() {
        this.ruleEngine = new RuleEngine();
        this.candidateGenerator = new CandidateGenerator(ruleEngine, TOP_K_CANDIDATES);
        this.opponentHandSampler = new OpponentHandSampler();
        this.monteCarloSimulator = new MonteCarloSimulator(ruleEngine, NUM_SAMPLES);
        this.phaseManager = new PhaseManager(ruleEngine);   // 传入 ruleEngine
    }

    @Override
    public List<Card> decidePlay(Player aiPlayer, GameState gameState) {
        List<Card> hand = aiPlayer.getHandCards();
        Play lastPlay = gameState.getLastPlay();
        boolean isFirstRound = gameState.isOpeningTurn();
        boolean isFirstTurn = (lastPlay == null || lastPlay.isEmpty());

        // 生成候选动作
        List<Play> candidates = candidateGenerator.generate(hand, lastPlay, isFirstRound, isFirstTurn);
        if (candidates.isEmpty()) {
            return null; // 无可出牌，Pass
        }
        if (candidates.size() == 1 && candidates.get(0).isEmpty()) {
            return null; // 唯一合法动作是 Pass
        }

        PhaseManager.GamePhase phase = phaseManager.getCurrentPhase(aiPlayer, gameState);
        List<OpponentHandSampler.World> worlds = opponentHandSampler.sampleWorlds(aiPlayer, gameState, NUM_SAMPLES);

        // 异步评估候选动作，避免阻塞主线程（但策略接口是同步的，我们内部使用超时）
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Map<Play, Double>> future = executor.submit(() -> {
            Map<Play, Double> scores = new HashMap<>();
            for (Play candidate : candidates) {
                double rawScore = monteCarloSimulator.evaluate(candidate, aiPlayer, gameState, worlds);
                double adjusted = phaseManager.adjustScore(rawScore, candidate, phase, aiPlayer, gameState);
                scores.put(candidate, adjusted);
            }
            return scores;
        });

        Play bestPlay = candidates.get(0);
        try {
            Map<Play, Double> scores = future.get(DECISION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Map.Entry<Play, Double> entry : scores.entrySet()) {
                if (entry.getValue() > bestScore) {
                    bestScore = entry.getValue();
                    bestPlay = entry.getKey();
                }
            }
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            // 超时或异常时使用第一个候选（启发式最佳）
            bestPlay = candidates.get(0);
        } finally {
            executor.shutdownNow();
        }

        return bestPlay.isEmpty() ? null : bestPlay.getCards();
    }
}