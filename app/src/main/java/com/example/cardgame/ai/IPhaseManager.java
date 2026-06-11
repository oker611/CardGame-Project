package com.example.cardgame.ai;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.model.Player;
import java.util.List;

public interface IPhaseManager {
    PhaseManager.GamePhase getCurrentPhase(Player aiPlayer, GameState state);
    double adjustScore(double baseScore, Play candidate, PhaseManager.GamePhase phase,
                       Player aiPlayer, GameState state, List<Card> handAfterPlay);
    boolean shouldForceBeat(Player aiPlayer, GameState state, Play lastPlay);
    double getAggression();
    double getDefense();
    boolean wouldBreakBigPattern(List<Card> originalHand, List<Card> remainingHand);
    boolean hasBigPattern(List<Card> hand);
    List<Card> findBeatForBigTwoPattern(Player aiPlayer, Play lastPlay);
}
