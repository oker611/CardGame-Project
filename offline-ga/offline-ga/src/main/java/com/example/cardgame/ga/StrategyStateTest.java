package com.example.cardgame.ga;

import com.example.cardgame.ai.OfflineMonteCarloStrategy;
import com.example.cardgame.model.*;
import java.util.*;

public class StrategyStateTest {
    public static void main(String[] args) {
        System.out.println("=== Strategy State Test ===");
        
        try {
            Chromosome chrom = new Chromosome(5, 3, 0.5, -1.0, 0.3, 0.8, 0.5, 0.5);
            OfflineMonteCarloStrategy strategy = new OfflineMonteCarloStrategy(chrom);
            
            int games = 5;
            for (int g = 0; g < games; g++) {
                System.out.println("\nGame " + (g+1) + ":");
                
                List<Card> deck = generateDeck();
                Collections.shuffle(deck);
                List<List<Card>> hands = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    hands.add(new ArrayList<>(deck.subList(i * 13, (i + 1) * 13)));
                }
                
                @SuppressWarnings("unchecked")
                List<Card>[] handsArray = hands.toArray(new List[4]);
                
                com.example.cardgame.ai.AIDecisionStrategy[] strategies = 
                    new com.example.cardgame.ai.AIDecisionStrategy[]{strategy, strategy, strategy, strategy};
                
                long start = System.currentTimeMillis();
                int[] scores = Tournament.playGame(strategies, handsArray);
                long end = System.currentTimeMillis();
                
                System.out.println("  Completed in " + (end - start) + " ms");
                System.out.println("  Scores: P1=" + scores[0] + ", P2=" + scores[1] + ", P3=" + scores[2] + ", P4=" + scores[3]);
            }
            
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
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
