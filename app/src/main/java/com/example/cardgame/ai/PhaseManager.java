package com.example.cardgame.ai;

import com.example.cardgame.model.*;
import com.example.cardgame.rule.PatternRecognizer;
import com.example.cardgame.rule.RuleEngine;
import java.util.*;
import java.util.stream.Collectors;

public class PhaseManager implements IPhaseManager {

    public enum GamePhase {
        EARLY, MID, LATE
    }

    private static final int EARLY_HAND_SIZE = 13;
    private static final int LATE_HAND_SIZE = 7;

    // ========== 最优参数（Genetic Algorithm 优化结果 + 手动调优）==========
    private static final double EARLY_BIG_CARD_BONUS = 0.432;     // 开局保留大牌加成
    private static final double FIVE_CARD_PENALTY = -0.567;       // 五张牌惩罚（负值为惩罚）
    private static final double MID_SUPPRESS_BONUS = 0.667;       // 中盘压制奖励
    private static final double LATE_FAST_BONUS = 2.255;          // 残局加速因子
    private static final double LATE_TWO_BONUS = 8.0;             // 残局单2奖励（调高到8.0）
    private static final double AGGRESSION = 1.2;                 // 激进程度（调高到1.2）
    private static final double DEFENSE = 0.2;                    // 防守程度（调低到0.2）
    private static final double TWO_URGENCY_BONUS = 3.5;          // 2牌紧急度奖励

    private final RuleEngine ruleEngine;

