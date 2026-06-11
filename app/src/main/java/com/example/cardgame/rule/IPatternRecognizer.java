package com.example.cardgame.rule;

import com.example.cardgame.model.Card;
import java.util.List;

public interface IPatternRecognizer {
    PatternRecognizer.PatternInfo recognizePattern(List<Card> cards);
}
