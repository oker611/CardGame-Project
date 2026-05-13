package com.example.cardgame.ai;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;
import com.example.cardgame.rule.PatternRecognizer;
import com.example.cardgame.rule.PlayValidator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 玩家：能根据当前牌局状态选择合法出牌（单张/对子），无合法牌则 Pass
 */
public class AIPlayer {
    private String playerId;
    private List<Card> hand;
    private PatternRecognizer patternRecognizer;
    private PlayValidator playValidator;

    public AIPlayer(String playerId) {
        this.playerId = playerId;
        this.hand = new ArrayList<>();
        this.patternRecognizer = new PatternRecognizer();
        this.playValidator = new PlayValidator();
    }

    // 设置手牌（由 GameEngine 发牌时调用）
    public void setHand(List<Card> hand) {
        this.hand = new ArrayList<>(hand);
    }

    public List<Card> getHand() {
        return hand;
    }

    public String getPlayerId() {
        return playerId;
    }

    /**
     * AI 决策：返回要出的牌，如果返回 null 或空列表则表示 Pass
     * @param lastPlay 桌面上最后出的牌（本轮有上家时传入，无上家传 null 或空列表）
     * @param isFirstRound 是否为游戏第一轮（影响方块3强制规则）
     * @param isFirstTurn 是否为当前轮次的第一个出牌者
     * @return 要出的牌列表，null 或空列表表示 Pass
     */
    public List<Card> choosePlay(List<Card> lastPlay, boolean isFirstRound, boolean isFirstTurn) {
        // 情况1：桌面上无牌（本轮第一个出牌）
        if (lastPlay == null || lastPlay.isEmpty()) {
            if (isFirstRound && isFirstTurn) {
                // 首轮首出：必须出包含方块3的牌
                return findPlayWithDiamondThree();
            } else {
                // 非首轮首出：出最小单张（也可改为最小对子，策略自选）
                return findSmallestSingle();
            }
        }

        // 情况2：有上家牌，需要压牌
        PatternRecognizer.PatternInfo lastInfo = patternRecognizer.recognizePattern(lastPlay);
        PatternRecognizer.PatternType lastType = lastInfo.getType();
        if (lastType == PatternRecognizer.PatternType.INVALID) {
            return null; // 上家牌型异常，AI Pass
        }

        // Route to specific pattern finders based on last play type
        switch (lastType) {
            case SINGLE:
                return findHigherSingle(lastPlay.get(0));
            case PAIR:
                return findHigherPair(lastPlay);
            case TRIPLE:
                return findHigherTriple(lastPlay);
            case QUADRUPLE:
                return findHigherQuadruple(lastPlay);
            case STRAIGHT:
            case FLUSH:
            case FULL_HOUSE:
            case IRON_BRANCH:
            case STRAIGHT_FLUSH:
                return findHigherFiveCardPattern(lastPlay, isFirstRound, isFirstTurn);
            default:
                return null;
        }
    }

    // ---------- 辅助方法 ----------

    /** 获取单张牌的 compareValue（复用 PatternRecognizer 逻辑） */
    private int getCardCompareValue(Card card) {
        PatternRecognizer.PatternInfo info = patternRecognizer.recognizePattern(Collections.singletonList(card));
        return info.getCompareValue();
    }

    /** 寻找手牌中最小的单张 */
    private List<Card> findSmallestSingle() {
        if (hand.isEmpty()) return null;
        Card smallest = hand.stream()
                .min(Comparator.comparingInt(this::getCardCompareValue))
                .orElse(null);
        return smallest == null ? null : Collections.singletonList(smallest);
    }

    /** 寻找包含方块3的出牌（首轮首出专用） */
    private List<Card> findPlayWithDiamondThree() {
        // 优先出单张方块3
        for (Card c : hand) {
            if (c.isThreeOfDiamonds()) {
                return Collections.singletonList(c);
            }
        }
        // 方块3理论上一定会被发到某个玩家手中，但以防万一，找包含方块3的对子
        for (Card c : hand) {
            if (c.getSuit() == Suit.DIAMONDS && c.getRank() == Rank.THREE) {
                for (Card other : hand) {
                    if (other != c && other.getRank() == Rank.THREE) {
                        return Arrays.asList(c, other);
                    }
                }
            }
        }
        // 兜底：出最小单张（不应发生）
        return findSmallestSingle();
    }

