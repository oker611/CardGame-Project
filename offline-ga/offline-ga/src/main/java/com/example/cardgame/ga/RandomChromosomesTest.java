package com.example.cardgame.ga;

public class RandomChromosomesTest {
    public static void main(String[] args) {
        System.out.println("=== Testing Random Chromosomes ===");

        for (int i = 0; i < 20; i++) {
            Chromosome chrom = Chromosome.randomChromosome();
            System.out.println("Chromosome " + (i+1) + ": numSamples=" + chrom.numSamples + ", topK=" + chrom.topKCandidates);

            long start = System.currentTimeMillis();
            double fitness = FitnessEvaluator.evaluate(chrom);
            long end = System.currentTimeMillis();

            System.out.println("  Fitness: " + fitness + " (" + (end - start) + " ms)");
        }
    }
}
