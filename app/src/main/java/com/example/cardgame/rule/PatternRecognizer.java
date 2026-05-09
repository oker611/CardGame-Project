package com.example.cardgame.rule;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;
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
        // 复制一份并按单张牌绝对大小排序（从小到大：3...K,A,2）
        List<Card> sorted = new ArrayList<>(cards);
        sorted.sort((c1, c2) -> helper.getCardScore(c1) - helper.getCardScore(c2));

        if (size == 1) {
            return new PatternInfo(PatternType.SINGLE, helper.getCardScore(sorted.get(0)));
        }
        else if (size == 2) {
            if (sorted.get(0).getRank() == sorted.get(1).getRank()) {
                // 对子比较花色：已排序，直接取最后一张的花色（大）
                return new PatternInfo(PatternType.PAIR, helper.getRankWeight(sorted.get(0).getRank()) * 10 + helper.getSuitWeight(sorted.get(1).getSuit()));
            }
        }
        else if (size == 3) {
            if (sorted.get(0).getRank() == sorted.get(2).getRank()) { // 三张
                return new PatternInfo(PatternType.TRIPLE, helper.getRankWeight(sorted.get(0).getRank()));
            }
        }
        else if (size == 4) {
            if (sorted.get(0).getRank() == sorted.get(3).getRank()) { // 四张
                return new PatternInfo(PatternType.QUADRUPLE, helper.getRankWeight(sorted.get(0).getRank()));
            }
        }
        else if (size == 5) {
            boolean isFlush = helper.isFlush(sorted);
            int straightScore = helper.getStraightScore(sorted);

            if (isFlush && straightScore != -1) {
                return new PatternInfo(PatternType.STRAIGHT_FLUSH, straightScore); // 同花顺
            }
            int ironBranchScore = helper.getIronBranchScore(sorted);
            if (ironBranchScore != -1) {
                return new PatternInfo(PatternType.IRON_BRANCH, ironBranchScore); // 铁支
            }
            int fullHouseScore = helper.getFullHouseScore(sorted);
            if (fullHouseScore != -1) {
                return new PatternInfo(PatternType.FULL_HOUSE, fullHouseScore); // 葫芦
            }
            if (isFlush) {
                return new PatternInfo(PatternType.FLUSH, helper.getCardScore(sorted.get(4))); // 同花看最大牌
            }
            if (straightScore != -1) {
                return new PatternInfo(PatternType.STRAIGHT, straightScore); // 顺子
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

        int getCardScore(Card card) {
            return getRankWeight(card.getRank()) * 10 + getSuitWeight(card.getSuit());
        }

        boolean isFlush(List<Card> sorted) {
            Suit s = sorted.get(0).getSuit();
            for (Card c : sorted) {
                if (c.getSuit() != s) return false;
            }
            return true;
        }

        int getStraightScore(List<Card> cards) {
            List<Card> seq = new ArrayList<>(cards);
            // 顺子判断需要按原始数值(A=1, 2=2...)排序
            seq.sort(Comparator.comparingInt(c -> getStraightBase(c.getRank())));

            boolean consecutive = true;
            for (int i = 1; i < 5; i++) {
                if (getStraightBase(seq.get(i).getRank()) != getStraightBase(seq.get(i - 1).getRank()) + 1) {
                    consecutive = false; break;
                }
            }

            if (consecutive) {
                // A,2,3,4,5 会走到这里，最大牌是5
                Card maxCard = seq.get(4);
                return getStraightBase(seq.get(0).getRank()) * 10 + getSuitWeight(maxCard.getSuit());
            }

            // 特殊处理 10,J,Q,K,A
            if (getStraightBase(seq.get(0).getRank()) == 1 &&
                    getStraightBase(seq.get(1).getRank()) == 10 &&
                    getStraightBase(seq.get(2).getRank()) == 11 &&
                    getStraightBase(seq.get(3).getRank()) == 12 &&
                    getStraightBase(seq.get(4).getRank()) == 13) {
                Card maxCard = seq.get(0); // A最大
                return 10 * 10 + getSuitWeight(maxCard.getSuit()); // 权重给到10(最高)
            }
            return -1;
        }

        int getStraightBase(Rank rank) {
            switch (rank) {
                case ACE: return 1; case TWO: return 2; case THREE: return 3;
                case FOUR: return 4; case FIVE: return 5; case SIX: return 6;
                case SEVEN: return 7; case EIGHT: return 8; case NINE: return 9;
                case TEN: return 10; case JACK: return 11; case QUEEN: return 12;
                case KING: return 13; default: return 0;
            }
        }

        int getIronBranchScore(List<Card> sorted) { // 传入的 sorted 是按大小(3~2)排序的
            if (sorted.get(0).getRank() == sorted.get(3).getRank()) return getRankWeight(sorted.get(0).getRank());
            if (sorted.get(1).getRank() == sorted.get(4).getRank()) return getRankWeight(sorted.get(1).getRank());
            return -1;
        }

        int getFullHouseScore(List<Card> sorted) {
            if (sorted.get(0).getRank() == sorted.get(2).getRank() && sorted.get(3).getRank() == sorted.get(4).getRank()) {
                return getRankWeight(sorted.get(0).getRank());
            }
            if (sorted.get(0).getRank() == sorted.get(1).getRank() && sorted.get(2).getRank() == sorted.get(4).getRank()) {
                return getRankWeight(sorted.get(2).getRank());
            }
            return -1;
        }
    }
}