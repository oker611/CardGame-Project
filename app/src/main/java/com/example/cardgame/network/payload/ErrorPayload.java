package com.example.cardgame.network.payload;

public class ErrorPayload {

    private String errorCode;
    private String errorMessage;

    public ErrorPayload(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}