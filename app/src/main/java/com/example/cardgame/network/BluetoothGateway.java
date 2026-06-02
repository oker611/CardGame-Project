package com.example.cardgame.network;

import android.content.Context;
import android.util.Log;

import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.network.payload.AckPayload;
import com.example.cardgame.network.payload.GameOverPayload;
import com.example.cardgame.network.payload.InitGamePayload;
import com.example.cardgame.network.payload.JoinPayload;
import com.example.cardgame.network.payload.PassActionPayload;
import com.example.cardgame.network.payload.PlayActionPayload;
import com.example.cardgame.network.payload.PlayerLeftPayload;
import com.example.cardgame.network.payload.ReconnectPayload;

import com.example.cardgame.util.HermesLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BluetoothGateway implements MultiplayerGateway, BluetoothMessageListener {

    private static final String[] CLIENT_PLAYER_IDS = {"P2", "P3", "P4"};
    private static final int MAX_CLIENTS = CLIENT_PLAYER_IDS.length;

    private final BluetoothConnectionManager connectionManager;
    private final BluetoothMessageCodec messageCodec;
    private final NetworkGameBridge networkGameBridge;

    private BluetoothEventListener eventListener;

    private volatile String localPlayerId;

    // ——— 多路连接状态 ———
    /** deviceAddress → playerId (如 "P2", "P3", "P4") */
    private final Map<String, String> deviceToPlayerId = new ConcurrentHashMap<>();
    /** deviceAddress → SenderReceiverPair */
    private final Map<String, SenderReceiverPair> clientChannels = new ConcurrentHashMap<>();
    /** playerId → deviceAddress（反向查找） */
    private final Map<String, String> playerIdToDevice = new ConcurrentHashMap<>();
    private final Map<String, String> playerNamesById = new ConcurrentHashMap<>();
    private final Map<String, String> pendingJoinNamesBySender = new ConcurrentHashMap<>();

    private volatile String role;
    private volatile boolean communicationReady = false;
    private volatile boolean acceptingClients = false;

    /** 锁对象：保护 send/disconnect 之间的 TOCTOU 竞态 */
    private final Object sendLock = new Object();

    /** 房间已通过 readyForGame() 主动结束（AI 补齐），startAsHost 线程退出时跳过错误处理 */
    private volatile boolean roomFinalized = false;

    // ——— 心跳机制 ———
    private static final long HEARTBEAT_INTERVAL_SECONDS = 5;
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 15;
    private ScheduledExecutorService heartbeatExecutor;
    /** 按设备地址追踪每路连接的心跳时间，避免活跃链路掩盖失联链路 */
    private final ConcurrentHashMap<String, Long> lastHeartbeatByAddress = new ConcurrentHashMap<>();

    // ——— ACK 可靠投递机制 ———
    private static final long ACK_TIMEOUT_MS = 3000;
    private static final int ACK_MAX_RETRIES = 3;
    /** 按设备地址追踪待确认的消息 */
    private final ConcurrentHashMap<String, PendingMessage> pendingByChannel = new ConcurrentHashMap<>();

    private static class PendingMessage {
        final BluetoothMessage message;
        long sentAt;
        int retryCount;

        PendingMessage(BluetoothMessage message) {
            this.message = message;
            this.sentAt = System.currentTimeMillis();
            this.retryCount = 0;
        }
    }

    // ——— 断线重连机制 ———
    private static final long RECONNECT_PENDING_TIMEOUT_MS = 10000;
    private volatile boolean acceptingReconnects = false;
    private Thread reconnectListenerThread;
    /** 暂存待验证的重连连接（deviceAddress → PendingReconnect） */
    private final ConcurrentHashMap<String, PendingReconnect> pendingReconnections = new ConcurrentHashMap<>();
    /** 缓存最近一次 GameState，用于重连时同步给客户端 */
    private volatile GameState cachedGameState;
    /** CLIENT 模式：是否正在重连 */
    private volatile boolean reconnecting = false;
    /** CLIENT 模式：HOST 设备地址（重连时使用） */
    private volatile String hostDeviceAddress;

    private static class PendingReconnect {
        final SenderReceiverPair pair;
        final String deviceAddress;
        final long acceptedAt;

        PendingReconnect(SenderReceiverPair pair, String deviceAddress) {
            this.pair = pair;
            this.deviceAddress = deviceAddress;
            this.acceptedAt = System.currentTimeMillis();
        }

        void close() {
            if (pair.receiver != null) pair.receiver.stopListening();
            if (pair.sender != null) pair.sender.stop();
        }
    }

    // ——— 客户端模式：等待 JOIN_ACK ———
    private volatile String assignedPlayerId = null;
    private volatile int assignedSlotIndex = -1;

    // ——— 单连接兼容（旧代码依赖） ———
    private String remotePlayerId;

    public BluetoothGateway(Context context, GameEngine gameEngine) {
        this.connectionManager = new BluetoothConnectionManager(context);
        this.messageCodec = new BluetoothMessageCodec();
        this.networkGameBridge = new NetworkGameBridge(gameEngine, messageCodec);
        this.role = "NONE";
    }

    public void setBluetoothEventListener(BluetoothEventListener eventListener) {
        this.eventListener = eventListener;
        this.networkGameBridge.setBluetoothEventListener(eventListener);
    }

    // ========================================================================
    //  HOST 模式：创建房间 + 接受多个客户端
    // ========================================================================

    public void startAsHost(String localPlayerId, String localPlayerName) {
        resetSessionState();
        this.localPlayerId = localPlayerId;
        this.role = "HOST";
        HermesLog.init("host");
        this.communicationReady = false;
        this.acceptingClients = true;
        this.roomFinalized = false;
        this.deviceToPlayerId.clear();
        this.clientChannels.clear();
        this.playerIdToDevice.clear();
        this.playerNamesById.clear();
        this.pendingJoinNamesBySender.clear();
        this.playerNamesById.put(localPlayerId, safePlayerName(localPlayerName, "Player " + localPlayerId));

        networkGameBridge.setPlayerContext(this.localPlayerId, new ArrayList<>());

        try {
            Log.i("CardGame", "[INFO] [蓝牙] 创建房间（4人模式） | 本机玩家:" + localPlayerId);

            // 第一步：创建服务端 Socket（非阻塞），此时房间已可被发现
            connectionManager.startServer();

            // 通知 UI：房间已创建，等待玩家加入
            notifyServerReady();

            // 第二步：依次接受 3 个客户端连接
            for (int i = 0; i < MAX_CLIENTS; i++) {
                if (roomFinalized) {
                    HermesLog.log("startAsHost loop break: room finalized at slot " + i);
                    break;
                }
                String clientPlayerId = CLIENT_PLAYER_IDS[i];

                Log.i("CardGame", "[INFO] [蓝牙] 等待第" + (i + 1) + "个客户端连接（" + clientPlayerId + "）...");

                String deviceAddress = connectionManager.waitForNextClient();

                // 分配 playerId
                deviceToPlayerId.put(deviceAddress, clientPlayerId);
                playerIdToDevice.put(clientPlayerId, deviceAddress);

                // 建立通信通道
                BluetoothConnectionManager.ClientConnection conn =
                        connectionManager.getConnection(deviceAddress);
                if (conn == null) {
                    throw new IOException("Client connection not found for " + deviceAddress);
                }

                BluetoothSender sender = new BluetoothSender(conn.outputStream, messageCodec);
                BluetoothReceiver receiver = new BluetoothReceiver(conn.inputStream, messageCodec, this);
                receiver.startListening();

                clientChannels.put(deviceAddress, new SenderReceiverPair(sender, receiver));

                // 发送 JOIN_ACK 给新客户端
                String fallbackName = defaultPlayerName(clientPlayerId);
                String playerName = waitForJoinPlayerName(fallbackName);
                playerNamesById.put(clientPlayerId, playerName);
                JoinPayload ackPayload = new JoinPayload(playerName, clientPlayerId, i + 1);
                BluetoothMessage ackMessage = messageCodec.buildJoinAckMessage(
                        localPlayerId, clientPlayerId, ackPayload);
                sender.sendMessage(ackMessage);
                sendExistingPlayersSnapshot(sender, clientPlayerId);

                // 第一个客户端 JOIN_ACK 发送完毕，启动心跳覆盖大厅等待阶段
                if (i == 0) {
                    startHeartbeat();
                }

                // 广播 PLAYER_JOINED 给所有已有客户端
                broadcastPlayerJoined(clientPlayerId, playerName, i + 1);

                // 更新 NetworkGameBridge 的远程玩家列表
                updateNetworkBridgeRemotePlayers();

                notifyPlayerJoined(clientPlayerId, i + 1);

                Log.i("CardGame", "[INFO] [蓝牙] 客户端" + (i + 1) + "已加入 | "
                        + clientPlayerId + " | " + playerName
                        + " | totalClients=" + (i + 1));
            }

            // 所有客户端就绪
            this.communicationReady = true;
            this.acceptingClients = false;
            this.remotePlayerId = CLIENT_PLAYER_IDS[0]; // 兼容旧代码
            notifyAllPlayersReady();

            Log.i("CardGame", "[INFO] [蓝牙] 4人房间就绪 | HOST:" + localPlayerId);

        } catch (Exception exception) {
            if (roomFinalized) {
                HermesLog.log("startAsHost thread exiting (room finalized)");
                return;
            }
            acceptingClients = false;

            // 清理部分建立的连接（停止 receiver 线程 + 关闭 socket）
            Log.e("CardGame", "[ERROR] [蓝牙] 房间创建失败，清理已建立的连接", exception);
            for (SenderReceiverPair pair : clientChannels.values()) {
                if (pair.receiver != null) pair.receiver.stopListening();
                if (pair.sender != null) pair.sender.stop();
            }
            clientChannels.clear();
            deviceToPlayerId.clear();
            playerIdToDevice.clear();
            connectionManager.close();

            handleConnectionError("创建蓝牙房间失败", exception);
        }
    }

    // ========================================================================
    //  CLIENT 模式：连接房间
    // ========================================================================

    public void connectAsClient(String localPlayerId, String deviceAddress, String localPlayerName) {
        resetSessionState();
        this.localPlayerId = localPlayerId;
        this.role = "CLIENT";
        HermesLog.init("client");
        this.communicationReady = false;
        this.roomFinalized = false;
        this.assignedPlayerId = null;
        this.assignedSlotIndex = -1;

        networkGameBridge.setPlayerContext(this.localPlayerId, new ArrayList<>());

        try {
            Log.i("CardGame", "[INFO] [蓝牙] 发起连接 | 目标设备:" + deviceAddress);

            this.hostDeviceAddress = deviceAddress;
            connectionManager.connectToDevice(deviceAddress);
            setupCommunicationChannelSingle();

            // 发送 JOIN 请求：只使用游戏昵称，不再回退到手机蓝牙名
            String playerName = safePlayerName(localPlayerName, defaultPlayerName(localPlayerId));
            JoinPayload joinPayload = new JoinPayload(playerName);
            BluetoothMessage joinMessage = messageCodec.buildJoinMessage(
                    localPlayerId, "HOST", joinPayload);
            sendBluetoothMessageRaw(joinMessage, "请求加入房间");

            notifyConnected();

            Log.i("CardGame", "[INFO] [蓝牙] 客户端已连接，等待HOST分配角色...");

        } catch (Exception exception) {
            // 清理部分建立的连接（停止 receiver 线程 + 关闭 socket）
            Log.e("CardGame", "[ERROR] [蓝牙] 客户端连接失败，清理资源", exception);
            for (SenderReceiverPair pair : clientChannels.values()) {
                if (pair.receiver != null) pair.receiver.stopListening();
                if (pair.sender != null) pair.sender.stop();
            }
            clientChannels.clear();
            connectionManager.close();

            handleConnectionError("连接蓝牙设备失败", exception);
        }
    }

    // ========================================================================
    //  设备搜索
    // ========================================================================

    public List<BluetoothDeviceInfo> searchDevices() {
        if (!connectionManager.isBluetoothAvailable()) {
            notifyError("当前设备不支持蓝牙", null);
            return new ArrayList<>();
        }

        if (!connectionManager.isBluetoothEnabled()) {
            notifyError("蓝牙未开启", null);
            return new ArrayList<>();
        }

        List<BluetoothDeviceInfo> devices = connectionManager.discoverJoinableMobileDevices();

        Log.i("CardGame", "[INFO] [蓝牙] 可加入设备搜索完成 | 数量:" + devices.size());

        return devices;
    }

    // ========================================================================
    //  通信通道管理
    // ========================================================================

    private void setupCommunicationChannelSingle() throws IOException {
        if (connectionManager.getOutputStream() == null || connectionManager.getInputStream() == null) {
            throw new IOException("Bluetooth stream is null");
        }

        SenderReceiverPair pair = new SenderReceiverPair(
                new BluetoothSender(connectionManager.getOutputStream(), messageCodec),
                new BluetoothReceiver(connectionManager.getInputStream(), messageCodec, this)
        );

        // CLIENT 端只有一路到 HOST 的连接
        String deviceAddress = connectionManager.getConnectedDeviceAddress();
        if (deviceAddress != null) {
            clientChannels.put(deviceAddress, pair);
        }

        pair.receiver.startListening();
        communicationReady = true;

        Log.i("CardGame", "[INFO] [蓝牙] 通信通道建立 | 角色:" + role
                + " 本机:" + localPlayerId);
    }

    // ========================================================================
    //  消息发送（所有方法改为广播到所有连接）
    // ========================================================================

    @Override
    public void sendPlayAction(Play play) {
        if (play == null) {
            Log.w("CardGame", "[WARN] [蓝牙] [发送] 出牌消息为空 | play:null");
            return;
        }

        PlayActionPayload payload = new PlayActionPayload(
                play.getPlayerId(),
                new ArrayList<>(),
                play
        );

        BluetoothMessage message = messageCodec.buildPlayActionMessage(
                localPlayerId, "ALL", payload);

        sendBluetoothMessage(message, "本地玩家出牌:" + play.getPlayerId());
    }

    @Override
    public void sendPassAction(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            Log.w("CardGame", "[WARN] [蓝牙] [发送] Pass玩家为空 | playerId:null");
            return;
        }

        PassActionPayload payload = new PassActionPayload(playerId);

        BluetoothMessage message = messageCodec.buildPassActionMessage(
                localPlayerId, "ALL", payload);

        sendBluetoothMessage(message, "本地玩家Pass:" + playerId);
    }

    @Override
    public void syncGameState(GameState gameState) {
        if (gameState == null) {
            Log.w("CardGame", "[WARN] [蓝牙] [发送] 同步状态为空 | gameState:null");
            return;
        }

        cacheGameState(gameState);

        HermesLog.log("SYNC players=" + (gameState.getPlayers() != null ? gameState.getPlayers().size() : 0)
                + " cr=" + communicationReady + " ch=" + clientChannels.size());

        // 构建 playerOrder（所有客户端一致）
        List<String> playerOrder = new ArrayList<>();
        Map<String, Integer> cardCounts = new HashMap<>();
        if (gameState.getPlayers() != null) {
            for (com.example.cardgame.model.Player p : gameState.getPlayers()) {
                if (p != null) {
                    playerOrder.add(p.getPlayerId());
                    cardCounts.put(p.getPlayerId(),
                            p.getHandCards() != null ? p.getHandCards().size() : 0);
                }
            }
        }

        // 按客户端分别构建 payload，只向目标客户端暴露其自身手牌
        for (Map.Entry<String, String> entry : deviceToPlayerId.entrySet()) {
            String targetDevice = entry.getKey();
            String targetPlayerId = entry.getValue();

            // 构建过滤后的手牌映射：仅包含目标玩家的实际手牌
            Map<String, List<Card>> filteredHands = new HashMap<>();
            for (com.example.cardgame.model.Player p : gameState.getPlayers()) {
                if (p == null) continue;
                if (p.getPlayerId().equals(targetPlayerId)) {
                    filteredHands.put(p.getPlayerId(), p.getHandCards());
                } else {
                    filteredHands.put(p.getPlayerId(), new ArrayList<>());
                }
            }

            InitGamePayload payload = new InitGamePayload(
                    filteredHands,
                    playerOrder,
                    gameState.getCurrentPlayerId(),
                    gameState,
                    cardCounts
            );

            BluetoothMessage message = messageCodec.buildInitGameMessage(
                    localPlayerId, targetPlayerId, payload);

            sendToClient(targetDevice, message,
                    "同步游戏状态 to:" + targetPlayerId
                            + " currentPlayer:" + gameState.getCurrentPlayerId());
        }
    }

    private void sendToClient(String deviceAddress, BluetoothMessage message, String summary) {
        synchronized (sendLock) {
            SenderReceiverPair pair = clientChannels.get(deviceAddress);
            if (pair == null || pair.sender == null || !pair.sender.isActive()) {
                Log.w("CardGame", "[WARN] [蓝牙] [发送] 目标通道不可用 | " + summary);
                return;
            }
            try {
                pair.sender.sendMessage(message);
                if (needsAck(message.getMessageType())) {
                    pendingByChannel.put(deviceAddress, new PendingMessage(message));
                }
                Log.d("CardGame", "[DEBUG] [蓝牙] [发送] 单播 | 到:" + deviceAddress
                        + " 类型:" + message.getMessageType() + " " + summary);
            } catch (Exception e) {
                Log.e("CardGame", "[ERROR] [蓝牙] [发送] 单播失败 | " + summary, e);
            }
        }
    }

    public void sendGameOver(String winnerId, String winnerName) {
        if (winnerId == null || winnerId.trim().isEmpty()) {
            Log.w("CardGame", "[WARN] [蓝牙] [发送] 游戏结束胜者为空 | winnerId:null");
            return;
        }

        GameOverPayload payload = new GameOverPayload(winnerId, winnerName);

        BluetoothMessage message = messageCodec.buildGameOverMessage(
                localPlayerId, "ALL", payload);

        sendBluetoothMessage(message, "游戏结束:" + winnerId);
    }

    // ========================================================================
    //  广播辅助方法
    // ========================================================================

    private void broadcastPlayerJoined(String playerId, String playerName, int slotIndex) {
        JoinPayload joinPayload = new JoinPayload(playerName, playerId, slotIndex);
        BluetoothMessage msg = messageCodec.buildPlayerJoinedMessage(
                localPlayerId, "ALL", joinPayload);

        synchronized (sendLock) {
            for (Map.Entry<String, SenderReceiverPair> entry : clientChannels.entrySet()) {
                String addr = entry.getKey();
                String existingPlayer = deviceToPlayerId.get(addr);
                if (existingPlayer != null && existingPlayer.equals(playerId)) {
                    continue;
                }

                try {
                    entry.getValue().sender.sendMessage(msg);
                } catch (Exception e) {
                    Log.e("CardGame", "[ERROR] [蓝牙] 广播PLAYER_JOINED失败 | to=" + addr, e);
                }
            }
        }
    }

    private void sendExistingPlayersSnapshot(BluetoothSender sender, String joiningPlayerId) {
        if (sender == null) {
            return;
        }

        synchronized (sendLock) {
            sendExistingPlayerToJoiningClient(sender, joiningPlayerId, localPlayerId);
            for (String playerId : CLIENT_PLAYER_IDS) {
                sendExistingPlayerToJoiningClient(sender, joiningPlayerId, playerId);
            }
        }
    }

    private void sendExistingPlayerToJoiningClient(
            BluetoothSender sender,
            String joiningPlayerId,
            String existingPlayerId
    ) {
        if (existingPlayerId == null || existingPlayerId.equals(joiningPlayerId)) {
            return;
        }
        if (!playerNamesById.containsKey(existingPlayerId)) {
            return;
        }

        int slotIndex = slotIndexForPlayerId(existingPlayerId);
        if (slotIndex < 0) {
            return;
        }

        String playerName = playerNamesById.get(existingPlayerId);
        JoinPayload payload = new JoinPayload(playerName, existingPlayerId, slotIndex);
        BluetoothMessage message = messageCodec.buildPlayerJoinedMessage(
                localPlayerId, joiningPlayerId, payload);

        try {
            sender.sendMessage(message);
        } catch (Exception e) {
            Log.e("CardGame", "[ERROR] [蓝牙] 发送已有玩家快照失败 | playerId="
                    + existingPlayerId + ", to=" + joiningPlayerId, e);
        }
    }

    private int slotIndexForPlayerId(String playerId) {
        if ("P1".equals(playerId)) {
            return 0;
        }
        for (int i = 0; i < CLIENT_PLAYER_IDS.length; i++) {
            if (CLIENT_PLAYER_IDS[i].equals(playerId)) {
                return i + 1;
            }
        }
        return -1;
    }

    private void updateNetworkBridgeRemotePlayers() {
        List<String> remoteIds = new ArrayList<>(playerIdToDevice.keySet());
        networkGameBridge.setPlayerContext(localPlayerId, remoteIds);
    }

    private String waitForJoinPlayerName(String fallbackName) {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            for (String name : pendingJoinNamesBySender.values()) {
                if (name != null && !name.trim().isEmpty()) {
                    pendingJoinNamesBySender.clear();
                    return name.trim();
                }
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        pendingJoinNamesBySender.clear();
        return safePlayerName(fallbackName, "Player");
    }

    // ========================================================================
    //  发送到所有客户端
    // ========================================================================

    private void sendBluetoothMessage(BluetoothMessage message, String summary) {
        synchronized (sendLock) {
            if (!communicationReady) {
                HermesLog.log("SEND BLOCKED communicationReady=false " + summary);
                Log.w("CardGame", "[WARN] [蓝牙] [发送] 通道未就绪，丢弃消息 | " + summary);
                return;
            }

            HermesLog.log("SEND START type=" + message.getMessageType()
                    + " channels=" + clientChannels.size());


            for (Map.Entry<String, SenderReceiverPair> entry : clientChannels.entrySet()) {
                try {
                    String deviceAddress = entry.getKey();
                    SenderReceiverPair pair = entry.getValue();

                    if (pair.sender == null || !pair.sender.isActive()) {
                        continue;
                    }

                    pair.sender.sendMessage(message);

                    if (needsAck(message.getMessageType())) {
                        pendingByChannel.put(deviceAddress, new PendingMessage(message));
                    }

                    Log.d("CardGame", "[DEBUG] [蓝牙] [发送] 消息已广播 | 类型:"
                            + message.getMessageType()
                            + " 到:" + deviceAddress
                            + " 内容:" + summary);

                } catch (Exception exception) {
                    Log.e("CardGame", "[ERROR] [蓝牙] [发送] 广播失败 | " + summary, exception);
                }
            }
        }

        if (eventListener != null) {
            eventListener.onMessageSent(message.getMessageType(), summary);
        }
    }

    /**
     * CLIENT 模式：仅发送到 HOST（不走广播循环）。
     */
    private void sendBluetoothMessageRaw(BluetoothMessage message, String summary) {
        Exception sendException = null;

        synchronized (sendLock) {
            if (clientChannels.isEmpty()) {
                Log.w("CardGame", "[WARN] [蓝牙] [发送] 无可用通道 | " + summary);
                return;
            }

            SenderReceiverPair pair = clientChannels.values().iterator().next();
            if (pair.sender == null || !pair.sender.isActive()) {
                Log.w("CardGame", "[WARN] [蓝牙] [发送] sender未就绪 | " + summary);
                return;
            }

            try {
                pair.sender.sendMessage(message);

                Log.d("CardGame", "[DEBUG] [蓝牙] [发送] 消息已发送 | 类型:"
                        + message.getMessageType()
                        + " 内容:" + summary);
            } catch (Exception exception) {
                sendException = exception;
            }
        }

        if (sendException != null) {
            handleConnectionError("发送蓝牙消息失败: " + summary, sendException);
            return;
        }

        if (eventListener != null) {
            eventListener.onMessageSent(message.getMessageType(), summary);
        }
    }

    // ========================================================================
    //  消息接收处理
    // ========================================================================

    @Override
    public void onMessageReceived(BluetoothMessage message) {
        if (message == null) {
            Log.w("CardGame", "[WARN] [蓝牙] [接收] 空消息 | message:null");
            return;
        }

        Log.d("CardGame", "[DEBUG] [蓝牙] [接收] 消息接收 | 类型:"
                + message.getMessageType()
                + " 发送者:" + message.getSenderPlayerId());

        if (eventListener != null) {
            eventListener.onMessageReceived(
                    message.getMessageType(),
                    "来自:" + message.getSenderPlayerId()
            );
        }

        // 处理新消息类型
        switch (message.getMessageType()) {
            case JOIN:
                // HOST 处理 JOIN（但如果 HOST 模式用的是 accept 循环，这个分支主要用于兼容）
                handleJoinMessage(message);
                break;

            case JOIN_ACK:
                // CLIENT 收到 HOST 的角色分配
                handleJoinAckMessage(message);
                break;

            case PLAYER_JOINED:
                // 已有客户端收到新玩家加入通知
                handlePlayerJoinedMessage(message);
                break;

            case PLAYER_LEFT:
                handlePlayerLeftMessage(message);
                break;

            case HEARTBEAT:
                updateHeartbeatTimestamp(message.getSenderPlayerId());
                Log.d("CardGame", "[DEBUG] [蓝牙] [接收] 心跳消息 | 发送者:" + message.getSenderPlayerId());
                break;

            case ACK:
                handleAckMessage(message);
                break;

            case RECONNECT:
                if (isHost()) {
                    handleReconnectMessage(message);
                }
                break;

            case RECONNECT_ACK:
                if (!isHost()) {
                    handleReconnectAckMessage(message);
                }
                break;

            default:
                // 游戏消息：发送 ACK 确认收到
                if (needsAck(message.getMessageType())) {
                    sendAckFor(message);
                }

                // 所有游戏相关消息（INIT_GAME, PLAY_ACTION, PASS_ACTION, GAME_OVER, ERROR）
                // HOST 模式下：转发给其他客户端
                if (isHost() && communicationReady) {
                    forwardToOtherClients(message);
                }

                // 交给 bridge 处理
                networkGameBridge.handleMessage(message);

                break;
        }
    }

    private void handleJoinMessage(BluetoothMessage message) {
        Log.d("CardGame", "[DEBUG] [蓝牙] 收到JOIN请求 from=" + message.getSenderPlayerId());
        try {
            JoinPayload payload = messageCodec.decodeJoinPayload(message.getPayloadJson());
            if (payload != null && payload.getPlayerName() != null) {
                pendingJoinNamesBySender.put(message.getSenderPlayerId(), payload.getPlayerName().trim());
            }
        } catch (Exception e) {
            Log.w("CardGame", "[WARN] [蓝牙] 解析JOIN昵称失败", e);
        }
    }

    private void handleJoinAckMessage(BluetoothMessage message) {
        Log.i("CardGame", "[INFO] [蓝牙] 收到JOIN_ACK");

        try {
            JoinPayload payload = messageCodec.decodeJoinPayload(message.getPayloadJson());
            this.assignedPlayerId = payload.getAssignedPlayerId();
            this.assignedSlotIndex = payload.getSlotIndex();

            // 更新本地身份
            this.localPlayerId = assignedPlayerId;
            this.remotePlayerId = "P1"; // HOST 始终是 P1
            this.playerIdToDevice.put(assignedPlayerId, connectionManager.getConnectedDeviceAddress());
            this.playerNamesById.put(assignedPlayerId,
                    safePlayerName(payload.getPlayerName(), "Player " + assignedPlayerId));

            // 更新 NetworkGameBridge：HOST (P1) 为远程玩家，确保后续 configurePlayerTypes 正确
            List<String> remoteIds = new ArrayList<>();
            remoteIds.add("P1");
            networkGameBridge.setPlayerContext(this.localPlayerId, remoteIds);

            Log.i("CardGame", "[INFO] [蓝牙] HOST分配角色: " + assignedPlayerId
                    + ", slot=" + assignedSlotIndex);

            if (eventListener != null) {
                eventListener.onPlayerAssigned(assignedPlayerId, assignedSlotIndex);
            }

            // JOIN_ACK 确认通道就绪，启动心跳覆盖大厅等待阶段
            startHeartbeat();

        } catch (Exception e) {
            Log.e("CardGame", "[ERROR] [蓝牙] 解析JOIN_ACK失败", e);
        }
    }

    private void handlePlayerJoinedMessage(BluetoothMessage message) {
        Log.i("CardGame", "[INFO] [蓝牙] 收到PLAYER_JOINED");

        try {
            JoinPayload payload = messageCodec.decodeJoinPayload(message.getPayloadJson());
            String newPlayerId = payload.getAssignedPlayerId();
            String newPlayerName = payload.getPlayerName();
            int slot = payload.getSlotIndex();
            playerNamesById.put(newPlayerId, safePlayerName(newPlayerName, "Player " + newPlayerId));

            Log.i("CardGame", "[INFO] [蓝牙] 新玩家加入: " + newPlayerId
                    + " (" + newPlayerName + "), slot=" + slot);

            if (eventListener != null) {
                eventListener.onPlayerJoined(newPlayerId, newPlayerName, slot);
            }

        } catch (Exception e) {
            Log.e("CardGame", "[ERROR] [蓝牙] 解析PLAYER_JOINED失败", e);
        }
    }

    private void handlePlayerLeftMessage(BluetoothMessage message) {
        Log.i("CardGame", "[INFO] [蓝牙] 收到PLAYER_LEFT");

        try {
            PlayerLeftPayload payload = messageCodec.decodePlayerLeftPayload(message.getPayloadJson());
            String leftPlayerId = payload.getPlayerId();
            String leftPlayerName = payload.getPlayerName();

            Log.i("CardGame", "[INFO] [蓝牙] 玩家离开: " + leftPlayerId
                    + " (" + leftPlayerName + ")");

            if (eventListener != null) {
                eventListener.onPlayerLeft(leftPlayerId, leftPlayerName);
            }

        } catch (Exception e) {
            Log.e("CardGame", "[ERROR] [蓝牙] 解析PLAYER_LEFT失败", e);
        }
    }

    /**
     * HOST 模式下：将收到的消息转发给其他所有客户端（除发送者外）。
     */
    private void forwardToOtherClients(BluetoothMessage originalMessage) {
        String senderPlayerId = originalMessage.getSenderPlayerId();

        synchronized (sendLock) {
            for (Map.Entry<String, String> entry : deviceToPlayerId.entrySet()) {
                String targetDevice = entry.getKey();
                String targetPlayerId = entry.getValue();

                if (targetPlayerId.equals(senderPlayerId)) {
                    continue;
                }

                SenderReceiverPair pair = clientChannels.get(targetDevice);
                if (pair == null || pair.sender == null || !pair.sender.isActive()) {
                    continue;
                }

                try {
                    pair.sender.sendMessage(originalMessage);

                    Log.d("CardGame", "[DEBUG] [蓝牙] 消息已转发 | 类型:"
                            + originalMessage.getMessageType()
                            + " 来自:" + senderPlayerId
                            + " 转发到:" + targetPlayerId);

                } catch (Exception e) {
                    Log.e("CardGame", "[ERROR] [蓝牙] 转发失败 | to=" + targetPlayerId, e);
                }
            }
        }
    }

    // ========================================================================
    //  状态查询
    // ========================================================================

    @Override
    public void onReceiveError(Exception exception) {
        HermesLog.log("RECV_ERROR type="
                + (exception != null ? exception.getClass().getSimpleName() : "null")
                + " msg=" + (exception != null ? exception.getMessage() : "null")
                + " role=" + role);
        if (!isHost() && communicationReady && !reconnecting) {
            Log.i("CardGame", "[INFO] [蓝牙] CLIENT 接收异常，启动重连");
            closeClientChannel();
            startClientReconnect();
            return;
        }
        handleConnectionError("蓝牙接收数据失败", exception);
    }

    public boolean isConnected() {
        return connectionManager.isConnected();
    }

    public boolean isHost() {
        return "HOST".equals(role);
    }

    public String getRole() {
        return role;
    }

    public String getLocalPlayerId() {
        return localPlayerId;
    }

    public String getAssignedPlayerId() {
        return assignedPlayerId;
    }

    public int getAssignedSlotIndex() {
        return assignedSlotIndex;
    }

    public Map<String, String> getDeviceToPlayerId() {
        return Collections.unmodifiableMap(deviceToPlayerId);
    }

    public List<String> getRemotePlayerIds() {
        return new ArrayList<>(playerIdToDevice.keySet());
    }

    public Map<String, String> getPlayerNamesById() {
        return new LinkedHashMap<>(playerNamesById);
    }

    public void readyForGame() {
        HermesLog.log("READY readyForGame communicationReady=" + communicationReady
                + " channels=" + clientChannels.size());
        this.communicationReady = true;
        this.acceptingClients = false;
        this.roomFinalized = true;

        if (isHost()) {
            // 有真人客户端时保留重连能力，纯 AI 局直接关闭 serverSocket
            if (!deviceToPlayerId.isEmpty()) {
                connectionManager.interruptAccept();
                connectionManager.resumeAccept();
                startReconnectListener();
            } else {
                connectionManager.closeServerSocket();
            }
        } else {
            connectionManager.closeServerSocket();
        }

        HermesLog.log("READY done");
    }

    /**
     * 缓存当前 GameState（在 syncGameState 时由 HOST 调用）。
     * CLIENT 端收到 INIT_GAME 后也会调用以保持最新状态。
     */
    public void cacheGameState(GameState gameState) {
        if (gameState != null) {
            this.cachedGameState = gameState;
        }
    }

    public void notifyAiPlayerAdded(String playerId, int slotIndex) {
        HermesLog.log("AI_ADD " + playerId + " slot=" + slotIndex + " channels=" + clientChannels.size());

        playerNamesById.put(playerId, "AI player");
        JoinPayload joinPayload = new JoinPayload("AI player", playerId, slotIndex);
        BluetoothMessage msg = messageCodec.buildPlayerJoinedMessage(localPlayerId, "ALL", joinPayload);

        synchronized (sendLock) {
            for (Map.Entry<String, SenderReceiverPair> entry : clientChannels.entrySet()) {
                try {
                    entry.getValue().sender.sendMessage(msg);
                } catch (Exception e) {
                    Log.e("CardGame", "[ERROR] broadcast AI failed", e);
                }
            }
        }
    }

    public List<BluetoothDeviceInfo> getBondedDevices() {
        if (!connectionManager.isBluetoothAvailable() || !connectionManager.isBluetoothEnabled()) {
            return new ArrayList<>();
        }
        return connectionManager.getBondedJoinableDevices();
    }

    public boolean hasRealClients() {
        return !deviceToPlayerId.isEmpty();
    }

    public int getConnectedClientCount() {
        return deviceToPlayerId.size();
    }

    // ========================================================================
    //  断开连接
    // ========================================================================

    public void disconnect() {
        stopHeartbeat();
        stopReconnectListener();
        reconnecting = false;

        synchronized (sendLock) {
            communicationReady = false;
            acceptingClients = false;

            for (SenderReceiverPair pair : clientChannels.values()) {
                if (pair.receiver != null) {
                    pair.receiver.stopListening();
                }
                if (pair.sender != null) {
                    pair.sender.stop();
                }
            }

            clientChannels.clear();
            deviceToPlayerId.clear();
            playerIdToDevice.clear();
            playerNamesById.clear();
            pendingJoinNamesBySender.clear();
            lastHeartbeatByAddress.clear();
            pendingByChannel.clear();
            roomFinalized = false;
            role = "NONE";
            localPlayerId = null;
            remotePlayerId = null;
            assignedPlayerId = null;
            assignedSlotIndex = -1;
            hostDeviceAddress = null;
            cachedGameState = null;
        }

        connectionManager.close();

        Log.i("CardGame", "[INFO] [蓝牙] 连接断开 | 原因:用户主动断开");

        if (eventListener != null) {
            eventListener.onDisconnected("用户主动断开");
        }
    }

    // ========================================================================
    //  通知回调
    // ========================================================================

    private void resetSessionState() {
        stopHeartbeat();
        stopReconnectListener();
        reconnecting = false;
        acceptingReconnects = false;

        synchronized (sendLock) {
            communicationReady = false;
            acceptingClients = false;
            roomFinalized = false;
            role = "NONE";
            localPlayerId = null;
            remotePlayerId = null;
            assignedPlayerId = null;
            assignedSlotIndex = -1;
            hostDeviceAddress = null;
            cachedGameState = null;

            for (SenderReceiverPair pair : clientChannels.values()) {
                if (pair.receiver != null) {
                    pair.receiver.stopListening();
                }
                if (pair.sender != null) {
                    pair.sender.stop();
                }
            }

            clientChannels.clear();
            deviceToPlayerId.clear();
            playerIdToDevice.clear();
            playerNamesById.clear();
            pendingJoinNamesBySender.clear();
            lastHeartbeatByAddress.clear();
            pendingByChannel.clear();
        }

        connectionManager.close();
    }

    private void notifyConnected() {
        Log.i("CardGame", "[INFO] [蓝牙] 连接成功 | 对方设备:"
                + connectionManager.getConnectedDeviceName());

        if (eventListener != null) {
            eventListener.onConnected(
                    connectionManager.getConnectedDeviceName(),
                    connectionManager.getConnectedDeviceAddress()
            );
        }
    }

    private void notifyServerReady() {
        Log.i("CardGame", "[INFO] [蓝牙] 服务端就绪 | 等待客户端连接...");

        if (eventListener != null) {
            eventListener.onServerReady();
        }
    }

    private void notifyPlayerJoined(String playerId, int slotIndex) {
        Log.i("CardGame", "[INFO] [蓝牙] 玩家加入 | " + playerId + " slot=" + slotIndex);

        if (eventListener != null) {
            eventListener.onPlayerJoined(playerId,
                    safePlayerName(playerNamesById.get(playerId), "Player " + playerId),
                    slotIndex);
        }
    }

    private String safePlayerName(String name, String fallback) {
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        return fallback != null && !fallback.trim().isEmpty() ? fallback.trim() : "Player";
    }

    private String defaultPlayerName(String playerId) {
        if (playerId != null && playerId.matches("P[1-4]")) {
            return "玩家" + playerId.substring(1);
        }
        return "玩家";
    }

    private void notifyAllPlayersReady() {
        Log.i("CardGame", "[INFO] [蓝牙] 4人房间就绪");

        if (eventListener != null) {
            eventListener.onAllPlayersReady();
        }
    }

    private void notifyError(String message, Exception exception) {
        Log.e("CardGame", "[ERROR] [蓝牙] 异常 | 原因:" + message, exception);

        if (eventListener != null) {
            eventListener.onError(message, exception);
        }
    }

    private void handleConnectionError(String message, Exception exception) {
        communicationReady = false;
        stopHeartbeat();

        Log.e("CardGame", "[ERROR] [蓝牙] 连接断开 | 原因:" + message, exception);

        if (eventListener != null) {
            eventListener.onDisconnected(message);
            eventListener.onError(message, exception);
        }
    }

    // ========================================================================
    //  心跳机制
    // ========================================================================

    private void startHeartbeat() {
        stopHeartbeat();

        // 初始化所有现有通道的心跳时间戳
        long now = System.currentTimeMillis();
        for (String addr : clientChannels.keySet()) {
            lastHeartbeatByAddress.put(addr, now);
        }

        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CardGame-Heartbeat");
            t.setDaemon(true);
            return t;
        });

        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                BluetoothMessage heartbeat = messageCodec.buildHeartbeatMessage(
                        localPlayerId, "ALL");

                synchronized (sendLock) {
                    if (clientChannels.isEmpty()) return;
                    for (Map.Entry<String, SenderReceiverPair> entry : clientChannels.entrySet()) {
                        try {
                            if (entry.getValue().sender.isActive()) {
                                entry.getValue().sender.sendMessage(heartbeat);
                            }
                        } catch (Exception e) {
                            Log.w("CardGame", "[WARN] [蓝牙] 心跳发送失败 | to=" + entry.getKey(), e);
                        }
                    }
                }

                // ACK 重试：检查待确认消息是否需要重发
                ackRetryCheck();

                // 清理过期的待验证重连
                cleanupStalePendingReconnections();

                // 逐路检测心跳超时：活跃链路不会掩盖失联链路
                long currentTime = System.currentTimeMillis();
                long timeoutMs = HEARTBEAT_TIMEOUT_SECONDS * 1000;
                for (String addr : clientChannels.keySet()) {
                    Long lastHb = lastHeartbeatByAddress.get(addr);
                    if (lastHb == null) {
                        lastHeartbeatByAddress.put(addr, currentTime);
                        continue;
                    }
                    long elapsed = currentTime - lastHb;
                    if (elapsed > timeoutMs) {
                        Log.e("CardGame", "[ERROR] [蓝牙] 心跳超时 | device=" + addr
                                + " | elapsed=" + (elapsed / 1000) + "s");
                        if (isHost()) {
                            handleChannelDisconnected(addr);
                        } else {
                            stopHeartbeat();
                            startClientReconnect();
                        }
                        return;
                    }
                }
            } catch (Exception e) {
                Log.w("CardGame", "[WARN] [蓝牙] 心跳任务异常", e);
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        Log.i("CardGame", "[INFO] [蓝牙] 心跳已启动 | interval=" + HEARTBEAT_INTERVAL_SECONDS + "s"
                + " timeout=" + HEARTBEAT_TIMEOUT_SECONDS + "s"
                + " channels=" + clientChannels.size());
    }

    /**
     * 根据 senderPlayerId 更新对应通道的心跳时间戳。
     * HOST 通过 playerIdToDevice 反查设备地址；
     * CLIENT 只有一条通道，直接用通道 key。
     */
    private void updateHeartbeatTimestamp(String senderPlayerId) {
        // HOST：通过 playerId → 设备地址
        String addr = playerIdToDevice.get(senderPlayerId);
        if (addr != null) {
            lastHeartbeatByAddress.put(addr, System.currentTimeMillis());
            return;
        }
        // CLIENT 或 fallback：只有一条通道，直接更新时间戳
        if (!clientChannels.isEmpty()) {
            String firstAddr = clientChannels.keySet().iterator().next();
            lastHeartbeatByAddress.put(firstAddr, System.currentTimeMillis());
        }
    }

    private void stopHeartbeat() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
            Log.i("CardGame", "[INFO] [蓝牙] 心跳已停止");
        }
    }

    // ========================================================================
    //  断线重连 — HOST 端
    // ========================================================================

    private void startReconnectListener() {
        if (reconnectListenerThread != null) return;

        acceptingReconnects = true;
        reconnectListenerThread = new Thread(() -> {
            Log.i("CardGame", "[INFO] [蓝牙] 重连监听线程已启动");
            while (acceptingReconnects && connectionManager.isServerSocketOpen()) {
                try {
                    BluetoothConnectionManager.ClientConnection conn =
                            connectionManager.acceptRawConnection();
                    if (conn == null) continue;

                    BluetoothSender sender = new BluetoothSender(conn.outputStream, messageCodec);
                    BluetoothReceiver receiver = new BluetoothReceiver(conn.inputStream, messageCodec, this);
                    receiver.startListening();

                    SenderReceiverPair pair = new SenderReceiverPair(sender, receiver);
                    pendingReconnections.put(conn.deviceAddress,
                            new PendingReconnect(pair, conn.deviceAddress));

                    Log.i("CardGame", "[INFO] [蓝牙] 重连请求待验证 | device=" + conn.deviceAddress);

                } catch (IOException e) {
                    if (acceptingReconnects) {
                        Log.w("CardGame", "[WARN] [蓝牙] 重连监听 accept 异常", e);
                    }
                }
            }
            Log.i("CardGame", "[INFO] [蓝牙] 重连监听线程已退出");
        }, "CardGame-ReconnectListener");
        reconnectListenerThread.setDaemon(true);
        reconnectListenerThread.start();
    }

    private void stopReconnectListener() {
        acceptingReconnects = false;
        connectionManager.interruptAccept();
        if (reconnectListenerThread != null) {
            reconnectListenerThread.interrupt();
            reconnectListenerThread = null;
        }
        // 清理所有待验证的重连
        for (PendingReconnect pr : pendingReconnections.values()) {
            pr.close();
        }
        pendingReconnections.clear();
    }

    /**
     * HOST 端处理单路心跳超时：关闭该通道并通知 UI，
     * 但不关闭 serverSocket，等待该客户端重连。
     */
    private void handleChannelDisconnected(String deviceAddress) {
        String playerId = deviceToPlayerId.get(deviceAddress);
        Log.e("CardGame", "[ERROR] [蓝牙] 通道断开 | device=" + deviceAddress
                + " playerId=" + playerId);

        synchronized (sendLock) {
            SenderReceiverPair pair = clientChannels.remove(deviceAddress);
            if (pair != null) {
                if (pair.receiver != null) pair.receiver.stopListening();
                if (pair.sender != null) pair.sender.stop();
            }
            deviceToPlayerId.remove(deviceAddress);
            if (playerId != null) {
                playerIdToDevice.remove(playerId);
            }
            pendingByChannel.remove(deviceAddress);
            lastHeartbeatByAddress.remove(deviceAddress);
            connectionManager.closeConnection(deviceAddress);
        }

        if (playerId != null && eventListener != null) {
            String name = playerNamesById.getOrDefault(playerId, playerId);
            eventListener.onPlayerLeft(playerId, name);
        }

        // 确保 serverSocket 仍在监听（接受重连）
        if (!connectionManager.isServerSocketOpen()) {
            Log.w("CardGame", "[WARN] [蓝牙] serverSocket 已关闭，无法接受重连");
        }
    }

    private void handleReconnectMessage(BluetoothMessage message) {
        String claimedPlayerId;
        String claimedPlayerName;
        try {
            ReconnectPayload payload = messageCodec.decodeReconnectPayload(message.getPayloadJson());
            claimedPlayerId = payload.getPlayerId();
            claimedPlayerName = payload.getPlayerName();
        } catch (Exception e) {
            Log.w("CardGame", "[WARN] [蓝牙] 解析RECONNECT失败", e);
            return;
        }

        if (claimedPlayerId == null) {
            Log.w("CardGame", "[WARN] [蓝牙] RECONNECT 缺少 playerId");
            return;
        }

        // 验证是否为已知玩家
        if (!playerNamesById.containsKey(claimedPlayerId)) {
            Log.w("CardGame", "[WARN] [蓝牙] RECONNECT 无效 playerId: " + claimedPlayerId);
            return;
        }

        // 找到对应的 pending 连接
        PendingReconnect found = null;
        String foundAddr = null;

        for (Map.Entry<String, PendingReconnect> entry : pendingReconnections.entrySet()) {
            foundAddr = entry.getKey();
            found = entry.getValue();
            break; // 通常只有一个 pending 连接
        }

        if (found == null) {
            Log.w("CardGame", "[WARN] [蓝牙] RECONNECT 无匹配的 pending 连接");
            return;
        }

        pendingReconnections.remove(foundAddr);

        synchronized (sendLock) {
            // 关闭该 playerId 的旧通道（如果存在）
            String oldAddr = playerIdToDevice.get(claimedPlayerId);
            if (oldAddr != null) {
                SenderReceiverPair oldPair = clientChannels.remove(oldAddr);
                if (oldPair != null) {
                    if (oldPair.receiver != null) oldPair.receiver.stopListening();
                    if (oldPair.sender != null) oldPair.sender.stop();
                }
                deviceToPlayerId.remove(oldAddr);
                lastHeartbeatByAddress.remove(oldAddr);
                pendingByChannel.remove(oldAddr);
            }

            // 建立新通道
            clientChannels.put(foundAddr, found.pair);
            deviceToPlayerId.put(foundAddr, claimedPlayerId);
            playerIdToDevice.put(claimedPlayerId, foundAddr);
            playerNamesById.put(claimedPlayerId,
                    safePlayerName(claimedPlayerName, "Player " + claimedPlayerId));
        }

        // 初始化心跳时间戳
        lastHeartbeatByAddress.put(foundAddr, System.currentTimeMillis());

        // 更新 NetworkGameBridge 远程玩家列表
        updateNetworkBridgeRemotePlayers();

        Log.i("CardGame", "[INFO] [蓝牙] 客户端重连成功 | playerId=" + claimedPlayerId
                + " device=" + foundAddr);

        // 发送 RECONNECT_ACK + 完整游戏状态
        List<String> playerOrder = new ArrayList<>();
        if (cachedGameState != null) {
            Map<String, Integer> cardCounts = new HashMap<>();
            if (cachedGameState.getPlayers() != null) {
                for (com.example.cardgame.model.Player p : cachedGameState.getPlayers()) {
                    if (p != null) {
                        playerOrder.add(p.getPlayerId());
                        cardCounts.put(p.getPlayerId(),
                                p.getHandCards() != null ? p.getHandCards().size() : 0);
                    }
                }
            }

            // 只向重连客户端暴露其自身手牌
            Map<String, List<Card>> filteredHands = new HashMap<>();
            for (com.example.cardgame.model.Player p : cachedGameState.getPlayers()) {
                if (p == null) continue;
                if (p.getPlayerId().equals(claimedPlayerId)) {
                    filteredHands.put(p.getPlayerId(), p.getHandCards());
                } else {
                    filteredHands.put(p.getPlayerId(), new ArrayList<>());
                }
            }

            InitGamePayload syncPayload = new InitGamePayload(
                    filteredHands,
                    playerOrder,
                    cachedGameState.getCurrentPlayerId(),
                    cachedGameState,
                    cardCounts
            );

            BluetoothMessage ack = messageCodec.buildReconnectAckMessage(
                    localPlayerId, claimedPlayerId, syncPayload);
            sendToClient(foundAddr, ack, "RECONNECT_ACK + gameState to " + claimedPlayerId);
        } else {
            BluetoothMessage ack = messageCodec.buildReconnectAckMessage(
                    localPlayerId, claimedPlayerId,
                    new InitGamePayload());
            sendToClient(foundAddr, ack, "RECONNECT_ACK (no state) to " + claimedPlayerId);
        }

        // 通知其他客户端该玩家已重新加入
        int slot = playerOrder.indexOf(claimedPlayerId);
        if (eventListener != null) {
            eventListener.onPlayerJoined(claimedPlayerId,
                    playerNamesById.get(claimedPlayerId), slot);
        }
        broadcastPlayerReconnected(claimedPlayerId, foundAddr, slot);
    }

    private void cleanupStalePendingReconnections() {
        long now = System.currentTimeMillis();
        java.util.Iterator<Map.Entry<String, PendingReconnect>> iter =
                pendingReconnections.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, PendingReconnect> entry = iter.next();
            if (now - entry.getValue().acceptedAt > RECONNECT_PENDING_TIMEOUT_MS) {
                Log.w("CardGame", "[WARN] [蓝牙] 待验证重连超时 | device=" + entry.getKey());
                iter.remove();
                entry.getValue().close();
            }
        }
    }

    private void broadcastPlayerReconnected(String playerId, String excludeAddr, int slot) {
        String playerName = playerNamesById.getOrDefault(playerId, playerId);
        JoinPayload joinPayload = new JoinPayload(playerName, playerId, slot);
        BluetoothMessage msg = messageCodec.buildPlayerJoinedMessage(localPlayerId, "ALL", joinPayload);

        synchronized (sendLock) {
            for (Map.Entry<String, SenderReceiverPair> entry : clientChannels.entrySet()) {
                String addr = entry.getKey();
                if (addr.equals(excludeAddr)) continue;
                try {
                    entry.getValue().sender.sendMessage(msg);
                } catch (Exception e) {
                    Log.e("CardGame", "[ERROR] [蓝牙] 广播重连失败 | to=" + addr, e);
                }
            }
        }
    }

    // ========================================================================
    //  断线重连 — CLIENT 端
    // ========================================================================

    private void startClientReconnect() {
        if (reconnecting) return;
        if (hostDeviceAddress == null) {
            Log.w("CardGame", "[WARN] [蓝牙] 无 HOST 地址，无法重连");
            handleConnectionError("无法重连：缺少HOST设备地址", null);
            return;
        }

        reconnecting = true;
        Log.i("CardGame", "[INFO] [蓝牙] 客户端开始重连 | host=" + hostDeviceAddress);

        Thread reconnectThread = new Thread(() -> {
            int attempt = 0;
            long[] backoff = {2000, 4000, 8000, 16000, 30000};

            while (reconnecting) {
                attempt++;
                long delay = backoff[Math.min(attempt - 1, backoff.length - 1)];

                Log.i("CardGame", "[INFO] [蓝牙] 重连尝试 " + attempt
                        + " | 等待 " + (delay / 1000) + "s");

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (!reconnecting) break;

                try {
                    // 重新连接到 HOST
                    connectionManager.connectToDevice(hostDeviceAddress);

                    // 建立通信通道（复用现有方法）
                    setupCommunicationChannelSingle();

                    // 发送 RECONNECT 消息
                    String playerName = playerNamesById.getOrDefault(localPlayerId, localPlayerId);
                    ReconnectPayload payload = new ReconnectPayload(localPlayerId, playerName);
                    BluetoothMessage reconnectMsg = messageCodec.buildReconnectMessage(
                            localPlayerId, "HOST", payload);
                    sendBluetoothMessageRaw(reconnectMsg, "重连请求 playerId=" + localPlayerId);

                    Log.i("CardGame", "[INFO] [蓝牙] 重连请求已发送 | attempt=" + attempt);

                    // 等待 RECONNECT_ACK（handleReconnectAckMessage 将 reconnecting 置 false）
                    long ackDeadline = System.currentTimeMillis() + 10000;
                    while (reconnecting && System.currentTimeMillis() < ackDeadline) {
                        Thread.sleep(200);
                    }

                    if (!reconnecting) {
                        // handleReconnectAckMessage 已处理成功
                        Log.i("CardGame", "[INFO] [蓝牙] 重连成功 | attempt=" + attempt);
                        return;
                    }

                    // 超时：关闭此次尝试的连接，继续下一轮
                    Log.w("CardGame", "[WARN] [蓝牙] 重连 ACK 超时 | attempt=" + attempt);
                    closeClientChannel();

                } catch (Exception e) {
                    Log.w("CardGame", "[WARN] [蓝牙] 重连尝试失败 | attempt=" + attempt, e);
                    closeClientChannel();
                }
            }

            // 所有重试耗尽
            reconnecting = false;
            Log.e("CardGame", "[ERROR] [蓝牙] 重连失败，所有尝试已耗尽");
            handleConnectionError("重连失败：无法恢复与HOST的连接", null);
        }, "CardGame-ClientReconnect");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    private void closeClientChannel() {
        synchronized (sendLock) {
            List<String> deviceAddresses = new ArrayList<>(clientChannels.keySet());
            for (SenderReceiverPair pair : clientChannels.values()) {
                if (pair.receiver != null) pair.receiver.stopListening();
                if (pair.sender != null) pair.sender.stop();
            }
            clientChannels.clear();
            pendingByChannel.clear();
            lastHeartbeatByAddress.clear();
            for (String deviceAddress : deviceAddresses) {
                connectionManager.closeConnection(deviceAddress);
            }
        }
    }

    private void handleReconnectAckMessage(BluetoothMessage message) {
        Log.i("CardGame", "[INFO] [蓝牙] 收到 RECONNECT_ACK");

        try {
            InitGamePayload payload = messageCodec.decodeInitGamePayload(message.getPayloadJson());

            if (payload.getGameState() != null) {
                // 通过 NetworkGameBridge 重建游戏状态
                BluetoothMessage syntheticInit = new BluetoothMessage(
                        message.getMessageId(),
                        1,
                        MessageType.INIT_GAME,
                        message.getSenderPlayerId(),
                        message.getReceiverPlayerId(),
                        message.getTimestamp(),
                        message.getPayloadJson(),
                        null,
                        0
                );
                networkGameBridge.handleMessage(syntheticInit);
            }

            // 更新设备映射
            String hostAddr = connectionManager.getConnectedDeviceAddress();
            if (hostAddr != null) {
                deviceToPlayerId.put(hostAddr, "P1"); // HOST is P1
                playerIdToDevice.put("P1", hostAddr);
            }

            reconnecting = false;

            if (eventListener != null) {
                eventListener.onConnected(
                        connectionManager.getConnectedDeviceName(),
                        hostAddr
                );
            }

            Log.i("CardGame", "[INFO] [蓝牙] 重连完成，状态已同步");

        } catch (Exception e) {
            Log.e("CardGame", "[ERROR] [蓝牙] 处理RECONNECT_ACK失败", e);
        }
    }

    // ========================================================================
    //  ACK 可靠投递
    // ========================================================================

    private static boolean needsAck(MessageType type) {
        return type == MessageType.INIT_GAME
                || type == MessageType.PLAY_ACTION
                || type == MessageType.PASS_ACTION
                || type == MessageType.GAME_OVER;
    }

    private void sendAckFor(BluetoothMessage original) {
        if (original.getMessageId() == null) return;
        String senderId = original.getSenderPlayerId();
        AckPayload ackPayload = new AckPayload(original.getMessageId());
        BluetoothMessage ack = messageCodec.buildAckMessage(localPlayerId, senderId, ackPayload);
        if (isHost()) {
            // HOST：只回复给消息发送者，不广播
            String senderAddr = playerIdToDevice.get(senderId);
            if (senderAddr != null) {
                sendToClient(senderAddr, ack, "ACK for " + original.getMessageType());
            }
        } else {
            // CLIENT：回复给 HOST
            sendBluetoothMessageRaw(ack, "ACK for " + original.getMessageType());
        }
    }

    private void handleAckMessage(BluetoothMessage message) {
        try {
            AckPayload payload = messageCodec.decodeAckPayload(message.getPayloadJson());
            if (payload == null || payload.getAcknowledgedMessageId() == null) return;

            // 找到匹配的待确认消息并移除
            String ackId = payload.getAcknowledgedMessageId();
            String senderAddr = playerIdToDevice.get(message.getSenderPlayerId());
            if (senderAddr == null && !clientChannels.isEmpty()) {
                senderAddr = clientChannels.keySet().iterator().next();
            }
            if (senderAddr != null) {
                PendingMessage pending = pendingByChannel.get(senderAddr);
                if (pending != null && ackId.equals(pending.message.getMessageId())) {
                    pendingByChannel.remove(senderAddr);
                    Log.d("CardGame", "[DEBUG] [蓝牙] ACK 确认 | device=" + senderAddr
                            + " msgId=" + ackId.substring(0, Math.min(8, ackId.length())));
                }
            }
        } catch (Exception e) {
            Log.w("CardGame", "[WARN] [蓝牙] ACK 解析失败", e);
        }
    }

    private void ackRetryCheck() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, PendingMessage> entry : pendingByChannel.entrySet()) {
            String addr = entry.getKey();
            PendingMessage pending = entry.getValue();
            long elapsed = now - pending.sentAt;
            if (elapsed < ACK_TIMEOUT_MS) continue;

            if (pending.retryCount >= ACK_MAX_RETRIES) {
                Log.e("CardGame", "[ERROR] [蓝牙] ACK 重试耗尽 | device=" + addr
                        + " retries=" + pending.retryCount);
                pendingByChannel.remove(addr);
                handleConnectionError("消息确认超时，通道可能已断开",
                        new IOException("ACK timeout for " + addr));
                return;
            }

            // 重发
            SenderReceiverPair pair;
            synchronized (sendLock) {
                pair = clientChannels.get(addr);
            }
            if (pair == null || pair.sender == null || !pair.sender.isActive()) {
                pendingByChannel.remove(addr);
                continue;
            }
            try {
                pair.sender.sendMessage(pending.message);
                pending.sentAt = now;
                pending.retryCount++;
                Log.w("CardGame", "[WARN] [蓝牙] ACK 重发 | device=" + addr
                        + " retry=" + pending.retryCount
                        + " type=" + pending.message.getMessageType());
            } catch (Exception e) {
                pendingByChannel.remove(addr);
                Log.e("CardGame", "[ERROR] [蓝牙] ACK 重发失败 | device=" + addr, e);
            }
        }
    }

    // ========================================================================
    //  内部类：Sender + Receiver 对
    // ========================================================================

    private static class SenderReceiverPair {
        final BluetoothSender sender;
        final BluetoothReceiver receiver;

        SenderReceiverPair(BluetoothSender sender, BluetoothReceiver receiver) {
            this.sender = sender;
            this.receiver = receiver;
        }
    }
}
