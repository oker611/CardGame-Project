package com.example.cardgame.ai;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.Play;
import java.util.List;

public interface ICandidateGenerator {
    void setPhaseManager(IPhaseManager phaseManager);
    void setProfile(AIPlayerProfile profile);
    List<Play> generate(List<Card> hand, Play lastPlay, boolean isFirstRound, boolean isFirstTurn);
}
