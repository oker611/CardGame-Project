package com.example.cardgame.ai;

import com.example.cardgame.ga.Chromosome;
import com.example.cardgame.model.*;
import java.util.*;

public class StrongAIDecisionStrategy implements AIDecisionStrategy {
    private final OfflineMonteCarloStrategy delegate;
    
    public StrongAIDecisionStrategy() {
        // 使用较大的参数，创建一个强对手
        Chromosome strong = new Chromosome();
        strong.numSamples = 15;
        strong.topKCandidates = 8;
        strong.earlyBigCardBonus = 0.5;
        strong.fiveCardPenalty = -2.0;
        strong.midSuppressBonus = 0.5;
        strong.lateFastBonus = 0.5;
        strong.aggression = 0.7;
        strong.defense = 0.3;
        this.delegate = new OfflineMonteCarloStrategy(strong);
    }
    
    @Override
    public List<Card> decidePlay(Player aiPlayer, GameState gameState) {
        return delegate.decidePlay(aiPlayer, gameState);
    }
}