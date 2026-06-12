package com.example.cardgame.ga;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Main {
    private static final int POPULATION_SIZE = 6;
    private static final int GENERATIONS = 10;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Genetic Algorithm for DaDi AI Optimization");
        System.out.println("(Aggressive Version)");
        System.out.println("========================================");
        System.out.println("Configuration:");
        System.out.println("  Population Size: " + POPULATION_SIZE);
        System.out.println("  Generations: " + GENERATIONS);
        System.out.println("  Mutation Rate: 0.1");
        System.out.println("  Crossover Rate: 0.8");
        System.out.println("  Elitism Count: 1");
        System.out.println();
        System.out.println("Evaluation Mode: Self-play with numSamples=3");
        System.out.println("Fitness: 0.4*rank + 0.3*score + 0.3*aggression + 0.2*bomb + 0.1*two");
        System.out.println();
        System.out.println("Parameter Ranges:");
        System.out.println("  numSamples: 5-10");
        System.out.println("  topKCandidates: 3-5");
        System.out.println();
        System.out.println("Estimated time: 10-20 minutes");
        System.out.println("========================================");
        System.out.println();

        SelfPlayGeneticAlgorithm ga = new SelfPlayGeneticAlgorithm(
            POPULATION_SIZE,
            0.1,
            0.8,
            1
        );

        long start = System.currentTimeMillis();
        Chromosome best = ga.run(GENERATIONS);
        long end = System.currentTimeMillis();

        System.out.println("\n========================================");
        System.out.println("Optimization Completed!");
        System.out.println("========================================");
        System.out.println("Total time: " + (end - start) / 1000 + " seconds");
        System.out.println();
        System.out.println("Optimized Chromosome (12 parameters):");
        System.out.println("  numSamples: " + best.numSamples);
        System.out.println("  topKCandidates: " + best.topKCandidates);
        System.out.println("  earlyBigCardBonus: " + best.earlyBigCardBonus);
        System.out.println("  fiveCardPenalty: " + best.fiveCardPenalty);
        System.out.println("  midSuppressBonus: " + best.midSuppressBonus);
        System.out.println("  lateFastBonus: " + best.lateFastBonus);
        System.out.println("  aggression: " + best.aggression);
        System.out.println("  defense: " + best.defense);
        System.out.println("  lateTwoBonus: " + best.lateTwoBonus);
        System.out.println("  straightKeepBonus: " + best.straightKeepBonus);
        System.out.println("  midControlThreshold: " + best.midControlThreshold);
        System.out.println("  simulatedAggression: " + best.simulatedAggression);
        System.out.println("========================================");

        saveResults(best, end - start);
    }

    private static void saveResults(Chromosome best, long totalTime) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = "best_params_aggressive_" + timestamp + ".txt";

        try (FileWriter fw = new FileWriter(filename)) {
            fw.write("========================================\n");
            fw.write("Optimized AI Parameters (Aggressive Version)\n");
            fw.write("========================================\n");
            fw.write("Timestamp: " + new Date() + "\n");
            fw.write("Total optimization time: " + totalTime / 1000 + " seconds\n");
            fw.write("\n");
            fw.write("Best Chromosome (12 parameters):\n");
            fw.write("  numSamples: " + best.numSamples + "\n");
            fw.write("  topKCandidates: " + best.topKCandidates + "\n");
            fw.write("  earlyBigCardBonus: " + best.earlyBigCardBonus + "\n");
            fw.write("  fiveCardPenalty: " + best.fiveCardPenalty + "\n");
            fw.write("  midSuppressBonus: " + best.midSuppressBonus + "\n");
            fw.write("  lateFastBonus: " + best.lateFastBonus + "\n");
            fw.write("  aggression: " + best.aggression + "\n");
            fw.write("  defense: " + best.defense + "\n");
            fw.write("  lateTwoBonus: " + best.lateTwoBonus + "\n");
            fw.write("  straightKeepBonus: " + best.straightKeepBonus + "\n");
            fw.write("  midControlThreshold: " + best.midControlThreshold + "\n");
            fw.write("  simulatedAggression: " + best.simulatedAggression + "\n");
            fw.write("\n");
            fw.write("========================================\n");
            fw.write("Copy to Android MonteCarloAIDecisionStrategy.java:\n");
            fw.write("========================================\n");
            fw.write("private static final int NUM_SAMPLES = " + best.numSamples + ";\n");
            fw.write("private static final int TOP_K_CANDIDATES = " + best.topKCandidates + ";\n");
            fw.write("private static final double EARLY_BIG_CARD_BONUS = " + best.earlyBigCardBonus + ";\n");
            fw.write("private static final double FIVE_CARD_PENALTY = " + best.fiveCardPenalty + ";\n");
            fw.write("private static final double MID_SUPPRESS_BONUS = " + best.midSuppressBonus + ";\n");
            fw.write("private static final double LATE_FAST_BONUS = " + best.lateFastBonus + ";\n");
            fw.write("========================================\n");

            System.out.println("\nResults saved to: " + filename);
        } catch (IOException e) {
            System.err.println("Error saving results: " + e.getMessage());
        }
    }
}
