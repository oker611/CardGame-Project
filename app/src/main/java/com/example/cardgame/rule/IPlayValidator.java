package com.example.cardgame.rule;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.Player;
import java.util.List;

public interface IPlayValidator {
    PlayValidator.ValidationResult validatePlay(List<Card> currentCards,
            List<Card> lastPlayCards, boolean isFirstRound, boolean isFirstTurn);
    boolean isValidPattern(List<Card> cards);
    boolean hasAnyValidPlay(Player player, List<Card> lastPlay,
            boolean isFirstRound, boolean isFirstTurn);
}
