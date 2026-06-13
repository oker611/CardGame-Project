package com.example.cardgame.ga;

public class GeneticOpTest {
    public static void main(String[] args) {
        System.out.println("=== Testing Genetic Operations ===");

        for (int i = 0; i < 20; i++) {
            Chromosome p1 = Chromosome.randomChromosome();
            Chromosome p2 = Chromosome.randomChromosome();

            System.out.println("Parent1: numSamples=" + p1.numSamples + ", topK=" + p1.topKCandidates);
            System.out.println("Parent2: numSamples=" + p2.numSamples + ", topK=" + p2.topKCandidates);

            Chromosome child = Chromosome.crossover(p1, p2);
            System.out.println("Child (before mutate): numSamples=" + child.numSamples + ", topK=" + child.topKCandidates);

            child.mutate(0.1);
            System.out.println("Child (after mutate): numSamples=" + child.numSamples + ", topK=" + child.topKCandidates);

            System.out.println("Testing child...");
            long start = System.currentTimeMillis();
            double fitness = FitnessEvaluator.evaluate(child);
            long end = System.currentTimeMillis();
            System.out.println("  Fitness: " + fitness + " (" + (end - start) + " ms)");
            System.out.println();
        }
    }
}
