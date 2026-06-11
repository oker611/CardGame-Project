package com.example.cardgame.network;

import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间控制器：管理玩家槽位分配、名称映射和设备-玩家关联。
 * 共享 BluetoothGateway 的状态 Map，自身不持有锁。
 */
public class RoomController {

    private static final String TAG = "CardGame";
    static final String[] CLIENT_PLAYER_IDS = {"P2", "P3", "P4"};
    static final int MAX_CLIENTS = CLIENT_PLAYER_IDS.length;

    private final Map<String, String> deviceToPlayerId;
    private final Map<String, String> playerIdToDevice;
    final Map<String, String> playerNamesById;
    private final Map<String, String> pendingJoinNamesBySender;

    RoomController(Map<String, String> deviceToPlayerId,
                   Map<String, String> playerIdToDevice,
                   Map<String, String> playerNamesById,
                   Map<String, String> pendingJoinNamesBySender) {
        this.deviceToPlayerId = deviceToPlayerId;
        this.playerIdToDevice = playerIdToDevice;
        this.playerNamesById = playerNamesById;
        this.pendingJoinNamesBySender = pendingJoinNamesBySender;
    }

    // ========== 槽位与 ID 分配 ==========

    String clientPlayerIdForSlot(int slotIndex) {
        return CLIENT_PLAYER_IDS[slotIndex];
    }

    int slotIndexForPlayerId(String playerId) {
        for (int i = 0; i < CLIENT_PLAYER_IDS.length; i++) {
            if (CLIENT_PLAYER_IDS[i].equals(playerId)) return i;
        }
        return -1;
    }

    void assignClientSlot(String deviceAddress, int slotIndex) {
        String playerId = CLIENT_PLAYER_IDS[slotIndex];
        deviceToPlayerId.put(deviceAddress, playerId);
        playerIdToDevice.put(playerId, deviceAddress);
    }

    String getPlayerIdForDevice(String deviceAddress) {
        return deviceToPlayerId.get(deviceAddress);
    }

    String getDeviceForPlayer(String playerId) {
        return playerIdToDevice.get(playerId);
    }

    boolean isClientSlot(String playerId) {
        for (String id : CLIENT_PLAYER_IDS) {
            if (id.equals(playerId)) return true;
        }
        return false;
    }

    // ========== 名称管理 ==========

    void registerPlayerName(String playerId, String name) {
        playerNamesById.put(playerId, safePlayerName(name, "Player " + playerId));
    }

    String getPlayerName(String playerId) {
        return playerNamesById.getOrDefault(playerId, playerId);
    }

    void putPendingJoinName(String senderKey, String name) {
        pendingJoinNamesBySender.put(senderKey, name);
    }

    String takePendingJoinName(String senderKey, String fallback) {
        String name = pendingJoinNamesBySender.remove(senderKey);
        return name != null ? safePlayerName(name, fallback) : fallback;
    }

    // ========== 清理 ==========

    void clear() {
        deviceToPlayerId.clear();
        playerIdToDevice.clear();
        playerNamesById.clear();
        pendingJoinNamesBySender.clear();
    }

    void removeDevice(String deviceAddress) {
        String playerId = deviceToPlayerId.remove(deviceAddress);
        if (playerId != null) {
            playerIdToDevice.remove(playerId);
        }
    }

    // ========== 工具 ==========

    static String safePlayerName(String name, String fallback) {
        if (name != null && !name.trim().isEmpty()) {
            String trimmed = name.trim();
            return trimmed.length() > 20 ? trimmed.substring(0, 20) : trimmed;
        }
        return fallback != null && !fallback.trim().isEmpty() ? fallback.trim() : "Player";
    }

    static String defaultPlayerName(String playerId) {
        switch (playerId) {
            case "P1": return "房主 / 本机";
            case "P2": return "玩家2";
            case "P3": return "玩家3";
            case "P4": return "玩家4";
            default: return "玩家";
        }
    }
}
