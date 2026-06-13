package com.example.cardgame.ga;

import com.example.cardgame.model.*;
import java.util.*;

public class Tournament {

    public static class GameResult {
        public int[] scores;
        public GameMetrics metrics;

        public GameResult(int[] scores, GameMetrics metrics) {
            this.scores = scores;
            this.metrics = metrics;
        }
    }

    public static GameResult playGameWithMetrics(com.example.cardgame.ai.AIDecisionStrategy[] strategies, List<Card>[] hands) {
        GameState state = new GameState();
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Player p = new Player("P" + (i + 1), "AI" + (i + 1));
            p.setHandCards(new ArrayList<>(hands[i]));
            players.add(p);
        }
        state.setPlayers(players);

        Player starter = findDiamondThreeOwner(players);
        state.setCurrentPlayerId(starter.getPlayerId());
        state.setOpeningTurn(true);
        state.setGameOver(false);

        int currentIdx = players.indexOf(starter);
        int consecutivePassCount = 0;
        String lastWinnerId = starter.getPlayerId();

        GameMetrics metrics = new GameMetrics();

        while (!state.isGameOver() && consecutivePassCount < 100) {
            Player cur = players.get(currentIdx);

            List<Card> chosen = null;
            try {
                chosen = strategies[currentIdx].decidePlay(cur, state);
            } catch (Exception e) {
                chosen = null;
            }

            if (chosen == null || chosen.isEmpty()) {
                cur.setPassed(true);
                consecutivePassCount++;

                if (consecutivePassCount >= 3) {
                    state.setLastPlay(null);
                    consecutivePassCount = 0;

                    if (lastWinnerId != null) {
                        for (int i = 0; i < players.size(); i++) {
                            if (players.get(i).getPlayerId().equals(lastWinnerId)) {
                                currentIdx = i;
                                break;
                            }
                        }
                    }
                    continue;
                }
            } else {
                List<Card> handCards = new ArrayList<>(cur.getHandCards());
                handCards.removeAll(chosen);
                cur.setHandCards(handCards);

                Play play = new Play();
                play.setPlayerId(cur.getPlayerId());
                play.setCards(chosen);

                state.setLastPlay(play);

                lastWinnerId = cur.getPlayerId();
                consecutivePassCount = 0;
                state.setOpeningTurn(false);

                metrics.totalPlays[currentIdx]++;

                if (hasBomb(chosen)) {
                    metrics.bombUsage[currentIdx]++;
                }

                if (hasTwo(chosen)) {
                    metrics.twoUsage[currentIdx]++;
                }

                if (state.getLastPlay() != null && state.getLastPlay().getCards() != null) {
                    metrics.suppressOpportunities[currentIdx]++;
                    metrics.suppressSuccesses[currentIdx]++;
                }
            }

            if (cur.getHandCards().isEmpty()) {
                state.setGameOver(true);
                break;
            }

            currentIdx = (currentIdx + 1) % 4;
        }

        List<Player> sorted = new ArrayList<>(players);
        sorted.sort(Comparator.comparingInt(p -> p.getHandCards().size()));

        int[] scores = new int[4];
        for (int i = 0; i < 4; i++) {
            scores[players.indexOf(sorted.get(i))] = 3 - i;
        }

        return new GameResult(scores, metrics);
    }

    public static int[] playGame(com.example.cardgame.ai.AIDecisionStrategy[] strategies, List<Card>[] hands) {
        GameResult result = playGameWithMetrics(strategies, hands);
        return result.scores;
    }

    private static boolean hasBomb(List<Card> cards) {
        if (cards == null || cards.size() != 4) return false;
        for (Card c : cards) {
            if (c.getRank() != cards.get(0).getRank()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasTwo(List<Card> cards) {
        if (cards == null) return false;
        for (Card c : cards) {
            if (c.getRank() == Rank.TWO) return true;
        }
        return false;
    }

    private static Player findDiamondThreeOwner(List<Player> players) {
        for (Player p : players) {
            for (Card c : p.getHandCards()) {
                if (c.isThreeOfDiamonds()) return p;
            }
        }
        return players.get(0);
    }

    public static int[] playGameWithRankings(com.example.cardgame.ai.AIDecisionStrategy[] strategies, List<Card>[] hands) {
        return playGame(strategies, hands);
    }
}
