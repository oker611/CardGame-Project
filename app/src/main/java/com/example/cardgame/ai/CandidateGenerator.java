package com.example.cardgame.ai;

import com.example.cardgame.model.*;
import com.example.cardgame.rule.PatternRecognizer;
import com.example.cardgame.rule.RuleEngine;
import com.example.cardgame.rule.PlayValidator;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 候选动作生成器 + 启发式排序（已优化）
 */
public class CandidateGenerator {

    private final RuleEngine ruleEngine;
    private final int topK;
    private PhaseManager phaseManager;
    private AIPlayerProfile profile;

    // 原有构造函数（兼容旧调用）
    public CandidateGenerator(RuleEngine ruleEngine, int topK) {
        this(ruleEngine, topK, null, null);
    }

    // 新构造函数，用于注入保牌逻辑所需的对象
    public CandidateGenerator(RuleEngine ruleEngine, int topK, PhaseManager phaseManager, AIPlayerProfile profile) {
        this.ruleEngine = ruleEngine;
        this.topK = topK;
        this.phaseManager = phaseManager;
        this.profile = profile;
    }

    // 添加设置方法（可选）
    public void setPhaseManager(PhaseManager phaseManager) { this.phaseManager = phaseManager; }
    public void setProfile(AIPlayerProfile profile) { this.profile = profile; }

    public List<Play> generate(List<Card> hand, Play lastPlay, boolean isFirstRound, boolean isFirstTurn) {
        // ===== 牌型前置过滤 =====
        // 当上家出牌后，只生成符合牌型要求的候选
        PatternRecognizer.PatternType requiredType = null;
        boolean needFilterByPattern = false;
        
        if (lastPlay != null && !lastPlay.isEmpty()) {
            PatternRecognizer.PatternInfo lastInfo = ruleEngine.recognizePattern(lastPlay.getCards());
            requiredType = lastInfo.getType();
            needFilterByPattern = true;
            System.out.println("[CandidateGenerator] 上家牌型: " + requiredType + "，将按此牌型过滤候选");
        }
        
        List<Play> allLegal = getAllLegalPlays(hand, lastPlay, isFirstRound, isFirstTurn, requiredType, needFilterByPattern);
        if (allLegal.isEmpty()) return Collections.emptyList();

        // ===== 保牌型过滤 =====
        if (profile != null && profile.isKeepBigPattern() && phaseManager != null) {
            List<Play> filtered = new ArrayList<>();
            for (Play play : allLegal) {
                List<Card> remaining = new ArrayList<>(hand);
                remaining.removeAll(play.getCards());
                if (!phaseManager.wouldBreakBigPattern(hand, remaining)) {
                    filtered.add(play);
                }
            }
            // 如果过滤后为空，则退回到不过滤（避免无动作可出）
            if (!filtered.isEmpty()) {
                allLegal = filtered;
            }
        }

        allLegal.sort((p1, p2) -> {
            double s1 = heuristicScore(hand, p1);
            double s2 = heuristicScore(hand, p2);
            return Double.compare(s2, s1);
        });

        return allLegal.size() <= topK ? allLegal : allLegal.subList(0, topK);
    }

    private List<Play> getAllLegalPlays(List<Card> hand, Play lastPlay, boolean isFirstRound, 
                                         boolean isFirstTurn, PatternRecognizer.PatternType requiredType, 
                                         boolean needFilterByPattern) {
        List<Play> result = new ArrayList<>();

        // 生成所有可能的组合（简化：单张、对子、三张、四张、五张牌型）
        List<List<Card>> allCombinations = new ArrayList<>();
        
        // ===== 牌型前置过滤：根据上家牌型决定生成哪些类型 =====
        if (needFilterByPattern && requiredType != null) {
            // 根据上家牌型，只生成符合要求的牌型
            switch (requiredType) {
                case SINGLE:
                    // 上家出单张，只能出单张或万能牌型
                    for (Card c : hand) allCombinations.add(Collections.singletonList(c));
                    // 万能牌型：四张相同或同花顺
                    addWildcardPatterns(hand, allCombinations);
                    break;
                case PAIR:
                    // 上家出对子，只能出对子或万能牌型
                    Map<Rank, List<Card>> rankMap = hand.stream().collect(Collectors.groupingBy(Card::getRank));
                    for (List<Card> sameRank : rankMap.values()) {
                        if (sameRank.size() >= 2) {
                            allCombinations.add(sameRank.subList(0, 2));
                        }
                    }
                    // 万能牌型：四张相同或同花顺
                    addWildcardPatterns(hand, allCombinations);
                    break;
                case TRIPLE:
                    // 上家出三张，只能出三张或万能牌型
                    Map<Rank, List<Card>> rankMapTriple = hand.stream().collect(Collectors.groupingBy(Card::getRank));
                    for (List<Card> sameRank : rankMapTriple.values()) {
                        if (sameRank.size() >= 3) {
                            allCombinations.add(sameRank.subList(0, 3));
                        }
                    }
                    // 万能牌型：四张相同或同花顺
                    addWildcardPatterns(hand, allCombinations);
                    break;
                case STRAIGHT:
                case FLUSH:
                case FULL_HOUSE:
                case IRON_BRANCH:
                case STRAIGHT_FLUSH:
                    // 上家出五张牌型，只能出五张牌型
                    if (hand.size() >= 5) {
                        generateCombinations(hand, 5, 0, new ArrayList<>(), allCombinations);
                    }
                    break;
                default:
                    // 未知牌型，生成所有组合
                    generateAllCombinations(hand, allCombinations);
            }
        } else {
            // 没有上家出牌或需要过滤，生成所有组合
            generateAllCombinations(hand, allCombinations);
        }

        List<Card> lastPlayCards = (lastPlay == null) ? null : lastPlay.getCards();
        for (List<Card> cards : allCombinations) {
            PlayValidator.ValidationResult valid = ruleEngine.validatePlay(
                    cards, lastPlayCards, isFirstRound, isFirstTurn);
            if (valid.valid) {
                PatternRecognizer.PatternInfo info = ruleEngine.recognizePattern(cards);
                CardPattern pattern = mapPattern(info.getType());
                result.add(new Play(null, cards, pattern));
            }
        }

        // 添加 Pass（非首轮首出且有上家时）
        if (!(isFirstRound && isFirstTurn) && lastPlay != null && !lastPlay.isEmpty()) {
            result.add(new Play(null, Collections.emptyList(), null));
        }

        return result;
    }
    
