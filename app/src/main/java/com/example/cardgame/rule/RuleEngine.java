package com.example.cardgame.rule;

import com.example.cardgame.model.Card;
import java.util.List;

/**
 * 规则引擎 - 对外统一接口
 * 临时版本，提供 validatePlay 和 recognizePattern
 */
public class RuleEngine {

    private final PatternRecognizer recognizer = new PatternRecognizer();
    private final PlayValidator validator = new PlayValidator();

    /**
     * 识别牌型
     */
    public PatternRecognizer.PatternInfo recognizePattern(List<Card> cards) {
        return recognizer.recognizePattern(cards);
    }

    /**
     * 校验出牌合法性
     * @param cardsToPlay    要出的牌（若Pass则传null或空列表）
     * @param lastPlayCards  上家出的牌
     * @param isFirstRound   是否游戏第一轮
     * @param isFirstTurn    是否本轮第一个出牌
     * @return 校验结果
     */
    public PlayValidator.ValidationResult validatePlay(List<Card> cardsToPlay,
                                                       List<Card> lastPlayCards,
                                                       boolean isFirstRound,
                                                       boolean isFirstTurn) {
        return validator.validatePlay(cardsToPlay, lastPlayCards, isFirstRound, isFirstTurn);
    }
}