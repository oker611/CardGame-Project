package com.example.cardgame.ga;

public class MultiEvalTest {
    public static void main(String[] args) {
        System.out.println("=== Multiple Evaluation Test ===");
        
        try {
            Chromosome chrom = new Chromosome(5, 3, 0.5, -1.0, 0.3, 0.8, 0.5, 0.5);
            System.out.println("Created chromosome: numSamples=" + chrom.numSamples + ", topK=" + chrom.topKCandidates);
            
            int evals = 3;
            for (int i = 0; i < evals; i++) {
                System.out.println("\nStarting evaluation " + (i+1) + "...");
                long start = System.currentTimeMillis();
                
                double fitness = FitnessEvaluator.evaluate(chrom);
                
                long end = System.currentTimeMillis();
                System.out.println("Evaluation " + (i+1) + " completed in " + (end - start) + " ms");
                System.out.println("Fitness: " + fitness);
            }
            
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