    /**
     * 生成所有可能的牌型组合（不进行牌型过滤）
     */
    private void generateAllCombinations(List<Card> hand, List<List<Card>> allCombinations) {
        // 单张
        for (Card c : hand) allCombinations.add(Collections.singletonList(c));
        // 对子、三张、四张（相同点数）
        Map<Rank, List<Card>> rankMap = hand.stream().collect(Collectors.groupingBy(Card::getRank));
        for (List<Card> sameRank : rankMap.values()) {
            if (sameRank.size() >= 2) {
                allCombinations.add(sameRank.subList(0, 2));
                if (sameRank.size() >= 3) allCombinations.add(sameRank.subList(0, 3));
                if (sameRank.size() >= 4) allCombinations.add(sameRank.subList(0, 4));
            }
        }
        // 五张牌型（简单组合枚举）
        if (hand.size() >= 5) {
            generateCombinations(hand, 5, 0, new ArrayList<>(), allCombinations);
        }
    }
    
    /**
     * 添加万能牌型（炸弹/同花顺）
     */
    private void addWildcardPatterns(List<Card> hand, List<List<Card>> allCombinations) {
        // 四张相同（炸弹）
        Map<Rank, List<Card>> rankMap = hand.stream().collect(Collectors.groupingBy(Card::getRank));
        for (List<Card> sameRank : rankMap.values()) {
            if (sameRank.size() >= 4) {
                allCombinations.add(sameRank.subList(0, 4));
            }
        }
        // 同花顺（五张同花色顺子）
        if (hand.size() >= 5) {
            generateCombinations(hand, 5, 0, new ArrayList<>(), allCombinations);
        }
    }

    private double heuristicScore(List<Card> hand, Play play) {
        if (play.isEmpty()) return 0.0;
        List<Card> cards = play.getCards();
        double score = 0.0;

        // 1. 基础牌力：点数加权和（越低越好，取负值）
        int totalWeight = cards.stream().mapToInt(c -> c.getRank().getWeight()).sum();
        score -= totalWeight * 0.05;

        // 2. 剩余手牌形状：不同点数数量
        Set<Rank> remainingRanks = new HashSet<>();
        for (Card c : hand) if (!cards.contains(c)) remainingRanks.add(c.getRank());
        double shapeScore = remainingRanks.size() / 13.0;
        score += shapeScore * 0.3;

        // 3. 手牌中是否保留控制牌（2或A）
        boolean hasControl = hand.stream().anyMatch(c -> c.getRank() == Rank.TWO || c.getRank() == Rank.ACE);
        if (hasControl) score += 0.2;

        // 4. 对于五张牌型，评估强度并施加阶段惩罚
        if (cards.size() == 5) {
            PatternRecognizer.PatternInfo info = ruleEngine.recognizePattern(cards);
            PatternRecognizer.PatternType type = info.getType();
            double strength = 0.0;
            boolean isStrong = false;
            switch (type) {
                case STRAIGHT_FLUSH:
                    strength = 1.0;
                    isStrong = true;
                    break;
                case IRON_BRANCH:
                    strength = 0.9;
                    isStrong = true;
                    break;
                case FULL_HOUSE:
                    strength = 0.7;
                    break;
                case FLUSH:
                    strength = 0.5;
                    break;
                case STRAIGHT:
                    strength = 0.4;
                    break;
                default:
                    strength = 0.0;
            }
            // 开局（手牌多）时，只有很强牌型才加分，否则严重扣分
            if (hand.size() >= 13) {
                if (isStrong) {
                    score += strength * 2.0;
                } else {
                    score -= 2.5;
                }
            } else {
                score += strength;
            }
        }

        // 5. 出牌张数越少，越安全（但不要过度）
        score += (6 - cards.size()) * 0.1;

        return score;
    }

    private void generateCombinations(List<Card> source, int k, int start, List<Card> current, List<List<Card>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < source.size(); i++) {
            current.add(source.get(i));
            generateCombinations(source, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    private CardPattern mapPattern(PatternRecognizer.PatternType type) {
        if (type == null) return CardPattern.INVALID;
        switch (type) {
            case SINGLE: return CardPattern.SINGLE;
            case PAIR: return CardPattern.PAIR;
            case TRIPLE: return CardPattern.TRIPLE;
            case QUADRUPLE: return CardPattern.QUADRUPLE;
            case STRAIGHT: return CardPattern.STRAIGHT;
            case FLUSH: return CardPattern.FLUSH;
            case FULL_HOUSE: return CardPattern.FULL_HOUSE;
            case IRON_BRANCH: return CardPattern.IRON_BRANCH;
            case STRAIGHT_FLUSH: return CardPattern.STRAIGHT_FLUSH;
            default: return CardPattern.INVALID;
        }
    }
}