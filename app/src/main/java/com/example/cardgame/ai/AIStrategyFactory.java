package com.example.cardgame.ai;

import com.example.cardgame.rule.RuleConfig;

import java.util.EnumMap;
import java.util.Map;

/**
 * AI 策略工厂 — 替代 GameController 中的嵌套 switch。
 * 遵循开闭原则：新增策略只需扩展枚举和 Map，无需修改调用代码。
 */
public final class AIStrategyFactory {

    private AIStrategyFactory() {}

    public static AIDecisionStrategy create(AIDifficulty difficulty, RuleConfig config) {
        switch (difficulty) {
            case GREEDY:  return new GreedyAIDecisionStrategy(config);
            case MONTE_CARLO: return new MonteCarloAIDecisionStrategy();
            case ADAPTIVE: return new AdaptiveAIDecisionStrategy();
            default: throw new IllegalArgumentException("Unknown difficulty: " + difficulty);
        }
    }

    /**
     * 按难度和规则创建，同时预加载自适应 AI 的人类风格档案。
     */
    public static AIDecisionStrategy createAdaptiveIfNeeded(
            AIDifficulty difficulty, RuleConfig config,
            com.example.cardgame.model.HumanStyleProfile profile) {
        AIDecisionStrategy strategy = create(difficulty, config);
        if (strategy instanceof AdaptiveAIDecisionStrategy && profile != null) {
            ((AdaptiveAIDecisionStrategy) strategy).setHumanStyleProfile(profile);
        }
        return strategy;
    }
}
