package com.example.cardgame.network;

public class BluetoothMessage {

    private String messageId;
    private int protocolVersion;
    private MessageType messageType;
    private String senderPlayerId;
    private String receiverPlayerId;
    private long timestamp;
    private String payloadJson;
    private String errorMessage;

    public BluetoothMessage(
            String messageId,
            int protocolVersion,
            MessageType messageType,
            String senderPlayerId,
            String receiverPlayerId,
            long timestamp,
            String payloadJson,
            String errorMessage
    ) {
        this.messageId = messageId;
        this.protocolVersion = protocolVersion;
        this.messageType = messageType;
        this.senderPlayerId = senderPlayerId;
        this.receiverPlayerId = receiverPlayerId;
        this.timestamp = timestamp;
        this.payloadJson = payloadJson;
        this.errorMessage = errorMessage;
    }

    public String getMessageId() {
        return messageId;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public String getSenderPlayerId() {
        return senderPlayerId;
    }

    public String getReceiverPlayerId() {
        return receiverPlayerId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}