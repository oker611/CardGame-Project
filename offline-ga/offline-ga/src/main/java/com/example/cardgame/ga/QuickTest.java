package com.example.cardgame.ga;

public class QuickTest {
    public static void main(String[] args) {
        System.out.println("=== Quick Genetic Algorithm Test ===");
        
        try {
            // Very small parameters for quick testing
            GeneticAlgorithm ga = new GeneticAlgorithm(2, 0.1, 0.8, 1);
            
            Chromosome best = ga.run(2);
            
            System.out.println("\nOptimization completed!");
            System.out.println("Best fitness: " + FitnessEvaluator.evaluate(best));
            System.out.println("\nOptimized Chromosome:");
            System.out.println("  numSamples: " + best.numSamples);
            System.out.println("  topKCandidates: " + best.topKCandidates);
            
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
