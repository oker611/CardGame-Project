package com.example.cardgame.network.payload;

public class AckPayload {
    private String acknowledgedMessageId;

    public AckPayload() {}

    public AckPayload(String acknowledgedMessageId) {
        this.acknowledgedMessageId = acknowledgedMessageId;
    }

    public String getAcknowledgedMessageId() {
        return acknowledgedMessageId;
    }

    public void setAcknowledgedMessageId(String acknowledgedMessageId) {
        this.acknowledgedMessageId = acknowledgedMessageId;
    }
}
