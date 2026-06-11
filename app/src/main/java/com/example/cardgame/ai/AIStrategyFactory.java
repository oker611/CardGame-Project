package com.example.cardgame.ai;

import com.example.cardgame.model.HumanStyleProfile;
import com.example.cardgame.rule.ConfigurableRuleEngine;
import com.example.cardgame.rule.IPatternRecognizer;
import com.example.cardgame.rule.IPlayValidator;
import com.example.cardgame.rule.PatternRecognizer;
import com.example.cardgame.rule.PlayValidator;
import com.example.cardgame.rule.RuleConfig;
import com.example.cardgame.rule.RuleEngine;
import com.example.cardgame.util.CardTracker;

public final class AIStrategyFactory {

    private AIStrategyFactory() {}

    public static AIDecisionStrategy create(AIDifficulty difficulty, RuleConfig config) {
        switch (difficulty) {
            case GREEDY: {
                IPatternRecognizer recognizer = new PatternRecognizer(config);
                IPlayValidator validator = new PlayValidator(config, recognizer);
                return new GreedyAIDecisionStrategy(config, recognizer, validator);
            }
            case MONTE_CARLO:
                return createMonteCarlo();
            case ADAPTIVE:
                return new AdaptiveAIDecisionStrategy(createMonteCarlo());
            default:
                throw new IllegalArgumentException("Unknown difficulty: " + difficulty);
        }
    }

    public static AIDecisionStrategy createGreedyWithStyle(AIStrategyStyle style, RuleConfig config) {
        IPatternRecognizer recognizer = new PatternRecognizer(config);
        IPlayValidator validator = new PlayValidator(config, recognizer);
        GreedyAIDecisionStrategy.Style gstyle = toGreedyStyle(style);
        return new GreedyAIDecisionStrategy(config, recognizer, validator, gstyle);
    }

    public static AIDecisionStrategy createAdaptiveIfNeeded(
            AIDecisionStrategy existing, RuleConfig config, HumanStyleProfile profile) {
        if (existing instanceof AdaptiveAIDecisionStrategy) {
            AdaptiveAIDecisionStrategy adaptive = (AdaptiveAIDecisionStrategy) existing;
            if (profile != null) {
                adaptive.setHumanStyleProfile(profile);
            }
            return adaptive;
        }
        MonteCarloAIDecisionStrategy mcStrategy = createMonteCarlo();
        AIDecisionStrategy strategy = new AdaptiveAIDecisionStrategy(mcStrategy);
        if (profile != null) {
            ((AdaptiveAIDecisionStrategy) strategy).setHumanStyleProfile(profile);
        }
        return strategy;
    }

    private static MonteCarloAIDecisionStrategy createMonteCarlo() {
        RuleEngine ruleEngine = new ConfigurableRuleEngine(RuleConfig.SOUTHERN);
        IPhaseManager phaseManager = new PhaseManager(ruleEngine);
        AIPlayerProfile profile = new AIPlayerProfile(AIPlayerProfile.LEVEL_STRONG);
        ICandidateGenerator candidateGen = new CandidateGenerator(
                ruleEngine, 4, phaseManager, profile);
        IOpponentHandSampler handSampler = new OpponentHandSampler();
        IMonteCarloSimulator simulator = new MonteCarloSimulator(ruleEngine, 10);
        CardTracker tracker = new CardTracker();
        return new MonteCarloAIDecisionStrategy(
                ruleEngine, candidateGen, handSampler, simulator, phaseManager, tracker, profile);
    }

    private static GreedyAIDecisionStrategy.Style toGreedyStyle(AIStrategyStyle style) {
        switch (style) {
            case AGGRESSIVE: return GreedyAIDecisionStrategy.Style.AGGRESSIVE;
            case DEFENSIVE:  return GreedyAIDecisionStrategy.Style.DEFENSIVE;
            default:         return GreedyAIDecisionStrategy.Style.NORMAL;
        }
    }
}