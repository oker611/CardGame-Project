package com.example.cardgame.ai;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.CardPattern;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.model.Player;
import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;
import com.example.cardgame.rule.PatternRecognizer;

import java.util.List;
import java.util.Map;

/**
 * 牌型分析工具：纯函数，无副作用，可从任意上下文调用。
 * 从 MonteCarloAIDecisionStrategy 提取，便于独立测试和复用。
 */
public final class PatternAnalyzer {

    private PatternAnalyzer() {}

    // ========== 牌型分数 ==========

    public static int getPatternScore(CardPattern pattern) {
        if (pattern == null) return 0;
        switch (pattern) {
            case STRAIGHT_FLUSH: return 9;
            case IRON_BRANCH: return 8;
            case FULL_HOUSE: return 7;
            case FLUSH: return 6;
            case STRAIGHT: return 5;
            case QUADRUPLE: return 4;
            case TRIPLE: return 3;
            case PAIR: return 2;
            case SINGLE: return 1;
            default: return 0;
        }
    }

    public static int getPatternScoreFromType(PatternRecognizer.PatternType patternType) {
        if (patternType == null) return 0;
        switch (patternType) {
            case STRAIGHT_FLUSH: return 9;
            case IRON_BRANCH: return 8;
            case FULL_HOUSE: return 7;
            case FLUSH: return 6;
            case STRAIGHT: return 5;
            case QUADRUPLE: return 4;
            case TRIPLE: return 3;
            case PAIR: return 2;
            case SINGLE: return 1;
            default: return 0;
        }
    }

    // ========== 牌型类型判断 ==========

    /**
     * 获取五张牌型类型：5-同花顺 4-铁支 3-葫芦 2-同花 1-顺子 0-其他
     */
    public static int getPatternType(List<Card> cards) {
        if (isStraightFlush(cards)) return 5;
        if (isFourOfAKindSimple(cards)) return 4;
        if (isFullHouseSimple(cards)) return 3;
        if (isFlushSimple(cards)) return 2;
        if (isStraightSimple(cards)) return 1;
        return 0;
    }

    /** 获取牌型关键比较值 */
    public static int getPatternKeyValue(List<Card> cards) {
        int type = getPatternType(cards);
        switch (type) {
            case 5: case 1: return getStraightMaxKey(cards);
            case 4: return getFourRankKey(cards);
            case 3: return getThreeRankKey(cards);
            case 2: return getFlushMaxKey(cards);
            default: return 0;
        }
    }

    // ========== 牌型检测 ==========

    public static boolean isStraightSimple(List<Card> cards) {
        if (cards.size() != 5) return false;
        List<Integer> weights = cards.stream()
                .map(c -> c.getRank().getWeight())
                .sorted()
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (weights.size() != 5) return false;
        for (int i = 1; i < weights.size(); i++) {
            if (weights.get(i) != weights.get(i - 1) + 1) {
                if (!(weights.get(0) == 2 && weights.get(1) == 3
                        && weights.get(2) == 4 && weights.get(3) == 5 && weights.get(4) == 14)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isFlushSimple(List<Card> cards) {
        if (cards.size() != 5) return false;
        Suit suit = cards.get(0).getSuit();
        return cards.stream().allMatch(c -> c.getSuit() == suit);
    }

    public static boolean isStraightFlush(List<Card> cards) {
        return isStraightSimple(cards) && isFlushSimple(cards);
    }

    public static boolean isFourOfAKindSimple(List<Card> cards) {
        if (cards.size() != 5) return false;
        Map<Rank, Long> freq = cards.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Card::getRank, java.util.stream.Collectors.counting()));
        return freq.containsValue(4L);
    }

    public static boolean isFullHouseSimple(List<Card> cards) {
        if (cards.size() != 5) return false;
        Map<Rank, Long> freq = cards.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Card::getRank, java.util.stream.Collectors.counting()));
        return freq.containsValue(3L) && freq.containsValue(2L);
    }

    // ========== Key 值提取 ==========

    public static int getStraightMaxKey(List<Card> cards) {
        List<Integer> weights = cards.stream()
                .map(c -> c.getRank().getWeight())
                .sorted()
                .collect(java.util.stream.Collectors.toList());
        if (weights.get(0) == 2 && weights.get(1) == 3
                && weights.get(2) == 4 && weights.get(3) == 5 && weights.get(4) == 14) {
            return 5;
        }
        return weights.get(4);
    }

    public static int getFourRankKey(List<Card> cards) {
        Map<Rank, Long> freq = cards.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Card::getRank, java.util.stream.Collectors.counting()));
        for (Map.Entry<Rank, Long> e : freq.entrySet()) {
            if (e.getValue() == 4) return e.getKey().getWeight();
        }
        return 0;
    }

    public static int getThreeRankKey(List<Card> cards) {
        Map<Rank, Long> freq = cards.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Card::getRank, java.util.stream.Collectors.counting()));
        for (Map.Entry<Rank, Long> e : freq.entrySet()) {
            if (e.getValue() == 3) return e.getKey().getWeight();
        }
        return 0;
    }

    public static int getFlushMaxKey(List<Card> cards) {
        return cards.stream()
                .mapToInt(c -> c.getRank().getWeight())
                .max().orElse(0);
    }

    // ========== 残局与动态策略 ==========

    /** 根据手牌数动态调整蒙特卡洛模拟次数 */
    public static int calculateDynamicSamples(int handSize) {
        if (handSize > 8) return 100;
        if (handSize >= 4) return 200;
        return 400;
    }

    /** 判断是否进入残局（对手人均手牌 ≤ 5） */
    public static boolean isEndGamePhase(GameState gameState, String aiPlayerId) {
        if (gameState == null) return false;
        int total = 0, count = 0;
        for (Player p : gameState.getPlayers()) {
            if (!p.getPlayerId().equals(aiPlayerId)) {
                total += p.getHandCards().size();
                count++;
            }
        }
        return count > 0 && (total / count) <= 5;
    }

    /** 获取对手最少手牌数 */
    public static int getMinOpponentHandSize(GameState gameState, String aiPlayerId) {
        return gameState.getPlayers().stream()
                .filter(p -> !p.getPlayerId().equals(aiPlayerId))
                .mapToInt(p -> p.getHandCards().size())
                .min().orElse(Integer.MAX_VALUE);
    }

    // ========== 策略分类 ==========

    /** 是否为进攻型出牌（平均牌值 ≥ 10） */
    public static boolean isAggressivePlay(Play candidate) {
        if (candidate == null || candidate.isEmpty()) return false;
        List<Card> cards = candidate.getCards();
        if (cards.isEmpty()) return false;
        return cards.stream().mapToDouble(c -> c.getRank().getWeight()).average().orElse(0) >= 10;
    }

    /** 是否为防守型出牌（平均牌值 < 8 或空过） */
    public static boolean isDefensivePlay(Play candidate, Play lastPlay) {
        if (candidate == null || candidate.isEmpty()) return true;
        if (lastPlay == null || lastPlay.isEmpty()) return false;
        List<Card> cards = candidate.getCards();
        if (cards.isEmpty()) return true;
        return cards.stream().mapToDouble(c -> c.getRank().getWeight()).average().orElse(0) < 8;
    }
}
