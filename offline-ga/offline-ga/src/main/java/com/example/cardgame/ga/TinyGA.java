package com.example.cardgame.ga;

public class TinyGA {
    public static void main(String[] args) {
        System.out.println("=== Tiny Genetic Algorithm Test ===");

        GeneticAlgorithm ga = new GeneticAlgorithm(2, 0.1, 0.8, 1);

        long start = System.currentTimeMillis();
        Chromosome best = ga.run(2);
        long end = System.currentTimeMillis();

        System.out.println("\nTotal time: " + (end - start) + " ms");
        System.out.println("Best fitness: " + FitnessEvaluator.evaluate(best));
        System.out.println("numSamples=" + best.numSamples + ", topK=" + best.topKCandidates);
    }
}
