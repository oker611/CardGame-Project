package com.example.cardgame.ga;

import com.example.cardgame.ai.OfflineMonteCarloStrategy;
import com.example.cardgame.model.*;
import java.util.*;

public class DebugGame {
    public static void main(String[] args) {
        System.out.println("=== Debug Game Simulation ===");
        
        try {
            // Step 1: Create a simple chromosome
            Chromosome chrom = new Chromosome(10, 5, 0.5, -1.0, 0.3, 0.8, 0.5, 0.5);
            System.out.println("1. Created chromosome: numSamples=" + chrom.numSamples + ", topK=" + chrom.topKCandidates);
            
            // Step 2: Create a simple game state with 4 players
            System.out.println("\n2. Creating game state...");
            List<Card> deck = generateDeck();
            Collections.shuffle(deck);
            List<List<Card>> hands = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                hands.add(new ArrayList<>(deck.subList(i * 13, (i + 1) * 13)));
                System.out.println("   Player " + (i+1) + " hand size: " + hands.get(i).size());
            }
            
            // Step 3: Create players
            System.out.println("\n3. Creating players...");
            List<Player> players = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Player p = new Player("P" + (i + 1), "AI" + (i + 1));
                p.setHandCards(new ArrayList<>(hands.get(i)));
                players.add(p);
                System.out.println("   Player " + p.getPlayerId() + " created");
            }
            
            // Step 4: Find diamond three owner
            System.out.println("\n4. Finding diamond three owner...");
            Player starter = findDiamondThreeOwner(players);
            System.out.println("   Starter: " + starter.getPlayerId());
            
            // Step 5: Create game state
            GameState state = new GameState();
            state.setPlayers(players);
            state.setCurrentPlayerId(starter.getPlayerId());
            state.setOpeningTurn(true);
            state.setGameOver(false);
            
            // Step 6: Test AI decision
            System.out.println("\n5. Testing AI decision...");
            OfflineMonteCarloStrategy strategy = new OfflineMonteCarloStrategy(chrom);
            Player current = state.getCurrentPlayer();
            
            System.out.println("   Current player: " + current.getPlayerId());
            System.out.println("   Hand size: " + current.getHandCards().size());
            System.out.println("   Is first round: " + state.isOpeningTurn());
            
            List<Card> decision = strategy.decidePlay(current, state);
            System.out.println("   Decision: " + (decision == null ? "Pass" : decision.size() + " cards"));
            
            if (decision != null && !decision.isEmpty()) {
                System.out.println("   Cards played: " + decision);
            }
            
            System.out.println("\n=== Debug completed successfully ===");
            
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
    
    private static Player findDiamondThreeOwner(List<Player> players) {
        for (Player p : players) {
            for (Card c : p.getHandCards()) {
                if (c.isThreeOfDiamonds()) return p;
            }
        }
        return players.get(0);
    }
}
