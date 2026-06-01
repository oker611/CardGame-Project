package com.example.cardgame.rule;

import com.example.cardgame.model.Card;
import java.util.List;

public class ConfigurableRuleEngine implements RuleEngine {

    private final PatternRecognizer recognizer;
    private final PlayValidator validator;

    public ConfigurableRuleEngine(RuleConfig config) {
        this.recognizer = new PatternRecognizer(config);
        this.validator = new PlayValidator(config);
    }

    @Override
    public PatternRecognizer.PatternInfo recognizePattern(List<Card> cards) {
        return recognizer.recognizePattern(cards);
    }

    @Override
    public PlayValidator.ValidationResult validatePlay(List<Card> cardsToPlay,
                                                       List<Card> lastPlayCards,
                                                       boolean isFirstRound,
                                                       boolean isFirstTurn) {
        return validator.validatePlay(cardsToPlay, lastPlayCards, isFirstRound, isFirstTurn);
    }
}
