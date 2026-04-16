package com.example.cardgame.rule;

import com.example.cardgame.rule.PatternRecognizer.PatternInfo;
import com.example.cardgame.rule.PatternRecognizer.PatternType;

import java.util.List;

/**
 * 出牌合法性校验器
 * 临时版本，不依赖外部模型
 */
public class PlayValidator {

    private final PatternRecognizer recognizer = new PatternRecognizer();

    /**
     * 校验出牌是否合法
     * @param isPass         是否Pass
     * @param cardsToPlay    要出的牌（若Pass则传null或空列表）
     * @param lastPlayCards  上家出的牌（没有则传null或空列表）
     * @param isFirstTurn    是否本轮第一个出牌
     * @param isFirstRound   是否游戏第一轮
     * @return true=合法，false=非法
     */
    public boolean validatePlay(boolean isPass,
                                List<PatternRecognizer.SimpleCard> cardsToPlay,
                                List<PatternRecognizer.SimpleCard> lastPlayCards,
                                boolean isFirstTurn,
                                boolean isFirstRound) {
        // 1. Pass处理
        if (isPass) {
            // 首轮第一个玩家不能Pass
            if (isFirstRound && isFirstTurn) {
                return false;
            }
            return true;
        }

        // 2. 非Pass时必须出牌
        if (cardsToPlay == null || cardsToPlay.isEmpty()) {
            return false;
        }

        // 3. 识别当前牌型
        PatternInfo currentInfo = recognizer.recognizePattern(cardsToPlay);
        if (currentInfo.getType() == PatternType.INVALID) {
            return false;
        }

        // 4. 首轮特殊规则：第一轮第一个出牌必须包含方块3
        if (isFirstRound && isFirstTurn) {
            if (!containsDiamondThree(cardsToPlay)) {
                return false;
            }
        }

        // 5. 没有上家牌（即本轮第一个出牌） -> 合法
        if (lastPlayCards == null || lastPlayCards.isEmpty()) {
            return true;
        }

        // 6. 有上家牌：必须压过上家（同牌型且点数更大）
        PatternInfo lastInfo = recognizer.recognizePattern(lastPlayCards);
        if (lastInfo.getType() == PatternType.INVALID) {
            return false;
        }
        if (currentInfo.getType() != lastInfo.getType()) {
            return false;
        }
        return currentInfo.getCompareValue() > lastInfo.getCompareValue();
    }

    // 检查是否包含方块3
    private boolean containsDiamondThree(List<PatternRecognizer.SimpleCard> cards) {
        for (PatternRecognizer.SimpleCard c : cards) {
            if (c.isDiamondThree()) {
                return true;
            }
        }
        return false;
    }
}