    /** 找能压住单张的最小单张 */
    private List<Card> findHigherSingle(Card lastCard) {
        int lastValue = getCardCompareValue(lastCard);
        Card best = null;
        for (Card c : hand) {
            int curValue = getCardCompareValue(c);
            if (curValue > lastValue) {
                if (best == null || curValue < getCardCompareValue(best)) {
                    best = c;
                }
            }
        }
        return best == null ? null : Collections.singletonList(best);
    }

    /** 找能压住对子的最小对子 */
    private List<Card> findHigherPair(List<Card> lastPair) {
        PatternRecognizer.PatternInfo lastInfo = patternRecognizer.recognizePattern(lastPair);
        int lastCompareValue = lastInfo.getCompareValue();

        Map<Rank, List<Card>> rankMap = hand.stream().collect(Collectors.groupingBy(Card::getRank));
        List<List<Card>> validPairs = new ArrayList<>();
        for (Map.Entry<Rank, List<Card>> entry : rankMap.entrySet()) {
            if (entry.getValue().size() >= 2) {
                List<Card> pair = entry.getValue().subList(0, 2);
                PatternRecognizer.PatternInfo pairInfo = patternRecognizer.recognizePattern(pair);
                if (pairInfo.getCompareValue() > lastCompareValue) {
                    validPairs.add(pair);
                }
            }
        }
        if (validPairs.isEmpty()) return null;
        validPairs.sort(Comparator.comparingInt(p -> patternRecognizer.recognizePattern(p).getCompareValue()));
        return validPairs.get(0);
    }

    private List<Card> findHigherTriple(List<Card> lastTriple) {
        PatternRecognizer.PatternInfo lastInfo = patternRecognizer.recognizePattern(lastTriple);
        int lastCompareValue = lastInfo.getCompareValue();

        Map<Rank, List<Card>> rankMap = hand.stream().collect(Collectors.groupingBy(Card::getRank));
        List<List<Card>> validTriples = new ArrayList<>();
        for (Map.Entry<Rank, List<Card>> entry : rankMap.entrySet()) {
            if (entry.getValue().size() >= 3) {
                List<Card> triple = entry.getValue().subList(0, 3);
                PatternRecognizer.PatternInfo tripleInfo = patternRecognizer.recognizePattern(triple);
                if (tripleInfo.getCompareValue() > lastCompareValue) {
                    validTriples.add(triple);
                }
            }
        }
        if (validTriples.isEmpty()) return null;
        validTriples.sort(Comparator.comparingInt(t -> patternRecognizer.recognizePattern(t).getCompareValue()));
        return validTriples.get(0);
    }

    private List<Card> findHigherQuadruple(List<Card> lastQuadruple) {
        PatternRecognizer.PatternInfo lastInfo = patternRecognizer.recognizePattern(lastQuadruple);
        int lastCompareValue = lastInfo.getCompareValue();

        Map<Rank, List<Card>> rankMap = hand.stream().collect(Collectors.groupingBy(Card::getRank));
        List<List<Card>> validQuadruples = new ArrayList<>();
        for (Map.Entry<Rank, List<Card>> entry : rankMap.entrySet()) {
            if (entry.getValue().size() >= 4) {
                List<Card> quadruple = entry.getValue().subList(0, 4);
                PatternRecognizer.PatternInfo quadInfo = patternRecognizer.recognizePattern(quadruple);
                if (quadInfo.getCompareValue() > lastCompareValue) {
                    validQuadruples.add(quadruple);
                }
            }
        }
        if (validQuadruples.isEmpty()) return null;
        validQuadruples.sort(Comparator.comparingInt(q -> patternRecognizer.recognizePattern(q).getCompareValue()));
        return validQuadruples.get(0);
    }

