package com.example.cardgame.network;

public interface BluetoothEventListener {

    void onConnected(String deviceName, String deviceAddress);

    void onDisconnected(String reason);

    void onMessageSent(MessageType messageType, String summary);

    void onMessageReceived(MessageType messageType, String summary);

    void onError(String message, Exception exception);

    void onGameOver(String winnerId, String winnerName);

    /**
     * 蓝牙服务端 Socket 已创建成功，房间可被发现，正在等待客户端连接。
     */
    void onServerReady();

    /**
     * CLIENT 端：收到 HOST 分配的 playerId。
     */
    default void onPlayerAssigned(String playerId, int slotIndex) {
    }

    /**
     * 新玩家加入房间通知。
     */
    default void onPlayerJoined(String playerId, String playerName, int slotIndex) {
    }

    /**
     * 玩家离开房间通知。
     */
    default void onPlayerLeft(String playerId, String playerName) {
    }

    /**
     * 所有玩家就绪（4人到齐），可以开始游戏。
     */
    default void onAllPlayersReady() {
    }
}