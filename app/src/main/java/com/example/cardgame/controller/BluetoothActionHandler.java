package com.example.cardgame.controller;

import com.example.cardgame.dto.BluetoothViewData;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * 获取当前已连接的所有远程玩家 ID 列表。
     * HOST 端返回 P2/P3/P4 等已分配的 ID，CLIENT 端返回空列表。
     */
    default List<String> getRemotePlayerIds() {
        return new ArrayList<>();
    }

    default Map<String, String> getPlayerNamesById() {
        return new HashMap<>();
    }

    /**
     * HOST 端：通知蓝牙网关房间已准备好开始游戏（AI 玩家已补齐）。
     * 必须在 syncGameState 之前调用。
     */
    default void readyForGame() {
    }

    /**
    }

    /**
     * 快速加载已配对设备（不启动蓝牙搜索，毫秒级返回）。
     */
    default void loadBondedDevices() {
    }

    /**
     * 是否有真实蓝牙客户端连接（不依赖 ViewData 的 connected 标志）。
     */
    default boolean hasRealClients() {
        return false;
    }

    /**
     * HOST 端：通知已连接客户端有 AI 玩家加入。
     */
    default void notifyAiPlayerAdded(String playerId, int slotIndex) {
    }
}