    /**
     * Finds the lowest valid 5-card combination from hand that can beat the last 5-card play.
     */
    private List<Card> findHigherFiveCardPattern(List<Card> lastPlay, boolean isFirstRound, boolean isFirstTurn) {
        if (hand.size() < 5) return null;

        List<List<Card>> allCombinations = new ArrayList<>();
        generateCombinations(hand, 5, 0, new ArrayList<>(), allCombinations);

        List<List<Card>> validPlays = new ArrayList<>();
        for (List<Card> combination : allCombinations) {
            // Re-use PlayValidator to guarantee absolute correctness of rules
            PlayValidator.ValidationResult result = playValidator.validatePlay(combination, lastPlay, isFirstRound, isFirstTurn);
            if (result.valid) {
                validPlays.add(combination);
            }
        }

        if (validPlays.isEmpty()) return null;

        // Sort by pattern priority first, then by internal compare value to play the smallest possible winning hand
        validPlays.sort((c1, c2) -> {
            PatternRecognizer.PatternInfo info1 = patternRecognizer.recognizePattern(c1);
            PatternRecognizer.PatternInfo info2 = patternRecognizer.recognizePattern(c2);
            int p1 = getFiveCardPriority(info1.getType());
            int p2 = getFiveCardPriority(info2.getType());
            if (p1 != p2) {
                return Integer.compare(p1, p2);
            }
            return Integer.compare(info1.getCompareValue(), info2.getCompareValue());
        });

        return formatPatternOutput(validPlays.get(0));
    }

    private void generateCombinations(List<Card> source, int combinationSize, int start, List<Card> current, List<List<Card>> result) {
        if (current.size() == combinationSize) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < source.size(); i++) {
            current.add(source.get(i));
            generateCombinations(source, combinationSize, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    private int getFiveCardPriority(PatternRecognizer.PatternType type) {
        switch (type) {
            case STRAIGHT:       return 1;
            case FLUSH:          return 2;
            case FULL_HOUSE:     return 3;
            case IRON_BRANCH:    return 4;
            case STRAIGHT_FLUSH: return 5;
            default:             return 0;
        }
    }

    private List<Card> formatPatternOutput(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return cards;
        }

        PatternRecognizer.PatternInfo info = patternRecognizer.recognizePattern(cards);
        PatternRecognizer.PatternType type = info.getType();

        // 针对三带二（葫芦）进行顺序重排
        if (type == PatternRecognizer.PatternType.FULL_HOUSE) {
            Map<Rank, List<Card>> grouped = cards.stream().collect(Collectors.groupingBy(Card::getRank));
            List<Card> tripleCards = new ArrayList<>();
            List<Card> pairCards = new ArrayList<>();

            for (List<Card> group : grouped.values()) {
                if (group.size() == 3) {
                    tripleCards.addAll(group);
                } else if (group.size() == 2) {
                    pairCards.addAll(group);
                }
            }

            if (!tripleCards.isEmpty() && !pairCards.isEmpty()) {
                List<Card> result = new ArrayList<>();
                result.addAll(tripleCards);
                result.addAll(pairCards);
                return result;
            }
        }
        // 针对四带一（铁支）顺便进行展示优化
        else if (type == PatternRecognizer.PatternType.IRON_BRANCH) {
            Map<Rank, List<Card>> grouped = cards.stream().collect(Collectors.groupingBy(Card::getRank));
            List<Card> quadCards = new ArrayList<>();
            List<Card> singleCard = new ArrayList<>();

            for (List<Card> group : grouped.values()) {
                if (group.size() == 4) {
                    quadCards.addAll(group);
                } else if (group.size() == 1) {
                    singleCard.addAll(group);
                }
            }

            if (!quadCards.isEmpty() && !singleCard.isEmpty()) {
                List<Card> result = new ArrayList<>();
                result.addAll(quadCards);
                result.addAll(singleCard);
                return result;
            }
        }

        return cards;
    }
}