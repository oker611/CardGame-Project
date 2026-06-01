package com.example.cardgame.rule;

import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;
import com.example.cardgame.rule.PatternRecognizer.PatternType;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.EnumSet;

public class RuleConfig {

    public final Map<Rank, Integer> rankWeights;
    public final Map<Suit, Integer> suitWeights;
    public final Rank requiredOpeningRank;
    public final Suit requiredOpeningSuit;
    public final Set<PatternType> allowedPatterns;
    public final Map<PatternType, Integer> fiveCardPriority;
    public final int passResetThreshold;

    private RuleConfig(Builder builder) {
        this.rankWeights = Collections.unmodifiableMap(new EnumMap<>(builder.rankWeights));
        this.suitWeights = Collections.unmodifiableMap(new EnumMap<>(builder.suitWeights));
        this.requiredOpeningRank = builder.requiredOpeningRank;
        this.requiredOpeningSuit = builder.requiredOpeningSuit;
        this.allowedPatterns = Collections.unmodifiableSet(new HashSet<>(builder.allowedPatterns));
        this.fiveCardPriority = Collections.unmodifiableMap(new EnumMap<>(builder.fiveCardPriority));
        this.passResetThreshold = builder.passResetThreshold;
    }

    // ========== 预设配置 ==========

    /** 南方（当前默认规则） */
    public static final RuleConfig SOUTHERN = new Builder()
            .rankWeights(standardRankWeights())
            .suitWeights(standardSuitWeights())
            .requiredOpening(Rank.THREE, Suit.DIAMONDS)
            .allowedPatterns(EnumSet.allOf(PatternType.class))
            .fiveCardPriority(standardFiveCardPriority())
            .passResetThreshold(3)
            .build();

    /** 北方：无首轮限制 + 花色颠倒 + 禁用铁支 */
    public static final RuleConfig NORTHERN = new Builder()
            .rankWeights(standardRankWeights())
            .suitWeights(reversedSuitWeights())
            .requiredOpening(null, null)
            .allowedPatterns(northernAllowedPatterns())
            .fiveCardPriority(northernFiveCardPriority())
            .passResetThreshold(3)
            .build();

    // ========== 工厂方法 ==========

    private static Map<Rank, Integer> standardRankWeights() {
        Map<Rank, Integer> map = new EnumMap<>(Rank.class);
        map.put(Rank.THREE, 0);
        map.put(Rank.FOUR, 1);
        map.put(Rank.FIVE, 2);
        map.put(Rank.SIX, 3);
        map.put(Rank.SEVEN, 4);
        map.put(Rank.EIGHT, 5);
        map.put(Rank.NINE, 6);
        map.put(Rank.TEN, 7);
        map.put(Rank.JACK, 8);
        map.put(Rank.QUEEN, 9);
        map.put(Rank.KING, 10);
        map.put(Rank.ACE, 11);
        map.put(Rank.TWO, 12);
        return map;
    }

    private static Map<Suit, Integer> standardSuitWeights() {
        Map<Suit, Integer> map = new EnumMap<>(Suit.class);
        map.put(Suit.DIAMONDS, 0);
        map.put(Suit.CLUBS, 1);
        map.put(Suit.HEARTS, 2);
        map.put(Suit.SPADES, 3);
        return map;
    }

    private static Map<Suit, Integer> reversedSuitWeights() {
        Map<Suit, Integer> map = new EnumMap<>(Suit.class);
        map.put(Suit.SPADES, 0);
        map.put(Suit.HEARTS, 1);
        map.put(Suit.CLUBS, 2);
        map.put(Suit.DIAMONDS, 3);
        return map;
    }

    private static Map<PatternType, Integer> standardFiveCardPriority() {
        Map<PatternType, Integer> map = new EnumMap<>(PatternType.class);
        map.put(PatternType.STRAIGHT, 1);
        map.put(PatternType.FLUSH, 2);
        map.put(PatternType.FULL_HOUSE, 3);
        map.put(PatternType.IRON_BRANCH, 4);
        map.put(PatternType.STRAIGHT_FLUSH, 5);
        return map;
    }

    private static Map<PatternType, Integer> northernFiveCardPriority() {
        Map<PatternType, Integer> map = new EnumMap<>(PatternType.class);
        map.put(PatternType.STRAIGHT, 1);
        map.put(PatternType.FLUSH, 2);
        map.put(PatternType.FULL_HOUSE, 3);
        map.put(PatternType.STRAIGHT_FLUSH, 4);
        return map;
    }

    private static Set<PatternType> northernAllowedPatterns() {
        Set<PatternType> set = new HashSet<>(Arrays.asList(PatternType.values()));
        set.remove(PatternType.INVALID);
        set.remove(PatternType.IRON_BRANCH);  // 北方禁用铁支
        return set;
    }

    // ========== Builder ==========

    public static class Builder {
        private Map<Rank, Integer> rankWeights = new EnumMap<>(Rank.class);
        private Map<Suit, Integer> suitWeights = new EnumMap<>(Suit.class);
        private Rank requiredOpeningRank = null;
        private Suit requiredOpeningSuit = null;
        private Set<PatternType> allowedPatterns = new HashSet<>();
        private Map<PatternType, Integer> fiveCardPriority = new EnumMap<>(PatternType.class);
        private int passResetThreshold = 3;

        public Builder rankWeights(Map<Rank, Integer> weights) {
            this.rankWeights.putAll(weights);
            return this;
        }

        public Builder suitWeights(Map<Suit, Integer> weights) {
            this.suitWeights.putAll(weights);
            return this;
        }

        public Builder requiredOpening(Rank rank, Suit suit) {
            this.requiredOpeningRank = rank;
            this.requiredOpeningSuit = suit;
            return this;
        }

        public Builder allowedPatterns(Set<PatternType> patterns) {
            this.allowedPatterns.clear();
            this.allowedPatterns.addAll(patterns);
            return this;
        }

        public Builder fiveCardPriority(Map<PatternType, Integer> priority) {
            this.fiveCardPriority.putAll(priority);
            return this;
        }

        public Builder passResetThreshold(int threshold) {
            this.passResetThreshold = threshold;
            return this;
        }

        public RuleConfig build() {
            return new RuleConfig(this);
        }
    }
}
