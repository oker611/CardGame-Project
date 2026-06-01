package com.example.cardgame.rule;

import com.example.cardgame.model.Card;

import java.util.List;

public interface RuleEngine {

    /**
     * 识别牌型
     */
    PatternRecognizer.PatternInfo recognizePattern(List<Card> cards);

    /**
     * 校验出牌合法性
     * @param cardsToPlay    要出的牌（若Pass则传null或空列表）
     * @param lastPlayCards  上家出的牌
     * @param isFirstRound   是否游戏第一轮
     * @param isFirstTurn    是否本轮第一个出牌
     * @return 校验结果
     */
    PlayValidator.ValidationResult validatePlay(List<Card> cardsToPlay,
                                                List<Card> lastPlayCards,
                                                boolean isFirstRound,
                                                boolean isFirstTurn);

    /**
     * 清空内部缓存（游戏重置时调用）
     */
    void clearCache();

    /**
     * 获取缓存统计信息（调试用），默认返回空，实现类可覆盖
     */
    default String getCacheStats() {
        return "No cache stats available";
    }
}