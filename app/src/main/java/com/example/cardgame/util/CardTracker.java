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
    // 每种点数已出的张数（每种点数有4张牌）
    private Map<Rank, Integer> rankPlayedCount = new HashMap<>();
    // 每种花色已出的张数（每个花色有13张牌）
    private Map<Suit, Integer> suitPlayedCount = new HashMap<>();

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
    }

    public int getPlayedCount() {
        return playedCards.size();
    }

    /**
     * ========== 牌的分布统计方法（用于概率建模）==========
     */

    /**
     * 获取某点数剩余牌的张数（每种点数最多4张）
     */
    public int getRemainingCountByRank(Rank rank) {
        int played = rankPlayedCount.getOrDefault(rank, 0);
        return Math.max(0, 4 - played);
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
