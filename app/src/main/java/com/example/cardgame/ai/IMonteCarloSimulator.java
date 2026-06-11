package com.example.cardgame.ai;

import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.model.Player;
import java.util.Map;

public interface IMonteCarloSimulator {
    void setOpponentProfiles(Map<String, AIPlayerProfile> profiles);
    double evaluate(Play candidate, Player aiPlayer, GameState currentState,
                    java.util.List<OpponentHandSampler.World> worlds);
}
