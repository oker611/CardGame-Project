package com.example.cardgame.ai;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.CardPattern;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.model.Player;
import com.example.cardgame.model.Rank;

import java.util.List;

/**
 * 候选出牌评分器：封装自适应因子调整和启发式奖励计算。
 * 从 MonteCarloAIDecisionStrategy 提取，将评分逻辑与决策流程分离。
 */
public class CandidateScorer {

    private final double aggressivenessFactor;
    private final double defenseFactor;

    public CandidateScorer(double aggressiveness, double defense) {
        this.aggressivenessFactor = aggressiveness;
        this.defenseFactor = defense;
    }

    /**
     * 应用自适应因子调整候选出牌的分数。
     * @return 调整后的分数
     */
    public double applyFactors(double baseScore, Play candidate, Play lastPlay,
                               GameState gameState, Player aiPlayer) {
        double score = baseScore;
        boolean isInitiative = (lastPlay == null || lastPlay.isEmpty());
        if (candidate == null || candidate.isEmpty()) return score;

        List<Card> cards = candidate.getCards();
        List<Card> hand = aiPlayer.getHandCards();

        // 开局鼓励组合牌
        score += applyOpeningBonus(isInitiative, hand.size(), cards, candidate.getPattern());

        // 防守模式调整
        score += applyDefenseAdjustment(cards);

        // 进攻模式调整
        score += applyAggressionAdjustment(cards, hand);

        // 防守过牌惩罚
        if (defenseFactor < 1.0 && !isInitiative) score *= defenseFactor;

        // 进攻压牌奖励
        if (aggressivenessFactor > 1.0 && !isInitiative && lastPlay != null && !lastPlay.isEmpty()) {
            score += applyBeatBonus(cards, lastPlay.getCards());
        }

        // 基础因子乘法
        if (PatternAnalyzer.isAggressivePlay(candidate)) score *= aggressivenessFactor;
        if (PatternAnalyzer.isDefensivePlay(candidate, lastPlay)) score *= defenseFactor;

        // 紧急模式
        if (gameState != null && isInitiative) {
            score += applyEmergencyAdjustment(cards, gameState, aiPlayer);
        }

        // 张数奖励
        if (isInitiative && cards.size() > 1) {
            score += (cards.size() - 1) * 10;
        }

        return score;
    }

    // ========== 评分组件 ==========

    private double applyOpeningBonus(boolean isInitiative, int handSize,
                                     List<Card> cards, CardPattern pattern) {
        if (!isInitiative || handSize <= 8) return 0;
        if (pattern != null && pattern != CardPattern.SINGLE && cards.size() >= 2) {
            return handSize * 2.0;
        }
        return 0;
    }

    private double applyDefenseAdjustment(List<Card> cards) {
        if (defenseFactor <= 1.0) return 0;
        double adjustment = 0;
        int bigCards = 0;
        for (Card c : cards) {
            if (c.getRank() == Rank.TWO || c.getRank() == Rank.ACE) bigCards++;
        }
        if (bigCards > 0) adjustment -= bigCards * 15.0 * (defenseFactor - 1.0);

        boolean allSmall = cards.stream().allMatch(c -> c.getRank().getWeight() < 10);
        if (allSmall) adjustment += 10.0 * (defenseFactor - 1.0) * cards.size();

        return adjustment;
    }

    private double applyAggressionAdjustment(List<Card> cards, List<Card> hand) {
        if (aggressivenessFactor <= 1.0) return 0;
        double adjustment = 0;

        int minHand = hand.stream().mapToInt(c -> c.getRank().getWeight()).min().orElse(Integer.MAX_VALUE);
        int minPlay = cards.stream().mapToInt(c -> c.getRank().getWeight()).min().orElse(Integer.MAX_VALUE);
        if (minPlay == minHand) adjustment += 15.0 * (aggressivenessFactor - 1.0) * cards.size();
        if (cards.size() > 1) adjustment += 8.0 * (aggressivenessFactor - 1.0) * cards.size();

        return adjustment;
    }

    private double applyBeatBonus(List<Card> myCards, List<Card> lastCards) {
        int myVal = myCards.stream().mapToInt(c -> c.getRank().getWeight()).sum();
        int lastVal = lastCards.stream().mapToInt(c -> c.getRank().getWeight()).sum();
        double strength = (double) (myVal - lastVal) / lastVal;
        return 10.0 * (aggressivenessFactor - 1.0) * strength;
    }

    private double applyEmergencyAdjustment(List<Card> cards, GameState state, Player aiPlayer) {
        int minOpp = PatternAnalyzer.getMinOpponentHandSize(state, aiPlayer.getPlayerId());
        if (minOpp > 4) return 0;
        if (cards.size() == 1) return -12.0 * aggressivenessFactor;
        return 15.0 * cards.size() * aggressivenessFactor;
    }
}
