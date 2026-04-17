package com.example.cardgame.rule;

<<<<<<< HEAD
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
    public PatternRecognizer.PatternInfo recognizePattern(List<PatternRecognizer.SimpleCard> cards) {
        return recognizer.recognizePattern(cards);
    }

    /**
     * 校验出牌合法性（简化版，不需要完整GameState）
     * @param cardsToPlay    要出的牌（若Pass则传null）
     * @param lastPlayCards  上家出的牌
     * @param isFirstTurn    是否本轮第一个出牌
     * @param isFirstRound   是否游戏第一轮
     * @return true=合法
     */
    public boolean validatePlay(List<PatternRecognizer.SimpleCard> cardsToPlay,
                                List<PatternRecognizer.SimpleCard> lastPlayCards,
                                boolean isFirstTurn,
                                boolean isFirstRound) {
        boolean isPass = (cardsToPlay == null || cardsToPlay.isEmpty());
        return validator.validatePlay(isPass, cardsToPlay, lastPlayCards, isFirstTurn, isFirstRound);
    }

    // ---------- 为了对接方便，也提供支持真实Player/GameState的版本（暂时抛出异常或返回false）----------
    // 替换成真实模型后，取消注释并实现
    /*
    public boolean validatePlay(Player player, List<Card> cards, GameState state) {
        // 从state提取参数后调用上面的简化方法
        // 待实现
        return false;
    }
    */
}
=======
public class RuleEngine {
}
>>>>>>> origin/dev-czh-ui-zhy
