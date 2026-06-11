package com.example.cardgame.ai;

import com.example.cardgame.rule.RuleConfig;

import org.junit.Test;

import static org.junit.Assert.*;

public class AIStrategyFactoryTest {

    @Test
    public void create_greedy_returnsGreedyStrategy() {
        AIDecisionStrategy s = AIStrategyFactory.create(AIDifficulty.GREEDY, RuleConfig.SOUTHERN);
        assertTrue(s instanceof GreedyAIDecisionStrategy);
    }

    @Test
    public void create_monteCarlo_returnsMonteCarloStrategy() {
        AIDecisionStrategy s = AIStrategyFactory.create(AIDifficulty.MONTE_CARLO, RuleConfig.SOUTHERN);
        assertTrue(s instanceof MonteCarloAIDecisionStrategy);
    }

    @Test
    public void create_adaptive_returnsAdaptiveStrategy() {
        AIDecisionStrategy s = AIStrategyFactory.create(AIDifficulty.ADAPTIVE, RuleConfig.SOUTHERN);
        assertTrue(s instanceof AdaptiveAIDecisionStrategy);
    }

    @Test
    public void create_nullConfig_greedyWorks() {
        AIDecisionStrategy s = AIStrategyFactory.create(AIDifficulty.GREEDY, null);
        assertNotNull(s);
    }
}
