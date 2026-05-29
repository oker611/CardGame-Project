package com.example.cardgame.rule;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.Player;
import com.example.cardgame.rule.PatternRecognizer.PatternInfo;
import com.example.cardgame.rule.PatternRecognizer.PatternType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 出牌合法性校验器
 * 支持首轮方块3、压过上家、Pass逻辑
 */
public class PlayValidator {

    private final PatternRecognizer recognizer;
    private final RuleConfig config;

    public PlayValidator(RuleConfig config) {
        this.config = config;
        this.recognizer = new PatternRecognizer(config);
    }

    public static class ValidationResult {
        public final boolean valid;
        public final String reason;

        public ValidationResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }
    }

    public ValidationResult validatePlay(List<Card> currentCards,
                                         List<Card> lastPlay,
                                         boolean isFirstRound,
                                         boolean isFirstTurn) {
        // 1. Pass 处理
        if (currentCards == null || currentCards.isEmpty()) {
            if (isFirstRound && isFirstTurn) {
                return new ValidationResult(false, "首轮第一个玩家不能 Pass");
            }
            return new ValidationResult(true, "Pass");
        }

        // 2. 非 Pass 时必须出牌
        if (currentCards.isEmpty()) {
            return new ValidationResult(false, "出牌列表为空");
        }

        // 3. 识别当前牌型
        PatternInfo currentInfo = recognizer.recognizePattern(currentCards);
        if (currentInfo.getType() == PatternType.INVALID) {
            return new ValidationResult(false, "不支持的牌型（只能出单张或对子）");
        }

        // 4. 首轮特殊规则：第一轮第一个出牌必须包含方块3
        if (isFirstRound && isFirstTurn) {
            if (!containsRequiredOpeningCard(currentCards)) {
                String required = config.requiredOpeningRank != null
                        ? config.requiredOpeningSuit.getSymbol() + config.requiredOpeningRank.getDisplayName()
                        : "";
                return new ValidationResult(false, "首轮必须出" + required);
            }
        }

        // 5. 如果没有上家（本轮第一个出牌） -> 合法
        if (lastPlay == null || lastPlay.isEmpty()) {
            return new ValidationResult(true, "合法");
        }

        // 6. 有上家牌：必须压过上家
        PatternInfo lastInfo = recognizer.recognizePattern(lastPlay);
        if (lastInfo.getType() == PatternType.INVALID) {
            return new ValidationResult(false, "上家牌型无效");
        }
        if (currentInfo.getType() != lastInfo.getType()) {
            if (isFiveCardPattern(currentInfo.getType()) && isFiveCardPattern(lastInfo.getType())) {
                if (getFiveCardPriority(currentInfo.getType()) > getFiveCardPriority(lastInfo.getType())) {
                    return new ValidationResult(true, "高级牌型压制");
                } else {
                    return new ValidationResult(false, "牌型优先级不足以压制上家");
                }
            }
            return new ValidationResult(false, "必须出与上家相同的牌型");
        }

        // 7. 同牌型比较大小
        if (currentInfo.getCompareValue() > lastInfo.getCompareValue()) {
            return new ValidationResult(true, "合法");
        } else {
            return new ValidationResult(false, "牌面必须大于上家");
        }
    }

    // ========== 新增：判断一个出牌组合自身是否为合法牌型 ==========
    public boolean isValidPattern(List<Card> cards) {
        if (cards == null || cards.isEmpty()) return false;
        PatternInfo info = recognizer.recognizePattern(cards);
        return info.getType() != PatternType.INVALID;
    }

    // ========== 判断玩家是否有任何合法牌可出（用于倒计时） ==========
    public boolean hasAnyValidPlay(Player player, List<Card> lastPlay,
                                   boolean isFirstRound, boolean isFirstTurn) {
        List<Card> hand = player.getHandCards();
        if (hand.isEmpty()) return false;

        // 1. 尝试单张
        for (Card card : hand) {
            List<Card> single = Collections.singletonList(card);
            ValidationResult result = validatePlay(single, lastPlay, isFirstRound, isFirstTurn);
            if (result.valid) return true;
        }

        // 2. 尝试对子
        if (hand.size() >= 2) {
            Map<String, List<Card>> rankMap = hand.stream()
                    .collect(Collectors.groupingBy(c -> c.getRank().name()));
            for (List<Card> sameRank : rankMap.values()) {
                if (sameRank.size() >= 2) {
                    List<Card> pair = sameRank.subList(0, 2);
                    ValidationResult result = validatePlay(pair, lastPlay, isFirstRound, isFirstTurn);
                    if (result.valid) return true;
                }
            }
        }

        // 3. 五张牌型
        if (hand.size() >= 5) {
            List<List<Card>> combinations = generateCombinations(hand, 5);
            for (List<Card> fiveCards : combinations) {
                PatternInfo curInfo = recognizer.recognizePattern(fiveCards);
                if (curInfo.getType() != PatternType.INVALID) {
                    ValidationResult result = validatePlay(fiveCards, lastPlay, isFirstRound, isFirstTurn);
                    if (result.valid) return true;
                }
            }
        }

        return false;
    }

    private List<List<Card>> generateCombinations(List<Card> cards, int k) {
        List<List<Card>> result = new ArrayList<>();
        combine(cards, 0, k, new ArrayList<>(), result);
        return result;
    }

    private void combine(List<Card> cards, int start, int k,
                         List<Card> current, List<List<Card>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < cards.size(); i++) {
            current.add(cards.get(i));
            combine(cards, i + 1, k, current, result);
            current.remove(current.size() - 1);
        }
    }

    private boolean containsRequiredOpeningCard(List<Card> cards) {
            if (config.requiredOpeningRank == null) {
                return true;
            }
            for (Card card : cards) {
                if (card.getRank() == config.requiredOpeningRank
                        && card.getSuit() == config.requiredOpeningSuit) {
                    return true;
                }
            }
            return false;
    }

    private boolean isFiveCardPattern(PatternType type) {
        return config.fiveCardPriority.containsKey(type);
    }

    private int getFiveCardPriority(PatternType type) {
        Integer p = config.fiveCardPriority.get(type);
        return p != null ? p : 0;
    }
}