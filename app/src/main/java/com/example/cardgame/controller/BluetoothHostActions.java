package com.example.cardgame.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HOST 专用蓝牙操作（仅房主调用）。
 *
 * 从 BluetoothActionHandler 拆分，遵循接口隔离原则。
 */
public interface BluetoothHostActions {

    /** 获取已连接的所有远程玩家 ID（P2/P3/P4） */
    default List<String> getRemotePlayerIds() { return new ArrayList<>(); }

    /** 获取 playerId → playerName 映射 */
    default Map<String, String> getPlayerNamesById() { return new HashMap<>(); }

    /** 通知蓝牙网关房间就绪（AI 玩家已补齐），必须在 syncGameState 之前调用 */
    default void readyForGame() {}

    /** 快速加载已配对设备（毫秒级，不启动蓝牙搜索） */
    default void loadBondedDevices() {}

    /** 是否有真实蓝牙客户端连接 */
    default boolean hasRealClients() { return false; }

    /** 通知已连接客户端有 AI 玩家加入 */
    default void notifyAiPlayerAdded(String playerId, int slotIndex) {}
}
