package com.example.cardgame.ai;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.rule.PatternRecognizer;
import com.example.cardgame.rule.PlayValidator;
import com.example.cardgame.rule.PatternRecognizer.PatternInfo;
import com.example.cardgame.rule.RuleConfig;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 贪心策略：枚举所有可能的合法出牌组合，选择能压住上家的最小组合。
 * 完全不使用牌型分支，通过通用组合生成 + 过滤器实现。
 */
public class GreedyAIDecisionStrategy implements AIDecisionStrategy {

    private final RuleConfig config;
    private final PatternRecognizer patternRecognizer;
    private final PlayValidator playValidator;

    // 出牌失败计数（用于兜底逻辑）
    private int consecutiveFailCount = 0;

    public GreedyAIDecisionStrategy(RuleConfig config) {
        this.config = config;
        this.patternRecognizer = new PatternRecognizer(config);
        this.playValidator = new PlayValidator(config);
    }

    @Override
    public List<Card> decidePlay(Player aiPlayer, GameState gameState) {
        List<Card> hand = aiPlayer.getHandCards();
        List<Card> lastPlayCards = gameState.getLastPlay() != null
                ? gameState.getLastPlay().getCards()
                : null;
        boolean isFirstRound = gameState.isFirstRound();
        boolean isFirstTurn = gameState.isFirstTurnOfRound();

        // 1. 生成所有可能的出牌组合（1-5张，且自身牌型合法）
        List<List<Card>> allPlays = generateAllValidPlays(hand);
        if (allPlays.isEmpty()) {
            return null; // 无牌可出，过牌
        }

        // 2. 首轮首出：根据配置检查必出牌
        if (isFirstRound && isFirstTurn && config.requiredOpeningRank != null) {
            allPlays = filterMustIncludeRequiredOpeningCard(allPlays);
            if (allPlays.isEmpty()) {
                return hand.stream()
                        .filter(c -> c.getRank() == config.requiredOpeningRank
                                && c.getSuit() == config.requiredOpeningSuit)
                        .findFirst()
                        .map(Collections::singletonList)
                        .orElse(null);
            }
        }

        // 3. 如果有上家出的牌，只保留能压过它的组合
        if (lastPlayCards != null && !lastPlayCards.isEmpty()) {
            List<List<Card>> beatable = new ArrayList<>();
            for (List<Card> play : allPlays) {
                if (canBeat(play, lastPlayCards, isFirstRound, isFirstTurn)) {
                    beatable.add(play);
                }
            }
            allPlays = beatable;
            if (allPlays.isEmpty()) {
                return null; // 无法压牌，过牌
            }
        }

        // 4. 贪心选择：按比较值从小到大排序，选最小的
        allPlays.sort(Comparator.comparingInt(this::getPlayCompareValue));
        return allPlays.get(0);
    }

    // ================== 通用组合生成 ==================
    private List<List<Card>> generateAllValidPlays(List<Card> hand) {
        List<List<Card>> allCombinations = new ArrayList<>();
        for (int size = 1; size <= 5 && size <= hand.size(); size++) {
            generateCombinations(hand, size, 0, new ArrayList<>(), allCombinations);
        }
        return allCombinations.stream()
                .filter(this::isValidPattern)
                .collect(Collectors.toList());
    }

    private void generateCombinations(List<Card> source, int size, int start,
                                      List<Card> current, List<List<Card>> result) {
        if (current.size() == size) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < source.size(); i++) {
            current.add(source.get(i));
            generateCombinations(source, size, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    private boolean isValidPattern(List<Card> cards) {
        return playValidator.isValidPattern(cards);
    }

    private boolean canBeat(List<Card> myPlay, List<Card> lastPlay,
                            boolean isFirstRound, boolean isFirstTurn) {
        PlayValidator.ValidationResult result = playValidator.validatePlay(
                myPlay, lastPlay, isFirstRound, isFirstTurn);
        return result.valid;
    }

    private int getPlayCompareValue(List<Card> play) {
        PatternInfo info = patternRecognizer.recognizePattern(play);
        return info.getCompareValue();
    }

    private List<List<Card>> filterMustIncludeRequiredOpeningCard(List<List<Card>> plays) {
        List<List<Card>> filtered = new ArrayList<>();
        for (List<Card> play : plays) {
            if (play.stream().anyMatch(c -> c.getRank() == config.requiredOpeningRank
                    && c.getSuit() == config.requiredOpeningSuit)) {
                filtered.add(play);
            }
        }
        return filtered;
    }

    @Override
    public void recordPlayFailure() {
        consecutiveFailCount++;
        System.out.println("[GreedyAI] 出牌失败，连续失败次数: " + consecutiveFailCount);
    }

    @Override
    public void resetFailCount() {
        consecutiveFailCount = 0;
    }
}