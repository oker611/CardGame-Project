package com.example.cardgame.ai;

import com.example.cardgame.model.*;
import com.example.cardgame.rule.RuleEngine;
import java.util.*;

public class MonteCarloAIDecisionStrategy implements AIDecisionStrategy {

    private static final int NUM_SAMPLES = 30;
    private static final int TOP_K_CANDIDATES = 15;

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
        this.phaseManager = new PhaseManager(ruleEngine);
    }

    @Override
    public List<Card> decidePlay(Player aiPlayer, GameState gameState) {
        List<Card> hand = aiPlayer.getHandCards();
        Play lastPlay = gameState.getLastPlay();
        boolean isFirstRound = gameState.isOpeningTurn();
        boolean isFirstTurn = (lastPlay == null || lastPlay.isEmpty());

        List<Play> candidates = candidateGenerator.generate(hand, lastPlay, isFirstRound, isFirstTurn);
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1 && candidates.get(0).isEmpty()) {
            return null;
        }

        PhaseManager.GamePhase phase = phaseManager.getCurrentPhase(aiPlayer, gameState);
        List<OpponentHandSampler.World> worlds = opponentHandSampler.sampleWorlds(aiPlayer, gameState, NUM_SAMPLES);

        Map<Play, Double> scores = new HashMap<>();
        for (Play candidate : candidates) {
            double rawScore = monteCarloSimulator.evaluate(candidate, aiPlayer, gameState, worlds);
            double adjusted = phaseManager.adjustScore(rawScore, candidate, phase, aiPlayer, gameState);
            scores.put(candidate, adjusted);
        }

        Play bestPlay = candidates.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Map.Entry<Play, Double> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                bestPlay = entry.getKey();
            }
        }

        return bestPlay.isEmpty() ? null : bestPlay.getCards();
    }
}