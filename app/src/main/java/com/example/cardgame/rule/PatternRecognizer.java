package com.example.cardgame.rule;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;
import com.example.cardgame.rule.RuleConfig;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;


/**
 * 牌型识别器（支持单张、对子）
 */
public class PatternRecognizer {

    public enum PatternType {
        SINGLE, PAIR, TRIPLE, QUADRUPLE,
        STRAIGHT, FLUSH, FULL_HOUSE, IRON_BRANCH, STRAIGHT_FLUSH,
        INVALID
    }

    /**
     * 识别结果，包含牌型和比较值（用于压牌比较）
     */
    public static class PatternInfo {
        private final PatternType type;
        private final int compareValue;  // 数值越大牌越大（点数权重*10 + 花色权重）

        public PatternInfo(PatternType type, int compareValue) {
            this.type = type;
            this.compareValue = compareValue;
        }

        public PatternType getType() { return type; }
        public int getCompareValue() { return compareValue; }
    }

    private final RuleConfig config;

    public PatternRecognizer(RuleConfig config) {
        this.config = config;
    }

    /**
     * 识别牌型
     * @param cards 要识别的牌列表（不能为null或空）
     * @return 识别结果
     */
    public PatternInfo recognizePattern(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return new PatternInfo(PatternType.INVALID, -1);
        }
        int size = cards.size();
        // 复制一份并按单张牌绝对大小排序（从小到大：3...K,A,2）
        List<Card> sorted = new ArrayList<>(cards);
        sorted.sort((c1, c2) -> getCardScore(c1) - getCardScore(c2));

