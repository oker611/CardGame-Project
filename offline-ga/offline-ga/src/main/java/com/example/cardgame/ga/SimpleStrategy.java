package com.example.cardgame.ga;

import com.example.cardgame.model.*;
import java.util.*;

public class SimpleStrategy implements com.example.cardgame.ai.AIDecisionStrategy {
    @Override
    public List<Card> decidePlay(Player aiPlayer, GameState gameState) {
        List<Card> hand = aiPlayer.getHandCards();
        if (hand.isEmpty()) return null;
        
        Play lastPlay = gameState.getLastPlay();
        boolean isFirstTurn = (lastPlay == null || lastPlay.isEmpty());
        
        if (isFirstTurn) {
            // First turn: play smallest card
            Card min = hand.stream().min(Comparator.comparingInt(c -> c.getRank().getWeight())).orElse(null);
            return min != null ? Collections.singletonList(min) : null;
        } else {
            // Try to play smallest card that beats last play
            List<Card> lastCards = lastPlay.getCards();
            if (lastCards.size() == 1) {
                Card lastCard = lastCards.get(0);
                Card best = null;
                for (Card c : hand) {
                    if (c.getRank().getWeight() > lastCard.getRank().getWeight()) {
                        if (best == null || c.getRank().getWeight() < best.getRank().getWeight()) {
                            best = c;
                        }
                    }
                }
                return best != null ? Collections.singletonList(best) : null;
            } else {
                // For multi-card plays, just play smallest single
                Card min = hand.stream().min(Comparator.comparingInt(c -> c.getRank().getWeight())).orElse(null);
                return min != null ? Collections.singletonList(min) : null;
            }
        }
    }
}
