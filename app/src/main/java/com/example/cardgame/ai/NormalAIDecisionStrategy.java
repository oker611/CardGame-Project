package com.example.cardgame.ai;

import com.example.cardgame.rule.RuleConfig;

/**
 * 普通贪心AI策略 - 平衡的游戏风格
 */
public class NormalAIDecisionStrategy extends GreedyAIDecisionStrategy {
    
    public NormalAIDecisionStrategy(RuleConfig ruleConfig) {
        super(ruleConfig, GreedyAIDecisionStrategy.Style.NORMAL);
    }
}