package com.example.cardgame.controller;

import com.example.cardgame.dto.BluetoothViewData;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.util.CardTracker;

/**
 * 蓝牙操作处理接口（HOST + CLIENT 通用）。
 *
 * HOST 专用方法通过 extends BluetoothHostActions 继承。
 * 遵循 ISP：CLIENT 不被迫实现 HOST 方法。
 */
public interface BluetoothActionHandler extends BluetoothHostActions {

    void createBluetoothRoom(String localPlayerId);

    void searchBluetoothDevices();

    void connectToDevice(String localPlayerId, String deviceAddress);

    void disconnectBluetooth();

    void sendLocalPlay(Play play);

    void sendLocalPass(String playerId);

    void syncGameState(GameState gameState);

    void sendGameOver(String winnerId, String winnerName);

    BluetoothViewData getBluetoothViewData();

    /** 设置本地记牌器实例（HOST+CLIENT 均需调用） */
    default void setCardTracker(CardTracker cardTracker) {}
}
