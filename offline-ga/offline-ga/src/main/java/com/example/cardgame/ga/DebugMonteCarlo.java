package com.example.cardgame.ga;

import com.example.cardgame.ai.OfflineMonteCarloStrategy;
import com.example.cardgame.model.*;
import java.util.*;

public class DebugMonteCarlo {
    public static void main(String[] args) {
        System.out.println("=== Debug MonteCarlo Strategy ===");
        
        try {
            // Create a simple chromosome with small values
            Chromosome chrom = new Chromosome(5, 3, 0.5, -1.0, 0.3, 0.8, 0.5, 0.5);
            System.out.println("1. Created chromosome: numSamples=" + chrom.numSamples + ", topK=" + chrom.topKCandidates);
            
            // Create a small hand for testing
            List<Card> hand = new ArrayList<>();
            hand.add(new Card("DIAMONDS_THREE", Suit.DIAMONDS, Rank.THREE));
            hand.add(new Card("HEARTS_FOUR", Suit.HEARTS, Rank.FOUR));
            hand.add(new Card("CLUBS_FIVE", Suit.CLUBS, Rank.FIVE));
            hand.add(new Card("SPADES_SIX", Suit.SPADES, Rank.SIX));
            hand.add(new Card("DIAMONDS_SEVEN", Suit.DIAMONDS, Rank.SEVEN));
            System.out.println("2. Created test hand with " + hand.size() + " cards");
            
            // Create player
            Player player = new Player("P1", "AI1");
            player.setHandCards(hand);
            System.out.println("3. Created player: " + player.getPlayerId());
            
            // Create game state
            GameState state = new GameState();
            state.setPlayers(Collections.singletonList(player));
            state.setCurrentPlayerId(player.getPlayerId());
            state.setOpeningTurn(true);
            state.setGameOver(false);
            System.out.println("4. Created game state");
            
            // Test the strategy
            System.out.println("\n5. Testing OfflineMonteCarloStrategy.decidePlay()...");
            OfflineMonteCarloStrategy strategy = new OfflineMonteCarloStrategy(chrom);
            
            long start = System.currentTimeMillis();
            List<Card> decision = strategy.decidePlay(player, state);
            long end = System.currentTimeMillis();
            
            System.out.println("   Decision time: " + (end - start) + " ms");
            System.out.println("   Decision: " + (decision == null ? "Pass" : decision.size() + " cards"));
            
            if (decision != null) {
                System.out.println("   Cards: " + decision);
            }
            
            System.out.println("\n=== Debug completed ===");
            
        } catch (Exception e) {
            System.out.println("\n=== ERROR ===");
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
