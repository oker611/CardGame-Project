package com.example.cardgame.rule;

import com.example.cardgame.model.Card;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则引擎 - 对外统一接口
 * 临时版本，提供 validatePlay 和 recognizePattern
 */
public class RuleEngine {

    private final PatternRecognizer recognizer = new PatternRecognizer();
    private final PlayValidator validator = new PlayValidator();

    // ========== 牌型比较结果缓存 ==========
    // 使用 ConcurrentHashMap 保证多线程安全（蒙特卡洛模拟可能并发）
    private static final Map<String, Boolean> validationCache = new ConcurrentHashMap<>();
    private static final Map<String, PatternRecognizer.PatternInfo> patternCache = new ConcurrentHashMap<>();

    /**
     * 识别牌型
     */
    public PatternRecognizer.PatternInfo recognizePattern(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return recognizer.recognizePattern(cards);
        }

        // 生成缓存键
        String cacheKey = generatePatternCacheKey(cards);
        PatternRecognizer.PatternInfo cached = patternCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 未命中缓存，执行识别
        PatternRecognizer.PatternInfo result = recognizer.recognizePattern(cards);
        patternCache.put(cacheKey, result);
        return result;
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
        // 生成缓存键
        String cacheKey = generateValidationCacheKey(cardsToPlay, lastPlayCards, isFirstRound, isFirstTurn);

        // 查询缓存
        Boolean cachedResult = validationCache.get(cacheKey);
        if (cachedResult != null) {
            return new PlayValidator.ValidationResult(cachedResult, cachedResult ? "CACHE_HIT" : "CACHE_HIT_INVALID");
        }

        // 未命中缓存，执行验证
        PlayValidator.ValidationResult result = validator.validatePlay(cardsToPlay, lastPlayCards, isFirstRound, isFirstTurn);

        // 存入缓存
        validationCache.put(cacheKey, result.valid);

        return result;
    }

    /**
     * 生成验证缓存键
     */
    private String generateValidationCacheKey(List<Card> cards, List<Card> lastCards,
                                               boolean isFirstRound, boolean isFirstTurn) {
        StringBuilder sb = new StringBuilder();

        // 当前出牌
        if (cards != null) {
            for (Card c : cards) {
                sb.append(c.getCardId()).append(",");
            }
        }
        sb.append("|");

        // 上家出牌
        if (lastCards != null) {
            for (Card c : lastCards) {
                sb.append(c.getCardId()).append(",");
            }
        }
        sb.append("|");

        // 首轮首出标记
        sb.append(isFirstRound ? "1" : "0");
        sb.append(isFirstTurn ? "1" : "0");

        return sb.toString();
    }

    /**
     * 生成牌型识别缓存键
     */
    private String generatePatternCacheKey(List<Card> cards) {
        StringBuilder sb = new StringBuilder();
        if (cards != null) {
            for (Card c : cards) {
                sb.append(c.getCardId()).append(",");
            }
        }
        return sb.toString();
    }

    /**
     * 清空缓存（游戏重置时调用）
     */
    public void clearCache() {
        validationCache.clear();
        patternCache.clear();
    }

    /**
     * 获取缓存统计信息（调试用）
     */
    public String getCacheStats() {
        return "ValidationCache: " + validationCache.size() + " entries, PatternCache: " + patternCache.size() + " entries";
    }
}