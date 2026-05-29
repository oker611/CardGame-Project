package com.example.cardgame.rule;

import com.example.cardgame.model.Card;

import java.util.List;

public interface RuleEngine {

    PatternRecognizer.PatternInfo recognizePattern(List<Card> cards);

    PlayValidator.ValidationResult validatePlay(List<Card> cardsToPlay,
                                                List<Card> lastPlayCards,
                                                boolean isFirstRound,
                                                boolean isFirstTurn);
}