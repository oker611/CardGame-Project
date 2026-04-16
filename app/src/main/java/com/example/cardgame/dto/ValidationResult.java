package com.example.cardgame.dto;

import com.example.cardgame.model.CardPattern;

public class ValidationResult {

    private boolean valid;
    private String message;
    private CardPattern pattern;

    public ValidationResult(boolean valid, String message, CardPattern pattern) {
        this.valid = valid;
        this.message = message;
        this.pattern = pattern;
    }

    public boolean isValid() {
        return valid;
    }

    public String getMessage() {
        return message;
    }

    public CardPattern getPattern() {
        return pattern;
    }
}