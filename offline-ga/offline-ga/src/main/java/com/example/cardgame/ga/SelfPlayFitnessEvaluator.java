package com.example.cardgame.ga;

import com.example.cardgame.ai.AIDecisionStrategy;
import com.example.cardgame.ai.OfflineMonteCarloStrategy;
import com.example.cardgame.model.*;
import java.util.*;
import java.util.concurrent.*;

public class SelfPlayFitnessEvaluator {
    private static final int GAMES_PER_MATCHUP = 2;
    private static final int SEED_BASE = 12345;
    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();

    public static FitnessResult[] evaluatePopulation(List<Chromosome> population) {
        int n = population.size();
        double[][] matchResults = new double[n][n];
        double[][] aggressionScores = new double[n][n];
        double[][] bombScores = new double[n][n];
        double[][] twoScores = new double[n][n];
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                final int fi = i, fj = j;
                futures.add(executor.submit(() -> {
                    double totalScore = 0;
                    double totalAggression = 0;
                    double totalBomb = 0;
                    double totalTwo = 0;
                    for (int g = 0; g < GAMES_PER_MATCHUP; g++) {
                        long seed = SEED_BASE + fi * n + fj + g * 1000;
                        MatchResult result = playMatch(population.get(fi), population.get(fj), seed);
                        totalScore += result.score;
                        totalAggression += result.aggression;
                        totalBomb += result.bombUsage;
                        totalTwo += result.twoUsage;
                    }
                    double avgScore = totalScore / GAMES_PER_MATCHUP;
                    matchResults[fi][fj] = avgScore;
                    matchResults[fj][fi] = 4.0 - avgScore;
                    aggressionScores[fi][fj] = totalAggression / GAMES_PER_MATCHUP;
                    aggressionScores[fj][fi] = totalAggression / GAMES_PER_MATCHUP;
                    bombScores[fi][fj] = totalBomb / GAMES_PER_MATCHUP;
                    bombScores[fj][fi] = totalBomb / GAMES_PER_MATCHUP;
                    twoScores[fi][fj] = totalTwo / GAMES_PER_MATCHUP;
                    twoScores[fj][fi] = totalTwo / GAMES_PER_MATCHUP;
                    return null;
                }));
            }
        }

        for (Future<Void> f : futures) {
            try { f.get(30, TimeUnit.SECONDS); } catch (Exception e) { e.printStackTrace(); }
        }
        executor.shutdown();

        FitnessResult[] results = new FitnessResult[n];
        for (int i = 0; i < n; i++) {
            double totalScore = 0;
            double totalAggression = 0;
            double totalBomb = 0;
            double totalTwo = 0;
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    totalScore += matchResults[i][j];
                    totalAggression += aggressionScores[i][j];
                    totalBomb += bombScores[i][j];
                    totalTwo += twoScores[i][j];
                }
            }
            double avgScore = totalScore / (n - 1);
            double avgAggression = totalAggression / (n - 1);
            double avgBomb = totalBomb / (n - 1);
            double avgTwo = totalTwo / (n - 1);
            int rank = 1;
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    double oppAvg = 0;
                    for (int k = 0; k < n; k++) if (j != k) oppAvg += matchResults[j][k];
                    oppAvg /= (n - 1);
                    if (oppAvg > avgScore) rank++;
                }
            }
            results[i] = new FitnessResult(population.get(i), avgScore, avgAggression, avgBomb, avgTwo, rank, n);
        }
        return results;
    }

    private static class MatchResult {
        double score;
        double aggression;
        double bombUsage;
        double twoUsage;

        MatchResult(double score, double aggression, double bombUsage, double twoUsage) {
            this.score = score;
            this.aggression = aggression;
            this.bombUsage = bombUsage;
            this.twoUsage = twoUsage;
        }
    }

    private static MatchResult playMatch(Chromosome c1, Chromosome c2, long seed) {
        Random rng = new Random(seed);

        Chromosome c1_fast = new Chromosome(c1.numSamples, c1.topKCandidates, c1.earlyBigCardBonus,
                c1.fiveCardPenalty, c1.midSuppressBonus, c1.lateFastBonus, c1.aggression, c1.defense,
                c1.lateTwoBonus, c1.straightKeepBonus, c1.midControlThreshold, c1.simulatedAggression);
        c1_fast.numSamples = 3;

        Chromosome c2_fast = new Chromosome(c2.numSamples, c2.topKCandidates, c2.earlyBigCardBonus,
                c2.fiveCardPenalty, c2.midSuppressBonus, c2.lateFastBonus, c2.aggression, c2.defense,
                c2.lateTwoBonus, c2.straightKeepBonus, c2.midControlThreshold, c2.simulatedAggression);
        c2_fast.numSamples = 3;

        AIDecisionStrategy s1 = new OfflineMonteCarloStrategy(c1_fast);
        AIDecisionStrategy s2 = new OfflineMonteCarloStrategy(c2_fast);
        int c1Pos = rng.nextInt(2) * 2;
        AIDecisionStrategy[] strategies = new AIDecisionStrategy[4];
        strategies[c1Pos] = s1;
        strategies[(c1Pos + 2) % 4] = s1;
        strategies[(c1Pos + 1) % 4] = s2;
        strategies[(c1Pos + 3) % 4] = s2;

        List<Card> deck = generateDeck();
        Collections.shuffle(deck, rng);
        @SuppressWarnings("unchecked")
        List<Card>[] hands = new List[4];
        for (int i = 0; i < 4; i++) {
            hands[i] = new ArrayList<>(deck.subList(i * 13, (i + 1) * 13));
        }
        Tournament.GameResult result = Tournament.playGameWithMetrics(strategies, hands);
        double avgScore = (result.scores[c1Pos] + result.scores[(c1Pos + 2) % 4]) / 2.0;

        double avgAggression1 = result.metrics.getSuppressRate(c1Pos);
        double avgAggression2 = result.metrics.getSuppressRate((c1Pos + 2) % 4);
        double avgAggression = (avgAggression1 + avgAggression2) / 2.0;

        double avgBomb1 = result.metrics.getBombRate(c1Pos);
        double avgBomb2 = result.metrics.getBombRate((c1Pos + 2) % 4);
        double avgBomb = (avgBomb1 + avgBomb2) / 2.0;

        double avgTwo1 = result.metrics.getTwoRate(c1Pos);
        double avgTwo2 = result.metrics.getTwoRate((c1Pos + 2) % 4);
        double avgTwo = (avgTwo1 + avgTwo2) / 2.0;

        return new MatchResult(avgScore, avgAggression, avgBomb, avgTwo);
    }

    private static List<Card> generateDeck() {
        List<Card> deck = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(suit.name() + "_" + rank.name(), suit, rank));
            }
        }
        return deck;
    }

    public static class FitnessResult {
        public final Chromosome chromosome;
        public final double avgScore;
        public final double avgAggression;
        public final double avgBomb;
        public final double avgTwo;
        public final int rank;
        public final int totalPlayers;

        public FitnessResult(Chromosome c, double score, double aggression, double bomb, double two, int r, int total) {
            this.chromosome = c;
            this.avgScore = score;
            this.avgAggression = aggression;
            this.avgBomb = bomb;
            this.avgTwo = two;
            this.rank = r;
            this.totalPlayers = total;
        }

        public double getFitness() {
            double rankScore = (totalPlayers - rank + 1) / (double) totalPlayers;
            double normalizedScore = avgScore / 3.0;
            double aggressionBonus = avgAggression * 0.3;
            double bombBonus = avgBomb * 0.2;
            double twoBonus = avgTwo * 0.1;
            return 0.4 * rankScore + 0.3 * normalizedScore + aggressionBonus + bombBonus + twoBonus;
        }
    }
}