        if (size == 1) {
            PatternType type = PatternType.SINGLE;
            if (!config.allowedPatterns.contains(type)) {
                return new PatternInfo(PatternType.INVALID, -1);
            }
            return new PatternInfo(type, getCardScore(sorted.get(0)));
        }
        else if (size == 2) {
            if (sorted.get(0).getRank() == sorted.get(1).getRank()) {
                PatternType type = PatternType.PAIR;
                if (!config.allowedPatterns.contains(type)) {
                    return new PatternInfo(PatternType.INVALID, -1);
                }
                int pairValue = getRankWeight(sorted.get(0).getRank()) * 10 + getSuitWeight(sorted.get(1).getSuit());
                return new PatternInfo(type, pairValue);
            }
        }
        else if (size == 3) {
            if (sorted.get(0).getRank() == sorted.get(2).getRank()) {
                PatternType type = PatternType.TRIPLE;
                if (!config.allowedPatterns.contains(type)) {
                    return new PatternInfo(PatternType.INVALID, -1);
                }
                return new PatternInfo(type, getRankWeight(sorted.get(0).getRank()));
            }
        }
        else if (size == 4) {
            if (sorted.get(0).getRank() == sorted.get(3).getRank()) {
                PatternType type = PatternType.QUADRUPLE;
                if (!config.allowedPatterns.contains(type)) {
                    return new PatternInfo(PatternType.INVALID, -1);
                }
                return new PatternInfo(type, getRankWeight(sorted.get(0).getRank()));
            }
        }
        else if (size == 5) {
            boolean flush = isFlush(sorted);
            int straightScore = getStraightScore(sorted);

            if (flush && straightScore != -1) {
                PatternType type = PatternType.STRAIGHT_FLUSH;
                if (!config.allowedPatterns.contains(type)) {
                    return new PatternInfo(PatternType.INVALID, -1);
                }
                return new PatternInfo(type, straightScore);
            }
            int ironBranchScore = getIronBranchScore(sorted);
            if (ironBranchScore != -1) {
                PatternType type = PatternType.IRON_BRANCH;
                if (!config.allowedPatterns.contains(type)) {
                    return new PatternInfo(PatternType.INVALID, -1);
                }
                return new PatternInfo(type, ironBranchScore);
            }
            int fullHouseScore = getFullHouseScore(sorted);
            if (fullHouseScore != -1) {
                PatternType type = PatternType.FULL_HOUSE;
                if (!config.allowedPatterns.contains(type)) {
                    return new PatternInfo(PatternType.INVALID, -1);
                }
                return new PatternInfo(type, fullHouseScore);
            }
            if (flush) {
                PatternType type = PatternType.FLUSH;
                if (!config.allowedPatterns.contains(type)) {
                    return new PatternInfo(PatternType.INVALID, -1);
                }
                return new PatternInfo(type, getCardScore(sorted.get(4)));
            }
            if (straightScore != -1) {
                PatternType type = PatternType.STRAIGHT;
                if (!config.allowedPatterns.contains(type)) {
                    return new PatternInfo(PatternType.INVALID, -1);
                }
                return new PatternInfo(type, straightScore);
            }
        }
        return new PatternInfo(PatternType.INVALID, -1);
    }

    private int getRankWeight(Rank rank) {
        Integer w = config.rankWeights.get(rank);
        return w != null ? w : -1;
    }

    private int getSuitWeight(Suit suit) {
        Integer w = config.suitWeights.get(suit);
        return w != null ? w : -1;
    }

    private int getCardScore(Card card) {
        return getRankWeight(card.getRank()) * 10 + getSuitWeight(card.getSuit());
    }

    private boolean isFlush(List<Card> sorted) {
        Suit s = sorted.get(0).getSuit();
        for (Card c : sorted) {
            if (c.getSuit() != s) return false;
        }
        return true;
    }

    private int getStraightScore(List<Card> cards) {
        List<Card> seq = new ArrayList<>(cards);
        seq.sort(Comparator.comparingInt(c -> getStraightBase(c.getRank())));

        boolean consecutive = true;
        for (int i = 1; i < 5; i++) {
            if (getStraightBase(seq.get(i).getRank()) != getStraightBase(seq.get(i - 1).getRank()) + 1) {
                consecutive = false;
                break;
            }
        }

        if (consecutive) {
            Card maxCard = seq.get(4);
            return getStraightBase(seq.get(0).getRank()) * 10 + getSuitWeight(maxCard.getSuit());
        }

        // 特殊处理 10,J,Q,K,A
        if (getStraightBase(seq.get(0).getRank()) == 1
                && getStraightBase(seq.get(1).getRank()) == 10
                && getStraightBase(seq.get(2).getRank()) == 11
                && getStraightBase(seq.get(3).getRank()) == 12
                && getStraightBase(seq.get(4).getRank()) == 13) {
            Card maxCard = seq.get(0);
            return 10 * 10 + getSuitWeight(maxCard.getSuit());
        }
        return -1;
    }

    private int getStraightBase(Rank rank) {
        switch (rank) {
            case ACE:   return 1;
            case TWO:   return 2;
            case THREE: return 3;
            case FOUR:  return 4;
            case FIVE:  return 5;
            case SIX:   return 6;
            case SEVEN: return 7;
            case EIGHT: return 8;
            case NINE:  return 9;
            case TEN:   return 10;
            case JACK:  return 11;
            case QUEEN: return 12;
            case KING:  return 13;
            default:    return 0;
        }
    }

    private int getIronBranchScore(List<Card> sorted) {
        if (sorted.get(0).getRank() == sorted.get(3).getRank())
            return getRankWeight(sorted.get(0).getRank());
        if (sorted.get(1).getRank() == sorted.get(4).getRank())
            return getRankWeight(sorted.get(1).getRank());
        return -1;
    }

    private int getFullHouseScore(List<Card> sorted) {
        if (sorted.get(0).getRank() == sorted.get(2).getRank()
                && sorted.get(3).getRank() == sorted.get(4).getRank()) {
            return getRankWeight(sorted.get(0).getRank());
        }
        if (sorted.get(0).getRank() == sorted.get(1).getRank()
                && sorted.get(2).getRank() == sorted.get(4).getRank()) {
            return getRankWeight(sorted.get(2).getRank());
        }
        return -1;
    }
}