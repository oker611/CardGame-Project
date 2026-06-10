package com.example.cardgame.util;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;
import java.util.*;
import java.util.stream.Collectors;

public class CardTracker {
    private Set<Card> playedCards = new HashSet<>();
    private Map<Card, String> playedBy = new HashMap<>();
    private Map<String, List<String>> opponentHistory = new HashMap<>();

    // ========== 牌的分布统计（用于概率建模）==========
    // 每种点数已出的张数（每种点数最多4张）
    private Map<Rank, Integer> rankPlayedCount = new HashMap<>();
    // 每种花色已出的张数（每个花色最多13张）
    private Map<Suit, Integer> suitPlayedCount = new HashMap<>();
    // 自己手牌中每种点数的数量（初始时扣除，用于记牌器显示真正的未知剩余）
    private Map<Rank, Integer> myHandRankCount = new HashMap<>();
    // 自己手牌中每种花色的数量
    private Map<Suit, Integer> myHandSuitCount = new HashMap<>();

    public void onCardPlayed(Card card, String playerId) {
        if (card == null || playedCards.contains(card)) {
            return;
        }
        playedCards.add(card);
        playedBy.put(card, playerId);

        // 更新分布统计
        rankPlayedCount.merge(card.getRank(), 1, Integer::sum);
        suitPlayedCount.merge(card.getSuit(), 1, Integer::sum);
    }

    public void onCardsPlayed(List<Card> cards, String playerId) {
        for (Card card : cards) {
            onCardPlayed(card, playerId);
        }
    }

    /**
     * 初始化自己的手牌信息。记牌器在显示每种点数的剩余张数时，
     * 会从总量中扣除自己手牌已有部分和已打出部分，只显示处于未知状态的剩余牌。
     */
    public void initFromMyHand(List<Card> myHand) {
        myHandRankCount.clear();
        myHandSuitCount.clear();
        if (myHand == null) return;
        for (Card c : myHand) {
            if (c != null) {
                myHandRankCount.merge(c.getRank(), 1, Integer::sum);
                myHandSuitCount.merge(c.getSuit(), 1, Integer::sum);
            }
        }
    }

    public void recordPlay(String playerId, String playDescription) {
        opponentHistory.computeIfAbsent(playerId, k -> new ArrayList<>()).add(playDescription);
    }

    public String getHistorySummary(String playerId) {
        List<String> history = opponentHistory.get(playerId);
        if (history == null || history.isEmpty()) return "";
        int size = history.size();
        int start = size > 20 ? size - 20 : 0;
        List<String> recent = history.subList(start, size);
        return String.join(", ", recent);
    }

    public List<Card> getRemainingCards(List<Card> allCards) {
        return allCards.stream()
                .filter(c -> !playedCards.contains(c))
                .collect(Collectors.toList());
    }

    public boolean isPlayed(Card card) {
        return playedCards.contains(card);
    }

    public Set<Card> getPlayedCards() {
        return new HashSet<>(playedCards);
    }

    public String getPlayedBy(Card card) {
        return playedBy.get(card);
    }

    public void reset() {
        playedCards.clear();
        playedBy.clear();
        opponentHistory.clear();
        rankPlayedCount.clear();
        suitPlayedCount.clear();
        myHandRankCount.clear();
        myHandSuitCount.clear();
    }

    public int getPlayedCount() {
        return playedCards.size();
    }

    /**
     * ========== 牌的分布统计方法（用于概率建模）==========
     */

    /**
     * 获取某点数剩余牌的张数（总量减去已出牌，不考虑自己手牌；供AI概率计算使用）。
     */
    public int getRemainingCountByRank(Rank rank) {
        int played = rankPlayedCount.getOrDefault(rank, 0);
        return Math.max(0, 4 - played);
    }

    /**
     * 获取某点数真正处于未知状态的剩余张数（总量减去自己手牌中该点数数量，再减去已打出数量）。
     * 供记牌器 UI 使用：牌局开始时扣除自己手牌，只显示未知状态的剩余。
     */
    public int getUnknownRemainingCountByRank(Rank rank) {
        int myCount = myHandRankCount.getOrDefault(rank, 0);
        int played = rankPlayedCount.getOrDefault(rank, 0);
        return Math.max(0, 4 - myCount - played);
    }

    /**
     * 获取所有点数的未知剩余张数映射。
     */
    public Map<Rank, Integer> getAllUnknownRemainingByRank() {
        Map<Rank, Integer> result = new HashMap<>();
        for (Rank rank : Rank.values()) {
            result.put(rank, getUnknownRemainingCountByRank(rank));
        }
        return result;
    }

    /**
     * 获取某花色剩余牌的张数（每个花色最多13张）
     */
    public int getRemainingCountBySuit(Suit suit) {
        int played = suitPlayedCount.getOrDefault(suit, 0);
        return Math.max(0, 13 - played);
    }

    /**
     * 获取某张牌的剩余概率（0.0~1.0）
     * 基于已出牌信息计算的概率
     */
    public double getRemainingProbability(Card card) {
        int remaining = getRemainingCountByRank(card.getRank());
        return remaining > 0 ? remaining / 4.0 : 0.0;
    }

    /**
     * 获取剩余牌堆中最高概率的牌（用于优先采样）
     */
    public List<Card> getHighProbabilityCards(List<Card> availableCards, int topN) {
        return availableCards.stream()
                .sorted((c1, c2) -> Double.compare(getRemainingProbability(c2), getRemainingProbability(c1)))
                .limit(topN)
                .collect(Collectors.toList());
    }

    /**
     * 获取牌的分布信息摘要（调试用）
     */
    public String getDistributionSummary() {
        StringBuilder sb = new StringBuilder("牌堆分布:");
        for (Rank rank : Rank.values()) {
            int remaining = getRemainingCountByRank(rank);
            if (remaining < 4) {
                sb.append(" ").append(rank).append("=").append(remaining);
            }
        }
        return sb.toString();
    }
}
