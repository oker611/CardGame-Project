package com.example.cardgame.ai;

import com.example.cardgame.rule.RuleConfig;

/**
 * 保守贪心AI策略 - 少出大牌、多过牌，注重防守
 */
public class DefensiveAIDecisionStrategy extends GreedyAIDecisionStrategy {
    
    public DefensiveAIDecisionStrategy(RuleConfig ruleConfig) {
        super(ruleConfig, GreedyAIDecisionStrategy.Style.DEFENSIVE);
    }
}