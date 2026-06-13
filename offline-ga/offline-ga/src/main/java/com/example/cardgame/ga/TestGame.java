package com.example.cardgame.ga;

import com.example.cardgame.model.*;
import java.util.*;

public class TestGame {
    public static void main(String[] args) {
        System.out.println("Testing Game Simulation...");
        
        try {
            // Create a simple chromosome
            Chromosome chrom = Chromosome.randomChromosome();
            System.out.println("Chromosome created: numSamples=" + chrom.numSamples);
            
            // Test single game evaluation
            System.out.println("\nEvaluating single game...");
            double score = FitnessEvaluator.evaluate(chrom);
            System.out.println("Score: " + score);
            
            System.out.println("\nGame simulation completed successfully!");
        } catch (Exception e) {
            System.out.println("Error during game simulation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
