package com.example.cardgame.ai;

import com.example.cardgame.model.*;
import com.example.cardgame.rule.PatternRecognizer;
import com.example.cardgame.rule.RuleEngine;
import java.util.*;

/**
 * 快速模拟器（已支持五张牌型压制、对手风格感知）
 */
public class FastGameSimulator {

    private final RuleEngine ruleEngine;
    private Map<String, AIPlayerProfile> opponentProfiles = new HashMap<>();

    public FastGameSimulator(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    public void setOpponentProfiles(Map<String, AIPlayerProfile> profiles) {
        this.opponentProfiles = profiles != null ? profiles : new HashMap<>();
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
        int handSize = hand.size();

        AIPlayerProfile profile = opponentProfiles.get(player.getPlayerId());
        double aggressiveness = profile != null ? profile.getOpponentAggressiveness() : 0.5;
        boolean defensive = profile != null && profile.isOpponentDefensive();

        return SimulatedOpponentPolicy.decidePlay(hand, lastPlay, handSize, ruleEngine, aggressiveness, defensive);
    }

    // ========== 模拟对手策略（包含风格感知） ==========
    private static class SimulatedOpponentPolicy {

        public static List<Card> decidePlay(List<Card> hand, Play lastPlay, int handSize,
                                           RuleEngine ruleEngine, double aggressiveness, boolean defensive) {
            boolean isLate = handSize <= 3;
            boolean firstTurn = (lastPlay == null || lastPlay.isEmpty());

            if (firstTurn) {
                return decideFirstPlay(hand, handSize, aggressiveness);
            }

            if (isLate) {
                List<Card> smallest = findSmallestLegalPlay(hand, lastPlay, ruleEngine);
                return smallest != null ? smallest : Collections.emptyList();
            }

            if (defensive && aggressiveness < 0.4) {
                return Collections.emptyList();
            }

            List<Card> beatPlay = findSmallestBeatPlay(hand, lastPlay, ruleEngine);
            if (beatPlay != null && !beatPlay.isEmpty()) {
                if (aggressiveness >= 0.7) {
                    return beatPlay;
                }
                if (isWasteHighCard(beatPlay, hand) && hasAlternativePlay(hand, lastPlay, ruleEngine)) {
                    return Collections.emptyList();
                }
                return beatPlay;
            }

            return Collections.emptyList();
        }

        private static List<Card> decideFirstPlay(List<Card> hand, int handSize, double aggressiveness) {
            if (handSize == 1) {
                return hand;
            }
            if (handSize == 2 && isPair(hand)) {
                return hand;
            }
            if (aggressiveness >= 0.7) {
                Card max = hand.stream().max(Comparator.comparingInt(c -> c.getRank().getWeight())).orElse(null);
                return max == null ? Collections.emptyList() : Collections.singletonList(max);
            }
            Card min = hand.stream().min(Comparator.comparingInt(c -> c.getRank().getWeight())).orElse(null);
            return min == null ? Collections.emptyList() : Collections.singletonList(min);
        }

        private static List<Card> findSmallestLegalPlay(List<Card> hand, Play lastPlay, RuleEngine ruleEngine) {
            PatternRecognizer.PatternInfo lastInfo = ruleEngine.recognizePattern(lastPlay.getCards());
            PatternRecognizer.PatternType type = lastInfo.getType();

            // 先尝试找最小压制牌
            List<Card> beatPlay = findSmallestBeatPlay(hand, lastPlay, ruleEngine);
            if (beatPlay != null) {
                return beatPlay;
            }

            // 无法压制，尝试出其他牌型（残局策略）
            // 优先出小单张
            Card min = hand.stream().min(Comparator.comparingInt(c -> c.getRank().getWeight())).orElse(null);
            return min == null ? null : Collections.singletonList(min);
        }

        private static List<Card> findSmallestBeatPlay(List<Card> hand, Play lastPlay, RuleEngine ruleEngine) {
            PatternRecognizer.PatternInfo lastInfo = ruleEngine.recognizePattern(lastPlay.getCards());
            PatternRecognizer.PatternType type = lastInfo.getType();

            if (type == PatternRecognizer.PatternType.SINGLE) {
                Card lastCard = lastPlay.getCards().get(0);
                int lastWeight = lastCard.getRank().getWeight();
                Card best = null;
                for (Card c : hand) {
                    int weight = c.getRank().getWeight();
                    if (weight > lastWeight && (best == null || weight < best.getRank().getWeight())) {
                        best = c;
                    }
                }
                return best == null ? null : Collections.singletonList(best);
            }
            else if (type == PatternRecognizer.PatternType.PAIR) {
                Map<Rank, List<Card>> rankMap = groupByRank(hand);
                List<Card> bestPair = null;
                int bestWeight = Integer.MAX_VALUE;
                int lastRankWeight = lastInfo.getCompareValue();
                for (List<Card> sameRank : rankMap.values()) {
                    if (sameRank.size() >= 2) {
                        int rankWeight = sameRank.get(0).getRank().getWeight();
                        if (rankWeight > lastRankWeight && rankWeight < bestWeight) {
                            bestPair = new ArrayList<>(sameRank.subList(0, 2));
                            bestWeight = rankWeight;
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
                            bestTriple = new ArrayList<>(sameRank.subList(0, 3));
                            bestRank = rankWeight;
                        }
                    }
                }
                return bestTriple;
            }
            else if (isFiveCardType(type)) {
                return findHigherFiveCardPattern(hand, lastPlay.getCards(), ruleEngine);
            }

            return null;
        }

        private static boolean hasAlternativePlay(List<Card> hand, Play lastPlay, RuleEngine ruleEngine) {
            // 检查是否有不包含大牌的其他出牌方式
            // 简化版：检查手牌中是否有比2和A小的牌
            return hand.stream().anyMatch(c -> c.getRank().getWeight() < 14); // 小于A
        }

        private static boolean isWasteHighCard(List<Card> play, List<Card> hand) {
            // 如果出的牌中包含2或A，且手牌中还有其他小牌，则视为浪费
            boolean hasHigh = play.stream().anyMatch(c -> 
                    c.getRank() == Rank.TWO || c.getRank() == Rank.ACE);
            Set<String> playCardIds = new HashSet<>();
            for (Card c : play) {
                playCardIds.add(c.getCardId());
            }
            boolean hasLow = hand.stream().anyMatch(c -> 
                    c.getRank().getWeight() < 14 && !playCardIds.contains(c.getCardId()));
            return hasHigh && hasLow;
        }

        private static boolean isPair(List<Card> hand) {
            return hand.size() == 2 && hand.get(0).getRank() == hand.get(1).getRank();
        }

        private static boolean isFiveCardType(PatternRecognizer.PatternType type) {
            return type == PatternRecognizer.PatternType.STRAIGHT ||
                    type == PatternRecognizer.PatternType.FLUSH ||
                    type == PatternRecognizer.PatternType.FULL_HOUSE ||
                    type == PatternRecognizer.PatternType.IRON_BRANCH ||
                    type == PatternRecognizer.PatternType.STRAIGHT_FLUSH;
        }

        private static List<Card> findHigherFiveCardPattern(List<Card> hand, List<Card> lastPlay, RuleEngine ruleEngine) {
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

        private static int getFiveCardPriority(PatternRecognizer.PatternType type) {
            switch (type) {
                case STRAIGHT:       return 1;
                case FLUSH:          return 2;
                case FULL_HOUSE:     return 3;
                case IRON_BRANCH:    return 4;
                case STRAIGHT_FLUSH: return 5;
                default:             return 0;
            }
        }

        private static void generateCombinations(List<Card> source, int k, int start, 
                                                List<Card> current, List<List<Card>> result) {
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

        private static Map<Rank, List<Card>> groupByRank(List<Card> cards) {
            Map<Rank, List<Card>> map = new HashMap<>();
            for (Card c : cards) {
                map.computeIfAbsent(c.getRank(), k -> new ArrayList<>()).add(c);
            }
            return map;
        }
    }

    // ========== 辅助方法 ==========
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

    private Map<Rank, List<Card>> groupByRank(List<Card> cards) {
        Map<Rank, List<Card>> map = new HashMap<>();
        for (Card c : cards) {
            map.computeIfAbsent(c.getRank(), k -> new ArrayList<>()).add(c);
        }
        return map;
    }
}