    public PhaseManager(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    public GamePhase getCurrentPhase(Player aiPlayer, GameState state) {
        int handSize = aiPlayer.getHandCards().size();
        boolean bigCardsLeft = aiPlayer.getHandCards().stream()
                .anyMatch(c -> c.getRank() == Rank.TWO || c.getRank() == Rank.ACE);
        if (handSize >= EARLY_HAND_SIZE && bigCardsLeft) {
            return GamePhase.EARLY;
        } else if (handSize <= LATE_HAND_SIZE) {
            return GamePhase.LATE;
        } else {
            return GamePhase.MID;
        }
    }

    public double adjustScore(double baseScore, Play candidate, GamePhase phase,
                              Player aiPlayer, GameState state, List<Card> handAfterPlay) {
        double bonus = 0.0;
        List<Card> hand = aiPlayer.getHandCards();
        List<Card> playCards = candidate.getCards();

        switch (phase) {
            case EARLY:
                // 开局：保留大牌（2或A）加成 – 遗传优化值 0.432
                long bigCount = hand.stream().filter(c -> c.getRank() == Rank.TWO || c.getRank() == Rank.ACE).count();
                bonus += EARLY_BIG_CARD_BONUS * bigCount;
                // 如果出的牌中包含2或A，且不是最后一手，扣分
                if (playCards.stream().anyMatch(c -> c.getRank() == Rank.TWO || c.getRank() == Rank.ACE)) {
                    if (hand.size() - playCards.size() > 0) {
                        bonus -= 0.8;
                    }
                }
                // 开局出五张牌：强力牌型奖励，普通牌型采用遗传惩罚
                if (playCards.size() == 5) {
                    if (isVeryStrongPattern(playCards)) {
                        bonus += 1.5;   // 同花顺/铁支 → 重大奖励
                    } else {
                        bonus += FIVE_CARD_PENALTY; // 遗传优化的五张牌惩罚
                    }
                }
                break;

            case MID:
                // 中盘：压制下家奖励 – 遗传优化值 0.667
                if (!candidate.isEmpty()) {
                    bonus += MID_SUPPRESS_BONUS;
                }
                // 中盘出五张牌（任何合法牌型）给予奖励，加速脱手
                if (playCards.size() == 5) {
                    bonus += 1.2;
                }
                // 额外：出中等牌（点数 8~K）加分
                int midCount = (int) playCards.stream().filter(c -> {
                    int w = c.getRank().getWeight();
                    return w >= 5 && w <= 10; // 8~K
                }).count();
                bonus += midCount * 0.1;
                // 新增：中期抢牌权奖励 - 出单张大牌（2或A）给予额外奖励
                if (playCards.size() == 1) {
                    Rank r = playCards.get(0).getRank();
                    if (r == Rank.TWO) bonus += 5.0;
                    else if (r == Rank.ACE) bonus += 3.0;
                }
                break;

            case LATE:
                // 残局：直接出完奖励
                if (hand.size() == playCards.size()) {
                    bonus += 3.0;
                }
                // 残局出五张牌给予更高奖励
                if (playCards.size() == 5) {
                    bonus += 2.0;
                }
                // 残局增强逻辑（使用出牌后的手牌）
                int handSize = handAfterPlay.size();
                if (handSize == 1) {
                    bonus += 5.0;   // 胜利在望，直接出完
                } else if (handSize == 2 && isPair(handAfterPlay)) {
                    bonus += 2.0;   // 出牌后剩两张且是对子，加分
                }
                // 残局有2时大幅加分（调高后的参数）
                if (hasCard(handAfterPlay, Rank.TWO)) {
                    bonus += LATE_TWO_BONUS;   // 出牌后有单张2，控制权保障
                }
                // 惩罚遗留高单张（A或2）
                long highSingles = hand.stream()
                        .filter(c -> c.getRank() == Rank.ACE || c.getRank() == Rank.TWO)
                        .count();
                bonus -= 0.4 * highSingles;
                // 出牌数越多越好 – 遗传优化值 2.255
                bonus += playCards.size() * LATE_FAST_BONUS;
                
                // 激进/防守系数调整
                if (!candidate.isEmpty()) {
                    bonus *= (1.0 + AGGRESSION - DEFENSE);
                }
                // 残局出2的紧急度奖励
                if (playCards.size() == 1 && playCards.get(0).getRank() == Rank.TWO) {
                    bonus += TWO_URGENCY_BONUS;  // 鼓励残局出2
                }
                break;
        }
        return baseScore + bonus;
    }

    private boolean isVeryStrongPattern(List<Card> cards) {
        if (cards.size() != 5) return false;
        PatternRecognizer.PatternInfo info = ruleEngine.recognizePattern(cards);
        PatternRecognizer.PatternType type = info.getType();
        return type == PatternRecognizer.PatternType.STRAIGHT_FLUSH ||
                type == PatternRecognizer.PatternType.IRON_BRANCH;
    }

    private boolean isPair(List<Card> hand) {
        return hand.size() == 2 && hand.get(0).getRank() == hand.get(1).getRank();
    }

    private boolean hasCard(List<Card> hand, Rank rank) {
        return hand.stream().anyMatch(c -> c.getRank() == rank);
    }

    /**
     * 残局强制压牌检测：当对手只剩1张牌时，强制使用大牌压制
     * @param aiPlayer AI玩家
     * @param state 游戏状态
     * @param lastPlay 上一轮出牌（用于判断是否能压制）
     * @return 是否需要强制压牌
     */
    public boolean shouldForceBeat(Player aiPlayer, GameState state, Play lastPlay) {
        // 上一轮无出牌或为空，不强制压牌
        if (lastPlay == null || lastPlay.isEmpty()) {
            return false;
        }
        List<Card> lastPlayed = lastPlay.getCards();
        // 只处理单牌场景，避免复杂牌型导致非法出牌
        if (lastPlayed.size() != 1) {
            return false;
        }
        
        // 检查是否有对手手牌数 <= 2（提前封堵）
        for (Player p : state.getPlayers()) {
            if (!p.getPlayerId().equals(aiPlayer.getPlayerId()) && p.getHandCards().size() <= 2) {
                // 对手只剩1张，检查AI是否有2或A可以压制
                List<Card> hand = aiPlayer.getHandCards();
                boolean hasBigCard = hand.stream()
                        .anyMatch(c -> c.getRank() == Rank.TWO || c.getRank() == Rank.ACE);
                return hasBigCard;
            }
        }
        return false;
    }

    /**
     * 获取激进程度
     */
    public double getAggression() {
        return AGGRESSION;
    }

    /**
     * 获取防守程度
     */
    public double getDefense() {
        return DEFENSE;
    }

    // ========== 保牌型检测方法 ==========

    /**
     * 检查出牌是否会破坏已有的大牌型
     * @param originalHand 原手牌
     * @param remainingHand 出牌后剩余的手牌
     * @return 是否破坏了大牌型
     */
    public boolean wouldBreakBigPattern(List<Card> originalHand, List<Card> remainingHand) {
        // 如果原手牌没有大牌型，则无所谓破坏
        if (!hasBigPattern(originalHand)) return false;
        // 如果剩余手牌仍然有大牌型，则没有破坏
        if (hasBigPattern(remainingHand)) return false;
        // 原来有，现在没有 → 破坏了
        return true;
    }

    /**
     * 检查手牌中是否存在大牌型（五张牌型）
     */
    public boolean hasBigPattern(List<Card> hand) {
        return findStraightFlush(hand) != null ||
               findFourOfAKind(hand) != null ||
               findFullHouse(hand) != null ||
               findFlush(hand) != null ||
               findStraight(hand) != null;
    }

    // ========== 锄大地五张牌型压制检测 ==========

    /**
     * 为锄大地寻找能压制上家出牌的更大牌型（支持5张牌型）
     * @param aiPlayer AI玩家
     * @param lastPlay 上家出的牌
     * @return 能压制的牌（列表），否则返回null
     */
    public List<Card> findBeatForBigTwoPattern(Player aiPlayer, Play lastPlay) {
        if (lastPlay == null || lastPlay.isEmpty()) return null;
        List<Card> lastCards = lastPlay.getCards();
        int lastSize = lastCards.size();
        if (lastSize != 5) return null;  // 只处理5张牌型

        List<Card> hand = aiPlayer.getHandCards();

        // 按优先级从高到低检测：同花顺 > 铁支 > 葫芦 > 同花 > 顺子
        List<Card> straightFlush = findStraightFlush(hand);
        if (straightFlush != null && isStrongerThanBigTwo(straightFlush, lastCards)) {
            return straightFlush;
        }

        List<Card> fourOfAKind = findFourOfAKind(hand);
        if (fourOfAKind != null && isStrongerThanBigTwo(fourOfAKind, lastCards)) {
            return fourOfAKind;
        }

        List<Card> fullHouse = findFullHouse(hand);
        if (fullHouse != null && isStrongerThanBigTwo(fullHouse, lastCards)) {
            return fullHouse;
        }

        List<Card> flush = findFlush(hand);
        if (flush != null && isStrongerThanBigTwo(flush, lastCards)) {
            return flush;
        }

        List<Card> straight = findStraight(hand);
        if (straight != null && isStrongerThanBigTwo(straight, lastCards)) {
            return straight;
        }

        return null;
    }

    /**
     * 找铁支（四张相同牌 + 任意一张单牌）
     */
    private List<Card> findFourOfAKind(List<Card> hand) {
        Map<Rank, Long> freq = hand.stream()
                .collect(Collectors.groupingBy(Card::getRank, Collectors.counting()));
        
        // 使用final变量避免lambda表达式报错
        final Rank[] fourRankHolder = {null};
        for (Map.Entry<Rank, Long> e : freq.entrySet()) {
            if (e.getValue() >= 4) {
                fourRankHolder[0] = e.getKey();
                break;
            }
        }
        
        if (fourRankHolder[0] == null) return null;
        final Rank fourRank = fourRankHolder[0];
        
        List<Card> result = hand.stream()
                .filter(c -> c.getRank() == fourRank)
                .limit(4)
                .collect(Collectors.toList());
        
        // 补任意一张其他牌
        hand.stream()
                .filter(c -> c.getRank() != fourRank)
                .findFirst()
                .ifPresent(result::add);
        
        return result.size() == 5 ? result : null;
    }

    /**
     * 找葫芦（三张相同 + 一对）
     */
    private List<Card> findFullHouse(List<Card> hand) {
        Map<Rank, Long> freq = hand.stream()
                .collect(Collectors.groupingBy(Card::getRank, Collectors.counting()));
        
        // 使用final变量避免lambda表达式报错
        final Rank[] threeRankHolder = {null};
        final Rank[] pairRankHolder = {null};
        
        for (Map.Entry<Rank, Long> e : freq.entrySet()) {
            if (e.getValue() >= 3 && threeRankHolder[0] == null) {
                threeRankHolder[0] = e.getKey();
            } else if (e.getValue() >= 2) {
                pairRankHolder[0] = e.getKey();
            }
        }
        
        final Rank threeRank = threeRankHolder[0];
        final Rank pairRank = pairRankHolder[0];
        
        if (threeRank != null && pairRank != null) {
            List<Card> result = hand.stream()
                    .filter(c -> c.getRank() == threeRank)
                    .limit(3)
                    .collect(Collectors.toList());
            result.addAll(hand.stream()
                    .filter(c -> c.getRank() == pairRank)
                    .limit(2)
                    .collect(Collectors.toList()));
            return result;
        }
        return null;
    }

    /**
     * 找顺子（5张连续单牌，锄大地中A-2-3-4-5是合法顺子）
     */
    private List<Card> findStraight(List<Card> hand) {
        // 独立实现顺子检测，不依赖RuleEngine
        List<Card> sorted = hand.stream()
                .filter(c -> c.getRank() != Rank.TWO) // 2通常不参与普通顺子
                .sorted((a, b) -> a.getRank().getWeight() - b.getRank().getWeight())
                .distinct()
                .collect(Collectors.toList());
        
        // 找最长的连续序列
        List<List<Card>> straights = new ArrayList<>();
        List<Card> current = new ArrayList<>();
        
        for (int i = 0; i < sorted.size(); i++) {
            if (current.isEmpty()) {
                current.add(sorted.get(i));
            } else {
                Card prev = current.get(current.size() - 1);
                Card curr = sorted.get(i);
                int prevWeight = prev.getRank().getWeight();
                int currWeight = curr.getRank().getWeight();
                
                // 处理A-2-3-4-5的特殊情况（A的weight通常最大，需要特殊处理）
                boolean isConsecutive = (currWeight == prevWeight + 1) ||
                        // A-2-3-4-5: A的weight是14，2的weight是15
                        (prev.getRank() == Rank.FIVE && curr.getRank() == Rank.ACE);
                
                if (isConsecutive) {
                    current.add(curr);
                } else {
                    if (current.size() >= 5) {
                        straights.add(new ArrayList<>(current));
                    }
                    current.clear();
                    current.add(curr);
                }
            }
        }
        if (current.size() >= 5) {
            straights.add(current);
        }
        
        // 返回最大的顺子（取最大牌决定）
        if (!straights.isEmpty()) {
            straights.sort((a, b) -> {
                int aMax = a.get(a.size() - 1).getRank().getWeight();
                int bMax = b.get(b.size() - 1).getRank().getWeight();
                return Integer.compare(bMax, aMax);
            });
            return straights.get(0).subList(0, 5);
        }
        return null;
    }

    /**
     * 找同花（5张同花色）
     */
    private List<Card> findFlush(List<Card> hand) {
        Map<Suit, List<Card>> bySuit = hand.stream()
                .collect(Collectors.groupingBy(Card::getSuit));
        
        for (List<Card> cards : bySuit.values()) {
            if (cards.size() >= 5) {
                return cards.stream()
                        .sorted((a, b) -> b.getRank().getWeight() - a.getRank().getWeight())
                        .limit(5)
                        .collect(Collectors.toList());
            }
        }
        return null;
    }

    /**
     * 找同花顺（既是顺子又是同花）
     */
    private List<Card> findStraightFlush(List<Card> hand) {
        // 按花色分组，对每组检测顺子
        Map<Suit, List<Card>> bySuit = hand.stream()
                .collect(Collectors.groupingBy(Card::getSuit));
        
        for (List<Card> suitCards : bySuit.values()) {
            if (suitCards.size() >= 5) {
                List<Card> straight = findStraight(suitCards);
                if (straight != null && straight.size() == 5) {
                    return straight;
                }
            }
        }
        return null;
    }

    /**
     * 比较两个锄大地牌型的大小（5张牌型）
     * 规则：同花顺 > 铁支 > 葫芦 > 同花 > 顺子
     * 同类型时比较关键牌
     */
    private boolean isStrongerThanBigTwo(List<Card> myCards, List<Card> otherCards) {
        if (myCards.size() != otherCards.size()) return false;
        
        int myType = getBigTwoType(myCards);
        int otherType = getBigTwoType(otherCards);
        
        if (myType != otherType) {
            return myType > otherType;
        }
        
        // 同类型比较
        return compareSameType(myCards, otherCards) > 0;
    }

    /**
     * 获取锄大地牌型优先级（数值越大越强）
     * 1-顺子 2-同花 3-葫芦 4-铁支 5-同花顺
     */
    private int getBigTwoType(List<Card> cards) {
        if (cards.size() != 5) return 0;
        if (isStraightFlush(cards)) return 5;
        if (isFourOfAKind(cards)) return 4;
        if (isFullHouse(cards)) return 3;
        if (isFlush(cards)) return 2;
        if (isStraight(cards)) return 1;
        return 0;
    }

    /**
     * 同类型牌型比较
     */
    private int compareSameType(List<Card> my, List<Card> other) {
        int type = getBigTwoType(my);
        
        switch (type) {
            case 5: // 同花顺
            case 1: // 顺子
                // 比较最大牌（A-2-3-4-5特殊处理）
                return compareStraightMax(my, other);
            case 4: // 铁支
                // 比较四张牌的点数
                return compareFourOfAKind(my, other);
            case 3: // 葫芦
                // 比较三张牌的点数
                return compareFullHouse(my, other);
            case 2: // 同花
                // 从大到小比较所有牌
                return compareFlush(my, other);
            default:
                return 0;
        }
    }

    // ========== 牌型判断辅助方法 ==========

    private boolean isStraightFlush(List<Card> cards) {
        if (cards.size() != 5) return false;
        return isFlush(cards) && isStraight(cards);
    }

    private boolean isFourOfAKind(List<Card> cards) {
        if (cards.size() != 5) return false;
        Map<Rank, Long> freq = cards.stream()
                .collect(Collectors.groupingBy(Card::getRank, Collectors.counting()));
        return freq.containsValue(4L);
    }

    private boolean isFullHouse(List<Card> cards) {
        if (cards.size() != 5) return false;
        Map<Rank, Long> freq = cards.stream()
                .collect(Collectors.groupingBy(Card::getRank, Collectors.counting()));
        return freq.containsValue(3L) && freq.containsValue(2L);
    }

    private boolean isFlush(List<Card> cards) {
        if (cards.size() != 5) return false;
        Suit suit = cards.get(0).getSuit();
        return cards.stream().allMatch(c -> c.getSuit() == suit);
    }

    private boolean isStraight(List<Card> cards) {
        if (cards.size() != 5) return false;
        List<Integer> weights = cards.stream()
                .map(c -> c.getRank().getWeight())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        
        if (weights.size() != 5) return false;
        
        // 检查是否连续
        for (int i = 1; i < weights.size(); i++) {
            if (weights.get(i) != weights.get(i-1) + 1) {
                // 检查A-2-3-4-5特殊情况
                if (!(weights.get(0) == 2 && weights.get(1) == 3 && 
                      weights.get(2) == 4 && weights.get(3) == 5 && weights.get(4) == 14)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ========== 同类型比较辅助方法 ==========

    private int compareStraightMax(List<Card> my, List<Card> other) {
        // 找顺子的最大牌
        int myMax = getStraightMax(my);
        int otherMax = getStraightMax(other);
        return Integer.compare(myMax, otherMax);
    }

    private int getStraightMax(List<Card> cards) {
        List<Integer> weights = cards.stream()
                .map(c -> c.getRank().getWeight())
                .sorted()
                .collect(Collectors.toList());
        
        // A-2-3-4-5特殊情况：最大是5
        if (weights.get(0) == 2 && weights.get(1) == 3 && 
            weights.get(2) == 4 && weights.get(3) == 5 && weights.get(4) == 14) {
            return 5;
        }
        return weights.get(4);
    }

    private int compareFourOfAKind(List<Card> my, List<Card> other) {
        Rank myFour = getFourRank(my);
        Rank otherFour = getFourRank(other);
        return Integer.compare(myFour.getWeight(), otherFour.getWeight());
    }

    private Rank getFourRank(List<Card> cards) {
        Map<Rank, Long> freq = cards.stream()
                .collect(Collectors.groupingBy(Card::getRank, Collectors.counting()));
        for (Map.Entry<Rank, Long> e : freq.entrySet()) {
            if (e.getValue() == 4) {
                return e.getKey();
            }
        }
        return null;
    }

    private int compareFullHouse(List<Card> my, List<Card> other) {
        Rank myThree = getThreeRank(my);
        Rank otherThree = getThreeRank(other);
        return Integer.compare(myThree.getWeight(), otherThree.getWeight());
    }

    private Rank getThreeRank(List<Card> cards) {
        Map<Rank, Long> freq = cards.stream()
                .collect(Collectors.groupingBy(Card::getRank, Collectors.counting()));
        for (Map.Entry<Rank, Long> e : freq.entrySet()) {
            if (e.getValue() == 3) {
                return e.getKey();
            }
        }
        return null;
    }

    private int compareFlush(List<Card> my, List<Card> other) {
        List<Integer> myWeights = my.stream()
                .map(c -> c.getRank().getWeight())
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
        List<Integer> otherWeights = other.stream()
                .map(c -> c.getRank().getWeight())
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
        
        for (int i = 0; i < 5; i++) {
            int cmp = Integer.compare(myWeights.get(i), otherWeights.get(i));
            if (cmp != 0) return cmp;
        }
        return 0;
    }
}