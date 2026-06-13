package com.example.cardgame.ga;

public class RandomChromosomeTest {
    public static void main(String[] args) {
        System.out.println("=== Random Chromosome Test ===");
        
        for (int i = 0; i < 10; i++) {
            Chromosome chrom = Chromosome.randomChromosome();
            System.out.println("\nChromosome " + (i+1) + ":");
            System.out.println("  numSamples: " + chrom.numSamples);
            System.out.println("  topKCandidates: " + chrom.topKCandidates);
            System.out.println("  earlyBigCardBonus: " + chrom.earlyBigCardBonus);
            System.out.println("  fiveCardPenalty: " + chrom.fiveCardPenalty);
            System.out.println("  midSuppressBonus: " + chrom.midSuppressBonus);
            System.out.println("  lateFastBonus: " + chrom.lateFastBonus);
            System.out.println("  aggression: " + chrom.aggression);
            System.out.println("  defense: " + chrom.defense);
            
            // Test evaluation
            System.out.print("  Evaluating... ");
            long start = System.currentTimeMillis();
            double fitness = FitnessEvaluator.evaluate(chrom);
            long end = System.currentTimeMillis();
            System.out.println("Fitness: " + fitness + " (" + (end - start) + " ms)");
        }
    }
}
