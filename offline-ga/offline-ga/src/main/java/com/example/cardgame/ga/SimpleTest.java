package com.example.cardgame.ga;

import com.example.cardgame.model.*;
import java.util.*;

public class SimpleTest {
    public static void main(String[] args) {
        System.out.println("=== Simple Game Test ===");
        
        try {
            // Create deck and hands
            List<Card> deck = generateDeck();
            Collections.shuffle(deck);
            List<List<Card>> hands = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                hands.add(new ArrayList<>(deck.subList(i * 13, (i + 1) * 13)));
            }
            
            // Create players
            List<Player> players = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Player p = new Player("P" + (i + 1), "AI" + (i + 1));
                p.setHandCards(new ArrayList<>(hands.get(i)));
                players.add(p);
            }
            
            // Find starter
            Player starter = findDiamondThreeOwner(players);
            System.out.println("Starter: " + starter.getPlayerId());
            
            // Create a simple rule-based AI strategy
            SimpleAI ai = new SimpleAI();
            
            // Play a simple game
            int currentIdx = players.indexOf(starter);
            int turns = 0;
            
            while (turns < 50) {
                Player cur = players.get(currentIdx);
                List<Card> chosen = ai.decide(cur);
                
                System.out.println("Turn " + (turns + 1) + ": Player " + cur.getPlayerId() + 
                    " (" + cur.getHandCards().size() + " cards) -> " + 
                    (chosen == null ? "Pass" : chosen.size() + " cards"));
                
                if (chosen != null && !chosen.isEmpty()) {
                    cur.getHandCards().removeAll(chosen);
                    
                    if (cur.getHandCards().isEmpty()) {
                        System.out.println("Player " + cur.getPlayerId() + " wins!");
                        break;
                    }
                }
                
                currentIdx = (currentIdx + 1) % 4;
                turns++;
            }
            
            System.out.println("\n=== Test completed ===");
            
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static class SimpleAI {
        public List<Card> decide(Player player) {
            List<Card> hand = player.getHandCards();
            if (hand.isEmpty()) return null;
            
            // Simple strategy: play smallest card
            Card min = hand.stream().min(Comparator.comparingInt(c -> c.getRank().getWeight())).orElse(null);
            return min != null ? Collections.singletonList(min) : null;
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
