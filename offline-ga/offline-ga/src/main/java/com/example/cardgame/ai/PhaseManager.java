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
                // 开局：保留大牌（2或A）加成，提高奖励值
                long bigCount = hand.stream().filter(c -> c.getRank() == Rank.TWO || c.getRank() == Rank.ACE).count();
                bonus += 0.6 * bigCount;                // 原 0.15 → 0.6
                // 如果出的牌中包含2或A，且不是最后一手，加重扣分
                if (playCards.stream().anyMatch(c -> c.getRank() == Rank.TWO || c.getRank() == Rank.ACE)) {
                    if (hand.size() - playCards.size() > 0) {
                        bonus -= 0.5;                   // 原 0.3 → 0.5
                    }
                }
                // 开局出五张牌：强力牌型（同花顺/铁支）奖励，普通牌型轻度惩罚
                if (playCards.size() == 5) {
                    if (isVeryStrongPattern(playCards)) {
                        bonus += 1.5;                   // 同花顺/铁支 → 重大奖励
                    } else {
                        bonus -= 1.0;                   // 普通五张牌 → 轻度惩罚
                    }
                }
                break;
            case MID:
                // 中盘：压制下家奖励（保持原值）
                if (!candidate.isEmpty()) {
                    bonus += 0.05113800120321388;       // 遗传优化值
                }
                // 中盘出五张牌（任何合法牌型）给予奖励，加速脱手
                if (playCards.size() == 5) {
                    bonus += 1.2;
                }
                // 额外：出中等牌（点数 8~K）加分（保持不变）
                int midCount = (int) playCards.stream().filter(c -> {
                    int w = c.getRank().getWeight();
                    return w >= 5 && w <= 10; // 8~K
                }).count();
                bonus += midCount * 0.1;
                break;
            case LATE:
                // 残局：直接出完奖励
                if (hand.size() == playCards.size()) {
                    bonus += 3.0;
                }
                // 残局出五张牌给予更高奖励
                if (playCards.size() == 5) {
                    bonus += 2.0;
                }
                // 惩罚遗留高单张（A或2）
                long highSingles = hand.stream()
                        .filter(c -> c.getRank() == Rank.ACE || c.getRank() == Rank.TWO)
                        .count();
                bonus -= 0.4 * highSingles;
                // 出牌数越多越好（保留遗传值）
                bonus += playCards.size() * 0.22154936056892915;
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