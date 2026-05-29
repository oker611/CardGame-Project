package com.example.cardgame.ai;

import com.example.cardgame.model.*;
import com.example.cardgame.rule.PatternRecognizer;
import com.example.cardgame.rule.RuleEngine;
import java.util.List;

public class PhaseManager {

    public enum GamePhase {
        EARLY, MID, LATE
    }

    private static final int EARLY_HAND_SIZE = 13;
    private static final int LATE_HAND_SIZE = 7;

    private final RuleEngine ruleEngine;

    public PhaseManager(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    public GamePhase getCurrentPhase(Player aiPlayer, GameState state) {
        int handSize = aiPlayer.getHandCards().size();
        boolean bigCardsLeft = aiPlayer.getHandCards().stream()
                .anyMatch(c -> c.getRank() == Rank.TWO || c.getRank() == Rank.ACE);
        if (handSize >= EARLY_HAND_SIZE && bigCardsLeft) {
            return GamePhase.EARLY;
        } else if (handSize <= LATE_HAND_SIZE) {
            return GamePhase.LATE;
        } else {
            return GamePhase.MID;
        }
    }

    public double adjustScore(double baseScore, Play candidate, GamePhase phase,
                              Player aiPlayer, GameState state) {
        double bonus = 0.0;
        List<Card> hand = aiPlayer.getHandCards();
        List<Card> playCards = candidate.getCards();

        switch (phase) {
            case EARLY:
                // 保留大牌：如果手牌中有2或A且没出，加分
                long bigCount = hand.stream().filter(c -> c.getRank() == Rank.TWO || c.getRank() == Rank.ACE).count();
                bonus += 0.15 * bigCount;
                // 如果出的牌中包含2或A，扣分（除非是最后一手）
                if (playCards.stream().anyMatch(c -> c.getRank() == Rank.TWO || c.getRank() == Rank.ACE)) {
                    if (hand.size() - playCards.size() > 0) { // 不是最后一手
                        bonus -= 0.3;
                    }
                }
                // 出五张牌且不是强力牌型，额外扣分
                if (playCards.size() == 5 && !isVeryStrongPattern(playCards)) {
                    bonus -= 0.5;
                }
                break;
            case MID:
                // 中盘：压制下家（只要出了非Pass的牌就算压制，简化）
                if (!candidate.isEmpty()) {
                    bonus += 0.4;
                }
                // 如果出的牌正好是中等大小（点数 8~K），加分
                int midCount = (int) playCards.stream().filter(c -> {
                    int w = c.getRank().getWeight();
                    return w >= 5 && w <= 10; // 8~K
                }).count();
                bonus += midCount * 0.1;
                break;
            case LATE:
                // 残局：如果出完能清空手牌，直接巨大奖励
                if (hand.size() == playCards.size()) {
                    bonus += 3.0;
                }
                // 惩罚遗留高单张（A或2）
                long highSingles = hand.stream()
                        .filter(c -> c.getRank() == Rank.ACE || c.getRank() == Rank.TWO)
                        .count();
                bonus -= 0.4 * highSingles;
                // 出牌数越多越好
                bonus += playCards.size() * 0.2;
                break;
        }
        return baseScore + bonus;
    }

    private boolean isVeryStrongPattern(List<Card> cards) {
        if (cards.size() != 5) return false;
        PatternRecognizer.PatternInfo info = ruleEngine.recognizePattern(cards);
        PatternRecognizer.PatternType type = info.getType();
        return type == PatternRecognizer.PatternType.STRAIGHT_FLUSH ||
                type == PatternRecognizer.PatternType.IRON_BRANCH;
    }
}