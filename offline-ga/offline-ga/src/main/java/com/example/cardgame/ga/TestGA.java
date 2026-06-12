package com.example.cardgame.ga;

public class TestGA {
    public static void main(String[] args) {
        System.out.println("Testing Genetic Algorithm basic functionality...");
        
        // Test Chromosome generation
        Chromosome c = Chromosome.randomChromosome();
        System.out.println("Created random chromosome:");
        System.out.println("  numSamples: " + c.numSamples);
        System.out.println("  aggression: " + c.aggression);
        
        // Test crossover
        Chromosome c2 = Chromosome.randomChromosome();
        Chromosome child = Chromosome.crossover(c, c2);
        System.out.println("\nCrossover result:");
        System.out.println("  numSamples: " + child.numSamples);
        
        // Test mutation
        child.mutate(0.5);
        System.out.println("\nAfter mutation:");
        System.out.println("  numSamples: " + child.numSamples);
        
        System.out.println("\nBasic tests passed!");
    }
}
