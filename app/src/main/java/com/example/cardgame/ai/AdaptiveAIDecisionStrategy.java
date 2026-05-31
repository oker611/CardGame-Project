package com.example.cardgame.ai;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.HumanStyleProfile;
import com.example.cardgame.model.Player;
import java.util.List;

public class AdaptiveAIDecisionStrategy implements AIDecisionStrategy {
    private final MonteCarloAIDecisionStrategy monteCarloStrategy;
    private HumanStyleProfile humanStyleProfile;
    private String humanPlayerId;

    public AdaptiveAIDecisionStrategy() {
        this.monteCarloStrategy = new MonteCarloAIDecisionStrategy();
    }

    public AdaptiveAIDecisionStrategy(MonteCarloAIDecisionStrategy strategy) {
        this.monteCarloStrategy = strategy;
    }

    public void setHumanStyleProfile(HumanStyleProfile profile) {
        this.humanStyleProfile = profile;
        applyStyleToFactors();
    }

    public void setHumanPlayerId(String playerId) {
        this.humanPlayerId = playerId;
    }

    public HumanStyleProfile getHumanStyleProfile() {
        return humanStyleProfile;
    }

    public MonteCarloAIDecisionStrategy getMonteCarloStrategy() {
        return monteCarloStrategy;
    }

    private void applyStyleToFactors() {
        if (humanStyleProfile == null) {
            monteCarloStrategy.setAggressivenessFactor(1.0);
            monteCarloStrategy.setDefenseFactor(1.0);
            return;
        }

        double aggressivenessFactor = 1.0;
        double defenseFactor = 1.0;

        if (humanStyleProfile.isAggressive()) {
            aggressivenessFactor = 0.6;
            defenseFactor = 1.5;
        } else if (humanStyleProfile.isConservative()) {
            aggressivenessFactor = 1.5;
            defenseFactor = 0.6;
        } else if (humanStyleProfile.isBluffer()) {
            aggressivenessFactor = 1.2;
            defenseFactor = 1.2;
        } else {
            aggressivenessFactor = 1.0;
            defenseFactor = 1.0;
        }

        monteCarloStrategy.setAggressivenessFactor(aggressivenessFactor);
        monteCarloStrategy.setDefenseFactor(defenseFactor);

        System.out.println("[AdaptiveAI] Applied style: " + humanStyleProfile.getStyleLabel() +
                ", aggressivenessFactor=" + aggressivenessFactor +
                ", defenseFactor=" + defenseFactor);
    }

    @Override
    public List<Card> decidePlay(Player aiPlayer, GameState gameState) {
        return monteCarloStrategy.decidePlay(aiPlayer, gameState);
    }

    @Override
    public void recordPlayFailure() {
        monteCarloStrategy.recordPlayFailure();
    }

    @Override
    public void resetFailCount() {
        monteCarloStrategy.resetFailCount();
    }

    public String getCurrentTacticDescription() {
        if (humanStyleProfile == null) {
            return "均衡策略：随机应变";
        }
        return humanStyleProfile.getCounterTactic();
    }
}
