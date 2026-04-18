package com.example.cardgame.rule;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;
import com.example.cardgame.rule.PatternRecognizer.PatternInfo;
import com.example.cardgame.rule.PatternRecognizer.PatternType;

import java.util.List;

/**
 * 出牌合法性校验器
 * 支持首轮方块3、压过上家、Pass逻辑
 */
public class PlayValidator {

    private final PatternRecognizer recognizer = new PatternRecognizer();

    /**
     * 校验结果封装
     */
    public static class ValidationResult {
        public final boolean valid;
        public final String reason;

        public ValidationResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }
    }

    /**
     * 校验出牌是否合法
     * @param currentCards  当前玩家要出的牌（如果Pass则传null或空列表）
     * @param lastPlay      上家出的牌（如果本轮第一个出牌则传null或空列表）
     * @param isFirstRound  是否为游戏第一轮（需要强制方块3）
     * @param isFirstTurn   是否为当前轮次的第一个出牌者（决定是否必须压牌）
     * @return 校验结果
     */
    public ValidationResult validatePlay(List<Card> currentCards,
                                         List<Card> lastPlay,
                                         boolean isFirstRound,
                                         boolean isFirstTurn) {
        // 1. Pass 处理
        if (currentCards == null || currentCards.isEmpty()) {
            // 首轮第一个玩家不能 Pass
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
            if (!containsDiamondThree(currentCards)) {
                return new ValidationResult(false, "首轮必须出方块3");
            }
        }

        // 5. 如果没有上家（本轮第一个出牌） -> 合法
        if (lastPlay == null || lastPlay.isEmpty()) {
            return new ValidationResult(true, "合法");
        }

        // 6. 有上家牌：必须压过上家（同牌型且比较值更大）
        PatternInfo lastInfo = recognizer.recognizePattern(lastPlay);
        if (lastInfo.getType() == PatternType.INVALID) {
            return new ValidationResult(false, "上家牌型无效");
        }
        if (currentInfo.getType() != lastInfo.getType()) {
            return new ValidationResult(false, "必须出与上家相同牌型");
        }
        if (currentInfo.getCompareValue() > lastInfo.getCompareValue()) {
            return new ValidationResult(true, "合法");
        } else {
            return new ValidationResult(false, "牌点必须大于上家");
        }
    }

    // 检查是否包含方块3
    private boolean containsDiamondThree(List<Card> cards) {
        for (Card card : cards) {
            if (card.getRank() == Rank.THREE && card.getSuit() == Suit.DIAMONDS) {
                return true;
            }
        }
        return false;
    }
}