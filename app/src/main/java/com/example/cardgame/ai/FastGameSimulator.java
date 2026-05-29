package com.example.cardgame.ai;

import com.example.cardgame.model.*;
import com.example.cardgame.rule.PatternRecognizer;
import com.example.cardgame.rule.RuleEngine;
import java.util.*;

/**
 * 快速模拟器（已支持五张牌型压制）
 */
public class FastGameSimulator {

    private final RuleEngine ruleEngine;

    public FastGameSimulator(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    public int simulateToEnd(GameState state, String aiPlayerId) {
        int steps = 0;
        while (!state.isGameOver() && steps < 500) {
            Player current = state.getCurrentPlayer();
            if (current == null) break;

            List<Card> decision = greedyDecision(current, state);
            if (decision == null || decision.isEmpty()) {
                fastPass(state, current.getPlayerId());
            } else {
                fastPlay(state, current.getPlayerId(), decision);
            }
            steps++;
        }
        // 按手牌数排序，得出排名（手牌少者胜）
        List<Player> players = new ArrayList<>(state.getPlayers());
        players.sort(Comparator.comparingInt(p -> p.getHandCards().size()));
        int rank = 1;
        for (Player p : players) {
            if (p.getPlayerId().equals(aiPlayerId)) return rank;
            rank++;
        }
        return 4;
    }

    private List<Card> greedyDecision(Player player, GameState state) {
        List<Card> hand = player.getHandCards();
        Play lastPlay = state.getLastPlay();
        boolean firstRound = state.isOpeningTurn();
        boolean firstTurn = (lastPlay == null || lastPlay.isEmpty());

        if (lastPlay != null && !lastPlay.isEmpty()) {
            PatternRecognizer.PatternInfo lastInfo = ruleEngine.recognizePattern(lastPlay.getCards());
            PatternRecognizer.PatternType type = lastInfo.getType();

            if (type == PatternRecognizer.PatternType.SINGLE) {
                Card lastCard = lastPlay.getCards().get(0);
                int lastValue = getCardValue(lastCard);
                Card best = null;
                for (Card c : hand) {
                    int val = getCardValue(c);
                    if (val > lastValue && (best == null || val < getCardValue(best))) {
                        best = c;
                    }
                }
                return best == null ? null : Collections.singletonList(best);
            }
            else if (type == PatternRecognizer.PatternType.PAIR) {
                Map<Rank, List<Card>> rankMap = groupByRank(hand);
                List<Card> bestPair = null;
                int bestValue = Integer.MAX_VALUE;
                int lastRankWeight = lastInfo.getCompareValue();
                for (List<Card> sameRank : rankMap.values()) {
                    if (sameRank.size() >= 2) {
                        int rankWeight = sameRank.get(0).getRank().getWeight();
                        if (rankWeight > lastRankWeight && rankWeight < bestValue) {
                            bestPair = sameRank.subList(0, 2);
                            bestValue = rankWeight;
                        }
                    }
                }
                return bestPair;
            }
            else if (type == PatternRecognizer.PatternType.TRIPLE) {
                int lastRank = lastInfo.getCompareValue();
                Map<Rank, List<Card>> rankMap = groupByRank(hand);
                List<Card> bestTriple = null;
                int bestRank = Integer.MAX_VALUE;
                for (List<Card> sameRank : rankMap.values()) {
                    if (sameRank.size() >= 3) {
                        int rankWeight = sameRank.get(0).getRank().getWeight();
                        if (rankWeight > lastRank && rankWeight < bestRank) {
                            bestTriple = sameRank.subList(0, 3);
                            bestRank = rankWeight;
                        }
                    }
                }
                return bestTriple;
            }
            else if (type == PatternRecognizer.PatternType.STRAIGHT ||
                    type == PatternRecognizer.PatternType.FLUSH ||
                    type == PatternRecognizer.PatternType.FULL_HOUSE ||
                    type == PatternRecognizer.PatternType.IRON_BRANCH ||
                    type == PatternRecognizer.PatternType.STRAIGHT_FLUSH) {
                return findHigherFiveCardPattern(hand, lastPlay.getCards());
            }
        }

        // 无上家或无法压制：出最小单张
        if (!hand.isEmpty()) {
            Card min = hand.stream().min(Comparator.comparingInt(this::getCardValue)).orElse(null);
            return min == null ? null : Collections.singletonList(min);
        }
        return null;
    }

    private List<Card> findHigherFiveCardPattern(List<Card> hand, List<Card> lastPlay) {
        if (hand.size() < 5) return null;
        PatternRecognizer.PatternInfo lastInfo = ruleEngine.recognizePattern(lastPlay);
        int lastPriority = getFiveCardPriority(lastInfo.getType());
        int lastCompare = lastInfo.getCompareValue();

        List<List<Card>> allCombinations = new ArrayList<>();
        generateCombinations(hand, 5, 0, new ArrayList<>(), allCombinations);

        List<List<Card>> valid = new ArrayList<>();
        for (List<Card> combo : allCombinations) {
            PatternRecognizer.PatternInfo curInfo = ruleEngine.recognizePattern(combo);
            int curPriority = getFiveCardPriority(curInfo.getType());
            if (curPriority > lastPriority) {
                valid.add(combo);
            } else if (curPriority == lastPriority && curInfo.getCompareValue() > lastCompare) {
                valid.add(combo);
            }
        }
        if (valid.isEmpty()) return null;
        valid.sort((c1, c2) -> {
            PatternRecognizer.PatternInfo i1 = ruleEngine.recognizePattern(c1);
            PatternRecognizer.PatternInfo i2 = ruleEngine.recognizePattern(c2);
            int p1 = getFiveCardPriority(i1.getType());
            int p2 = getFiveCardPriority(i2.getType());
            if (p1 != p2) return Integer.compare(p1, p2);
            return Integer.compare(i1.getCompareValue(), i2.getCompareValue());
        });
        return valid.get(0);
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

    private void fastPass(GameState state, String playerId) {
        Player p = state.getPlayerById(playerId);
        if (p != null) p.setPassed(true);
        List<Player> players = state.getPlayers();
        int idx = players.indexOf(p);
        Player next = players.get((idx + 1) % players.size());
        state.setCurrentPlayerId(next.getPlayerId());
    }

    private void fastPlay(GameState state, String playerId, List<Card> cards) {
        Player p = state.getPlayerById(playerId);
        p.getHandCards().removeAll(cards);
        Play play = new Play(playerId, cards, CardPattern.SINGLE); // pattern 仅用于标识
        state.setLastPlay(play);
        state.setOpeningTurn(false);
        for (Player pl : state.getPlayers()) pl.setPassed(false);
        List<Player> players = state.getPlayers();
        int idx = players.indexOf(p);
        Player next = players.get((idx + 1) % players.size());
        state.setCurrentPlayerId(next.getPlayerId());
    }

    private int getCardValue(Card card) {
        return card.getRank().getWeight() * 10 + card.getSuit().getWeight();
    }

    private Map<Rank, List<Card>> groupByRank(List<Card> cards) {
        Map<Rank, List<Card>> map = new HashMap<>();
        for (Card c : cards) {
            map.computeIfAbsent(c.getRank(), k -> new ArrayList<>()).add(c);
        }
        return map;
    }
}