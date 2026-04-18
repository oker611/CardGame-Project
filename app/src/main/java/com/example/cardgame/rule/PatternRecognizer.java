package com.example.cardgame.rule;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;
import java.util.List;

/**
 * 牌型识别器（支持单张、对子）
 */
public class PatternRecognizer {

    public enum PatternType {
        SINGLE,   // 单张
        PAIR,     // 对子
        INVALID   // 无效
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

    private final PatternRecognizerHelper helper = new PatternRecognizerHelper();

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
        if (size == 1) {
            // 单张：比较值 = 点数权重 * 10 + 花色权重
            Card card = cards.get(0);
            int rankWeight = helper.getRankWeight(card.getRank());
            int suitWeight = helper.getSuitWeight(card.getSuit());
            int compareValue = rankWeight * 10 + suitWeight;
            return new PatternInfo(PatternType.SINGLE, compareValue);
        }
        else if (size == 2) {
            Rank rank1 = cards.get(0).getRank();
            Rank rank2 = cards.get(1).getRank();
            if (rank1 == rank2) {
                // 对子：比较值 = 点数权重 * 10 + 两张牌中较大的花色权重
                int rankWeight = helper.getRankWeight(rank1);
                int suitWeight1 = helper.getSuitWeight(cards.get(0).getSuit());
                int suitWeight2 = helper.getSuitWeight(cards.get(1).getSuit());
                int maxSuitWeight = Math.max(suitWeight1, suitWeight2);
                int compareValue = rankWeight * 10 + maxSuitWeight;
                return new PatternInfo(PatternType.PAIR, compareValue);
            }
        }
        return new PatternInfo(PatternType.INVALID, -1);
    }

    /**
     * 辅助类：获取权重
     */
    private static class PatternRecognizerHelper {
        // 点数权重（3最小→0，2最大→12）
        int getRankWeight(Rank rank) {
            switch (rank) {
                case THREE: return 0;
                case FOUR:  return 1;
                case FIVE:  return 2;
                case SIX:   return 3;
                case SEVEN: return 4;
                case EIGHT: return 5;
                case NINE:  return 6;
                case TEN:   return 7;
                case JACK:  return 8;
                case QUEEN: return 9;
                case KING:  return 10;
                case ACE:   return 11;
                case TWO:   return 12;
                default:    return -1;
            }
        }

        // 花色权重（方块最小0，黑桃最大3，与游戏规则一致）
        int getSuitWeight(Suit suit) {
            switch (suit) {
                case DIAMONDS: return 0;
                case CLUBS:    return 1;
                case HEARTS:   return 2;
                case SPADES:   return 3;
                default:       return -1;
            }
        }
    }
}