package com.example.cardgame.ai;

import com.example.cardgame.model.HumanStyleProfile;
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

    @Test
    public void createGreedyWithStyle_aggressive_returnsGreedyStrategy() {
        AIDecisionStrategy s = AIStrategyFactory.createGreedyWithStyle(AIStrategyStyle.AGGRESSIVE, RuleConfig.SOUTHERN);
        assertTrue(s instanceof GreedyAIDecisionStrategy);
        assertNotNull(s);
    }

    @Test
    public void createGreedyWithStyle_defensive_returnsGreedyStrategy() {
        AIDecisionStrategy s = AIStrategyFactory.createGreedyWithStyle(AIStrategyStyle.DEFENSIVE, RuleConfig.SOUTHERN);
        assertTrue(s instanceof GreedyAIDecisionStrategy);
        assertNotNull(s);
    }

    @Test
    public void createGreedyWithStyle_normal_returnsGreedyStrategy() {
        AIDecisionStrategy s = AIStrategyFactory.createGreedyWithStyle(AIStrategyStyle.NORMAL, RuleConfig.SOUTHERN);
        assertTrue(s instanceof GreedyAIDecisionStrategy);
        assertNotNull(s);
    }

    @Test
    public void createAdaptiveIfNeeded_reusesExistingInstance() {
        AdaptiveAIDecisionStrategy existing = (AdaptiveAIDecisionStrategy)
                AIStrategyFactory.create(AIDifficulty.ADAPTIVE, RuleConfig.SOUTHERN);
        AIDecisionStrategy result = AIStrategyFactory.createAdaptiveIfNeeded(existing, RuleConfig.SOUTHERN, null);
        assertSame(existing, result);
    }

    @Test
    public void createAdaptiveIfNeeded_createsNewWhenNull() {
        AIDecisionStrategy result = AIStrategyFactory.createAdaptiveIfNeeded(null, RuleConfig.SOUTHERN, null);
        assertNotNull(result);
        assertTrue(result instanceof AdaptiveAIDecisionStrategy);
    }

    @Test
    public void createAdaptiveIfNeeded_createsNewForNonAdaptive() {
        AIDecisionStrategy greedy = AIStrategyFactory.create(AIDifficulty.GREEDY, RuleConfig.SOUTHERN);
        AIDecisionStrategy result = AIStrategyFactory.createAdaptiveIfNeeded(greedy, RuleConfig.SOUTHERN, null);
        assertNotNull(result);
        assertTrue(result instanceof AdaptiveAIDecisionStrategy);
        assertNotSame(greedy, result);
    }

    @Test
    public void createAdaptiveIfNeeded_appliesProfile() {
        HumanStyleProfile profile = new HumanStyleProfile("P1");
        profile.setStyleLabel(HumanStyleProfile.STYLE_AGGRESSIVE);
        AIDecisionStrategy result = AIStrategyFactory.createAdaptiveIfNeeded(null, RuleConfig.SOUTHERN, profile);
        assertNotNull(result);
        assertTrue(result instanceof AdaptiveAIDecisionStrategy);
    }

    @Test
    public void create_unknownDifficulty_throwsException() {
        try {
            AIStrategyFactory.create(null, RuleConfig.SOUTHERN);
            fail("Expected NullPointerException or IllegalArgumentException");
        } catch (IllegalArgumentException | NullPointerException expected) {
            // expected
        }
    }
}
