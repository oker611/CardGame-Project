package com.example.cardgame.network.payload;

/** ACK 消息 payload，确认收到指定序列号的消息。 */
public class AckPayload {

    private int acknowledgedSeq;

    public AckPayload() {}

    public AckPayload(int acknowledgedSeq) {
        this.acknowledgedSeq = acknowledgedSeq;
    }

    public int getAcknowledgedSeq() {
        return acknowledgedSeq;
    }

    public void setAcknowledgedSeq(int acknowledgedSeq) {
        this.acknowledgedSeq = acknowledgedSeq;
    }
}
