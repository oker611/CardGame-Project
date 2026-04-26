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

        if (lastType == PatternRecognizer.PatternType.SINGLE) {
            return findHigherSingle(lastPlay.get(0));
        } else if (lastType == PatternRecognizer.PatternType.PAIR) {
            return findHigherPair(lastPlay);
        }
        return null;
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
}