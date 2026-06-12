package com.example.cardgame.ga;

public class MiniTest {
    public static void main(String[] args) {
        System.out.println("=== Mini Genetic Algorithm Test ===");
        
        try {
            // Very small parameters for quick testing
            GeneticAlgorithm ga = new GeneticAlgorithm(3, 0.1, 0.8, 1);
            
            Chromosome best = ga.run(2);
            
            System.out.println("\nOptimization completed!");
            System.out.println("Best fitness: " + FitnessEvaluator.evaluate(best));
            System.out.println("\nOptimized Chromosome:");
            System.out.println("  numSamples: " + best.numSamples);
            System.out.println("  topKCandidates: " + best.topKCandidates);
            System.out.println("  earlyBigCardBonus: " + best.earlyBigCardBonus);
            System.out.println("  fiveCardPenalty: " + best.fiveCardPenalty);
            
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
