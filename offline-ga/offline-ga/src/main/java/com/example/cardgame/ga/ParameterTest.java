package com.example.cardgame.ga;

public class ParameterTest {
    public static void main(String[] args) {
        System.out.println("=== Parameter Combination Test ===");

        int[] sampleSizes = {1, 2, 3, 4, 5};
        int[] topKs = {1, 2, 3};

        for (int numSamples : sampleSizes) {
            for (int topK : topKs) {
                Chromosome chrom = new Chromosome(numSamples, topK, 0.5, -1.0, 0.3, 0.8, 0.5, 0.5);
                System.out.println("Testing (numSamples=" + numSamples + ", topK=" + topK + ")... ");

                long start = System.currentTimeMillis();
                double fitness = FitnessEvaluator.evaluate(chrom);
                long end = System.currentTimeMillis();

                System.out.println("  Fitness: " + fitness + " (" + (end - start) + " ms)");
            }
        }
    }
}
