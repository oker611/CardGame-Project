package com.example.cardgame.ai;

import com.example.cardgame.ga.Chromosome;
import com.example.cardgame.model.*;
import com.example.cardgame.rule.PatternRecognizer;
import com.example.cardgame.rule.RuleEngine;
import java.util.*;

public class OfflineMonteCarloStrategy implements AIDecisionStrategy {
    private final Chromosome config;
    private final RuleEngine ruleEngine;
    private final CandidateGenerator candidateGenerator;
    private final OpponentHandSampler sampler;
    private final MonteCarloSimulator simulator;
    private final PhaseManager phaseManager;

    public OfflineMonteCarloStrategy(Chromosome config) {
        this.config = config;
        this.ruleEngine = new RuleEngine();
        this.candidateGenerator = new CandidateGenerator(ruleEngine, config.topKCandidates);
        this.sampler = new OpponentHandSampler();
        this.simulator = new MonteCarloSimulator(ruleEngine, config.numSamples);
        this.phaseManager = new PhaseManager(ruleEngine);
    }

    @Override
    public List<Card> decidePlay(Player aiPlayer, GameState gameState) {
        List<Card> hand = aiPlayer.getHandCards();
        Play lastPlay = gameState.getLastPlay();
        boolean isFirstRound = gameState.isOpeningTurn();
        boolean isFirstTurn = (lastPlay == null || lastPlay.isEmpty());

        List<Play> candidates = candidateGenerator.generate(hand, lastPlay, isFirstRound, isFirstTurn);
        if (candidates.isEmpty() || candidates.get(0).isEmpty()) return null;

        PhaseManager.GamePhase phase = phaseManager.getCurrentPhase(aiPlayer, gameState);
        List<OpponentHandSampler.World> worlds = sampler.sampleWorlds(aiPlayer, gameState, config.numSamples);

        double bestScore = Double.NEGATIVE_INFINITY;
        Play bestPlay = candidates.get(0);
        for (Play cand : candidates) {
            double raw = simulator.evaluate(cand, aiPlayer, gameState, worlds);
            double adjusted = adjustWithConfig(raw, cand, phase, aiPlayer);
            if (adjusted > bestScore) {
                bestScore = adjusted;
                bestPlay = cand;
            }
        }
        return bestPlay.isEmpty() ? null : bestPlay.getCards();
    }

    private double adjustWithConfig(double base, Play play, PhaseManager.GamePhase phase, Player ai) {
        double bonus = 0;
        List<Card> hand = ai.getHandCards();
        List<Card> playCards = play.getCards();
        
        switch (phase) {
            case EARLY:
                // 开局保留大牌（2或A）加成
                long big = hand.stream()
                        .filter(c -> c.getRank() == Rank.TWO || c.getRank() == Rank.ACE).count();
                bonus += config.earlyBigCardBonus * big;
                
                // 开局出五张牌惩罚（除非是强力牌型）
                if (playCards.size() == 5 && !isVeryStrongPattern(playCards))
                    bonus += config.fiveCardPenalty;
                
                // 保留顺子倾向：如果手牌中包含顺子，给予奖励
                if (containsStraight(hand)) {
                    bonus += config.straightKeepBonus;
                }
                
                // 如果出牌会破坏顺子结构，给予惩罚
                if (!playCards.isEmpty() && wouldBreakStraight(hand, playCards)) {
                    bonus -= config.straightKeepBonus * 0.5;
                }
                break;
            case MID:
                // 中盘压制奖励
                if (!playCards.isEmpty()) {
                    bonus += config.midSuppressBonus;
                    
                    // 中盘争牌权：只压 ≥ 阈值的牌
                    int avgRank = (int) playCards.stream()
                            .mapToInt(c -> c.getRank().getWeight())
                            .average().orElse(0);
                    // 阈值映射：0→3(3), 1→14(2)
                    int threshold = (int) (3 + config.midControlThreshold * 11);
                    if (avgRank >= threshold) {
                        bonus += config.midSuppressBonus * 0.5;
                    }
                }
                break;
            case LATE:
                // 残局出完奖励
                if (hand.size() == playCards.size()) {
                    bonus += 3.0;
                }
                
                // 残局单2奖励
                long twos = playCards.stream()
                        .filter(c -> c.getRank() == Rank.TWO).count();
                bonus += config.lateTwoBonus * twos;
                
                // 惩罚遗留高单张（A或2）
                long high = hand.stream()
                        .filter(c -> c.getRank() == Rank.ACE || c.getRank() == Rank.TWO).count();
                bonus += config.lateFastBonus * playCards.size() - 0.4 * high;
                break;
        }
        return base + bonus;
    }
    
    private boolean containsStraight(List<Card> cards) {
        if (cards.size() < 5) return false;
        // 简化检测：检查是否有连续5张牌
        int[] ranks = cards.stream()
                .mapToInt(c -> c.getRank().getWeight())
                .distinct()
                .sorted()
                .toArray();
        for (int i = 0; i <= ranks.length - 5; i++) {
            boolean consecutive = true;
            for (int j = 0; j < 4; j++) {
                if (ranks[i + j + 1] != ranks[i + j] + 1) {
                    consecutive = false;
                    break;
                }
            }
            if (consecutive) return true;
        }
        return false;
    }
    
    private boolean wouldBreakStraight(List<Card> hand, List<Card> playCards) {
        List<Card> remaining = new ArrayList<>(hand);
        remaining.removeAll(playCards);
        return containsStraight(hand) && !containsStraight(remaining);
    }

    private boolean isVeryStrongPattern(List<Card> cards) {
        if (cards.size() != 5) return false;
        PatternRecognizer.PatternInfo info = ruleEngine.recognizePattern(cards);
        PatternRecognizer.PatternType type = info.getType();
        return type == PatternRecognizer.PatternType.STRAIGHT_FLUSH ||
               type == PatternRecognizer.PatternType.IRON_BRANCH;
    }
}