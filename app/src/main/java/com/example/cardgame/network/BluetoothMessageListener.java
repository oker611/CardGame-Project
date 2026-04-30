package com.example.cardgame.network;

public interface BluetoothMessageListener {

    void onMessageReceived(BluetoothMessage message);

    void onReceiveError(Exception exception);
}