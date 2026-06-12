package com.example.cardgame.ga;

public class SingleEvalTest {
    public static void main(String[] args) {
        System.out.println("=== Single Evaluation Test ===");
        
        try {
            Chromosome chrom = new Chromosome(5, 3, 0.5, -1.0, 0.3, 0.8, 0.5, 0.5);
            System.out.println("Created chromosome: numSamples=" + chrom.numSamples + ", topK=" + chrom.topKCandidates);
            
            System.out.println("Starting evaluation...");
            long start = System.currentTimeMillis();
            
            double fitness = FitnessEvaluator.evaluate(chrom);
            
            long end = System.currentTimeMillis();
            System.out.println("Evaluation completed in " + (end - start) + " ms");
            System.out.println("Fitness: " + fitness);
            
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
