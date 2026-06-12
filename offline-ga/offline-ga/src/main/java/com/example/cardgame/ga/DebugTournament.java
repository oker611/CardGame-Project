package com.example.cardgame.ga;

import com.example.cardgame.ai.OfflineMonteCarloStrategy;
import com.example.cardgame.model.*;
import java.util.*;

public class DebugTournament {
    public static void main(String[] args) {
        System.out.println("=== Debug Tournament ===");
        
        try {
            // Create a simple chromosome
            Chromosome chrom = new Chromosome(5, 3, 0.5, -1.0, 0.3, 0.8, 0.5, 0.5);
            System.out.println("1. Created chromosome: numSamples=" + chrom.numSamples + ", topK=" + chrom.topKCandidates);
            
            // Create deck and hands
            List<Card> deck = generateDeck();
            Collections.shuffle(deck);
            List<List<Card>> hands = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                hands.add(new ArrayList<>(deck.subList(i * 13, (i + 1) * 13)));
                System.out.println("   Player " + (i+1) + " hand size: " + hands.get(i).size());
            }
            
            // Create strategies
            OfflineMonteCarloStrategy strategy = new OfflineMonteCarloStrategy(chrom);
            
            @SuppressWarnings("unchecked")
            List<Card>[] handsArray = hands.toArray(new List[4]);
            
            com.example.cardgame.ai.AIDecisionStrategy[] strategies = 
                new com.example.cardgame.ai.AIDecisionStrategy[]{strategy, strategy, strategy, strategy};
            
            System.out.println("\n2. Starting game...");
            long start = System.currentTimeMillis();
            
            int[] scores = Tournament.playGame(strategies, handsArray);
            
            long end = System.currentTimeMillis();
            System.out.println("   Game completed in " + (end - start) + " ms");
            
            System.out.println("\n3. Scores:");
            for (int i = 0; i < scores.length; i++) {
                System.out.println("   Player " + (i+1) + ": " + scores[i] + " points");
            }
            
            System.out.println("\n=== Debug completed ===");
            
        } catch (Exception e) {
            System.out.println("\n=== ERROR ===");
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<Card> generateDeck() {
        List<Card> deck = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(suit.name() + "_" + rank.name(), suit, rank));
            }
        }
        return deck;
    }
}
