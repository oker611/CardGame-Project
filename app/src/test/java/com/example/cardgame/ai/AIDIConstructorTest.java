package com.example.cardgame.ai;

import com.example.cardgame.rule.ConfigurableRuleEngine;
import com.example.cardgame.rule.IPatternRecognizer;
import com.example.cardgame.rule.IPlayValidator;
import com.example.cardgame.rule.PatternRecognizer;
import com.example.cardgame.rule.PlayValidator;
import com.example.cardgame.rule.RuleConfig;
import com.example.cardgame.rule.RuleEngine;
import com.example.cardgame.util.CardTracker;

import org.junit.Test;

import static org.junit.Assert.*;

public class AIDIConstructorTest {

    // --- GreedyAIDecisionStrategy DI constructor ---

    @Test
    public void greedy_diConstructor_createsStrategy() {
        RuleConfig config = RuleConfig.SOUTHERN;
        IPatternRecognizer recognizer = new PatternRecognizer(config);
        IPlayValidator validator = new PlayValidator(config, recognizer);
        GreedyAIDecisionStrategy s = new GreedyAIDecisionStrategy(config, recognizer, validator);
        assertNotNull(s);
    }

    @Test
    public void greedy_diConstructor_withStyle() {
        RuleConfig config = RuleConfig.SOUTHERN;
        IPatternRecognizer recognizer = new PatternRecognizer(config);
        IPlayValidator validator = new PlayValidator(config, recognizer);
        GreedyAIDecisionStrategy s = new GreedyAIDecisionStrategy(
                config, recognizer, validator, GreedyAIDecisionStrategy.Style.AGGRESSIVE);
        assertNotNull(s);
    }

    // --- MonteCarloAIDecisionStrategy DI constructor ---

    @Test
    public void monteCarlo_diConstructor_createsStrategy() {
        RuleEngine ruleEngine = new ConfigurableRuleEngine(RuleConfig.SOUTHERN);
        IPhaseManager phaseManager = new PhaseManager(ruleEngine);
        AIPlayerProfile profile = new AIPlayerProfile(AIPlayerProfile.LEVEL_STRONG);
        ICandidateGenerator candidateGen = new CandidateGenerator(ruleEngine, 4, phaseManager, profile);
        IOpponentHandSampler handSampler = new OpponentHandSampler();
        IMonteCarloSimulator simulator = new MonteCarloSimulator(ruleEngine, 10);
        CardTracker tracker = new CardTracker();

        MonteCarloAIDecisionStrategy s = new MonteCarloAIDecisionStrategy(
                ruleEngine, candidateGen, handSampler, simulator, phaseManager, tracker, profile);
        assertNotNull(s);
    }

    // --- MonteCarloAIDecisionStrategy no-arg still works ---

    @Test
    public void monteCarlo_noArgConstructor_createsStrategy() {
        MonteCarloAIDecisionStrategy s = new MonteCarloAIDecisionStrategy();
        assertNotNull(s);
        assertNotNull(s.getProfile());
    }

    // --- AdaptiveAIDecisionStrategy requires MonteCarloStrategy ---

    @Test
    public void adaptive_requiresMonteCarloStrategy() {
        MonteCarloAIDecisionStrategy mc = new MonteCarloAIDecisionStrategy();
        AdaptiveAIDecisionStrategy adaptive = new AdaptiveAIDecisionStrategy(mc);
        assertNotNull(adaptive);
    }
}
