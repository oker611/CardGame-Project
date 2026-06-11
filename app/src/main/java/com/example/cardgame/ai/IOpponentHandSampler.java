package com.example.cardgame.ai;

import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.util.CardTracker;
import java.util.List;

public interface IOpponentHandSampler {
    List<OpponentHandSampler.World> sampleWorlds(Player aiPlayer, GameState gameState,
                                                  CardTracker cardTracker, int numSamples);
}
