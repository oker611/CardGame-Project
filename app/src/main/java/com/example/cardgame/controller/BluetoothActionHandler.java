package com.example.cardgame.controller;

import com.example.cardgame.dto.BluetoothViewData;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;

import java.util.ArrayList;
import java.util.List;

public interface BluetoothActionHandler {

    void createBluetoothRoom(String localPlayerId);

    void searchBluetoothDevices();

    void connectToDevice(String localPlayerId, String deviceAddress);

    void disconnectBluetooth();

    void sendLocalPlay(Play play);

    void sendLocalPass(String playerId);

    void syncGameState(GameState gameState);

    void sendGameOver(String winnerId, String winnerName);

    BluetoothViewData getBluetoothViewData();

    /**
     * 通信通道是否已就绪（所有客户端均已连接并建立通道）。
     */
    default boolean isCommunicationReady() {
        return false;
    }

    /**
     * 获取当前已连接的所有远程玩家 ID 列表。
     * HOST 端返回 P2/P3/P4 等已分配的 ID，CLIENT 端返回空列表。
     */
    default List<String> getRemotePlayerIds() {
        return new ArrayList<>();
    }
}