package com.example.cardgame.network;

public interface BluetoothEventListener {

    void onConnected(String deviceName, String deviceAddress);

    void onDisconnected(String reason);

    void onMessageSent(MessageType messageType, String summary);

    void onMessageReceived(MessageType messageType, String summary);

    void onError(String message, Exception exception);

    void onGameOver(String winnerId, String winnerName);
}