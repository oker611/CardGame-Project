package com.example.cardgame.ai;

import com.example.cardgame.rule.RuleConfig;

/**
 * 激进贪心AI策略 - 多出大牌、少过牌，主动进攻
 */
public class AggressiveAIDecisionStrategy extends GreedyAIDecisionStrategy {
    
    public AggressiveAIDecisionStrategy(RuleConfig ruleConfig) {
        super(ruleConfig, GreedyAIDecisionStrategy.Style.AGGRESSIVE);
    }
}