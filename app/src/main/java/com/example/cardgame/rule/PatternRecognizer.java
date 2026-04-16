package com.example.cardgame.rule;

import java.util.List;

/**
 * 牌型识别器
 * 支持点数+花色的完整比较
 */
public class PatternRecognizer {

    // 牌型枚举
    public enum PatternType {
        SINGLE,    // 单张
        PAIR,      // 对子
        INVALID    // 无效
    }

    // 花色比较值（越大越大）
    private static int getSuitValue(String suit) {
        switch (suit) {
            case "SPADE":   return 3;
            case "HEART":   return 2;
            case "CLUB":    return 1;
            case "DIAMOND": return 0;
            default:        return -1;
        }
    }

    // 识别结果，比较值已包含点数+花色
    public static class PatternInfo {
        private final PatternType type;
        private final int compareValue;  // 编码：点数*10 + 花色值（单张）/ 对子中最大花色

        public PatternInfo(PatternType type, int compareValue) {
            this.type = type;
            this.compareValue = compareValue;
        }

        public PatternType getType() { return type; }
        public int getCompareValue() { return compareValue; }
    }

    /**
     * 识别牌型
     * @param cards 要识别的牌
     * @return 识别结果
     */
    public PatternInfo recognizePattern(List<SimpleCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return new PatternInfo(PatternType.INVALID, -1);
        }
        int size = cards.size();
        if (size == 1) {
            // 单张：比较值 = 点数*10 + 花色值
            SimpleCard card = cards.get(0);
            int rankVal = getCardRankValue(card);
            int suitVal = getSuitValue(card.suit);
            int compareVal = rankVal * 10 + suitVal;
            return new PatternInfo(PatternType.SINGLE, compareVal);
        } else if (size == 2) {
            int v1 = getCardRankValue(cards.get(0));
            int v2 = getCardRankValue(cards.get(1));
            if (v1 == v2) {
                // 对子：比较值 = 点数*10 + 两张牌中较大的花色值
                int suit1 = getSuitValue(cards.get(0).suit);
                int suit2 = getSuitValue(cards.get(1).suit);
                int maxSuit = Math.max(suit1, suit2);
                int compareVal = v1 * 10 + maxSuit;
                return new PatternInfo(PatternType.PAIR, compareVal);
            }
        }
        return new PatternInfo(PatternType.INVALID, -1);
    }

    // 获取单张牌的点数（3最小=0，2最大=12）
    private int getCardRankValue(SimpleCard card) {
        String rank = card.rank;
        switch (rank) {
            case "3": return 0;
            case "4": return 1;
            case "5": return 2;
            case "6": return 3;
            case "7": return 4;
            case "8": return 5;
            case "9": return 6;
            case "10": return 7;
            case "J": return 8;
            case "Q": return 9;
            case "K": return 10;
            case "A": return 11;
            case "2": return 12;
            default: return -1;
        }
    }

    // ---------- 临时内部类：SimpleCard ----------
    public static class SimpleCard {
        public String suit;  // "DIAMOND", "CLUB", "HEART", "SPADE"
        public String rank;  // "3","4","5","6","7","8","9","10","J","Q","K","A","2"

        public SimpleCard(String suit, String rank) {
            this.suit = suit;
            this.rank = rank;
        }

        public boolean isDiamondThree() {
            return "DIAMOND".equals(suit) && "3".equals(rank);
        }
    }
}