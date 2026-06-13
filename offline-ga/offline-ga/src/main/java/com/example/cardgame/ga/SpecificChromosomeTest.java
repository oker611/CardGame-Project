package com.example.cardgame.ga;

public class SpecificChromosomeTest {
    public static void main(String[] args) {
        System.out.println("=== Specific Chromosome Test ===");
        
        // This is the chromosome that caused the issue
        Chromosome chrom = new Chromosome(7, 6, 0.8929251499450651, -0.6503474576321002, 
                                         0.2436492382218675, 0.6667124791530531, 
                                         0.47475331052010605, 0.29527955524895144);
        
        System.out.println("Testing chromosome:");
        System.out.println("  numSamples: " + chrom.numSamples);
        System.out.println("  topKCandidates: " + chrom.topKCandidates);
        
        System.out.print("Evaluating... ");
        long start = System.currentTimeMillis();
        double fitness = FitnessEvaluator.evaluate(chrom);
        long end = System.currentTimeMillis();
        System.out.println("Fitness: " + fitness + " (" + (end - start) + " ms)");
    }
}
