package com.example.cardgame.controller;

import com.example.cardgame.dto.BluetoothViewData;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;

public interface BluetoothActionHandler {

    void createBluetoothRoom(String localPlayerId);

    void searchBluetoothDevices();

    void connectToDevice(String localPlayerId, String deviceAddress);

    void disconnectBluetooth();

    void sendLocalPlay(Play play);

    void sendLocalPass(String playerId);

    void syncGameState(GameState gameState);

    BluetoothViewData getBluetoothViewData();
}