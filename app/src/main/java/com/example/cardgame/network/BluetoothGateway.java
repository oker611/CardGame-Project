package com.example.cardgame.network;

import android.content.Context;
import android.util.Log;

import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.network.payload.GameOverPayload;
import com.example.cardgame.network.payload.InitGamePayload;
import com.example.cardgame.network.payload.JoinPayload;
import com.example.cardgame.network.payload.PassActionPayload;
import com.example.cardgame.network.payload.PlayActionPayload;
import com.example.cardgame.network.payload.PlayerLeftPayload;
import com.example.cardgame.network.payload.AckPayload;

import com.example.cardgame.util.HermesLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BluetoothGateway implements MultiplayerGateway, BluetoothMessageListener {

    private static final String[] CLIENT_PLAYER_IDS = {"P2", "P3", "P4"};
    private static final int MAX_CLIENTS = CLIENT_PLAYER_IDS.length;

    private final BluetoothConnectionManager connectionManager;
    private final BluetoothMessageCodec messageCodec;
    private final NetworkGameBridge networkGameBridge;

    private BluetoothEventListener eventListener;

    private String localPlayerId;

    // ——— 多路连接状态 ———
    /** deviceAddress → playerId (如 "P2", "P3", "P4") */
    private final Map<String, String> deviceToPlayerId = new ConcurrentHashMap<>();
    /** deviceAddress → SenderReceiverPair */
    private final Map<String, SenderReceiverPair> clientChannels = new ConcurrentHashMap<>();
    /** playerId → deviceAddress（反向查找） */
    private final Map<String, String> playerIdToDevice = new ConcurrentHashMap<>();
    /** deviceAddress → ReliableMessageSender（可靠投递） */
    private final Map<String, ReliableMessageSender> reliableSenders = new ConcurrentHashMap<>();

    private String role;
    private volatile boolean communicationReady = false;
    private volatile boolean acceptingClients = false;

    /** 锁对象：保护 send/disconnect 之间的 TOCTOU 竞态 */
    private final Object sendLock = new Object();

    /** 房间已通过 readyForGame() 主动结束（AI 补齐），startAsHost 线程退出时跳过错误处理 */
    private volatile boolean roomFinalized = false;

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

    public void startAsHost(String localPlayerId) {
        this.localPlayerId = localPlayerId;
        this.role = "HOST";
        HermesLog.init("host");
        this.communicationReady = false;
        this.acceptingClients = true;
        this.deviceToPlayerId.clear();
        this.clientChannels.clear();
        this.playerIdToDevice.clear();
        this.reliableSenders.clear();

        networkGameBridge.setPlayerContext(this.localPlayerId, new ArrayList<>());

        try {
            Log.i("CardGame", "[INFO] [蓝牙] 创建房间（4人模式） | 本机玩家:" + localPlayerId);

            // 第一步：创建服务端 Socket（非阻塞），此时房间已可被发现
            connectionManager.startServer();

            // 通知 UI：房间已创建，等待玩家加入
            notifyServerReady();

            // 第二步：依次接受 3 个客户端连接
            for (int i = 0; i < MAX_CLIENTS; i++) {
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

                // 创建可靠投递通道
                ReliableMessageSender reliable = new ReliableMessageSender(
                        sender, messageCodec, localPlayerId, deviceAddress,
                        () -> onReliableDeliveryFailed(deviceAddress, clientPlayerId));
                reliableSenders.put(deviceAddress, reliable);

                // 发送 JOIN_ACK 给新客户端
                String playerName = conn.deviceName != null ? conn.deviceName : "Player";
                JoinPayload ackPayload = new JoinPayload(playerName, clientPlayerId, i + 1);
                BluetoothMessage ackMessage = messageCodec.buildJoinAckMessage(
                        localPlayerId, clientPlayerId, ackPayload);
                sender.sendMessage(ackMessage);

                // 告知新客户端房间内已有玩家
                sendExistingPlayersToNewClient(sender, clientPlayerId);

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
            handleConnectionError("创建蓝牙房间失败", exception);
        }
    }

    // ========================================================================
    //  CLIENT 模式：连接房间
    // ========================================================================

    public void connectAsClient(String localPlayerId, String deviceAddress) {
        this.localPlayerId = localPlayerId;
        this.role = "CLIENT";
        HermesLog.init("client");
        this.communicationReady = false;
        this.assignedPlayerId = null;
        this.assignedSlotIndex = -1;

        networkGameBridge.setPlayerContext(this.localPlayerId, new ArrayList<>());

        try {
            Log.i("CardGame", "[INFO] [蓝牙] 发起连接 | 目标设备:" + deviceAddress);

            connectionManager.connectToDevice(deviceAddress);
            setupCommunicationChannelSingle();

            // 发送 JOIN 请求（使用本机蓝牙名）
            String playerName = connectionManager.getLocalDeviceName();
            JoinPayload joinPayload = new JoinPayload(playerName);
            BluetoothMessage joinMessage = messageCodec.buildJoinMessage(
                    localPlayerId, "HOST", joinPayload);
            sendBluetoothMessageRaw(joinMessage, "请求加入房间");

            notifyConnected();

            Log.i("CardGame", "[INFO] [蓝牙] 客户端已连接，等待HOST分配角色...");

        } catch (Exception exception) {
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

            // 创建可靠投递通道
            ReliableMessageSender reliable = new ReliableMessageSender(
                    pair.sender, messageCodec, localPlayerId, deviceAddress,
                    () -> onReliableDeliveryFailed(deviceAddress, "HOST"));
            reliableSenders.put(deviceAddress, reliable);
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

        HermesLog.log("SYNC players=" + (gameState.getPlayers() != null ? gameState.getPlayers().size() : 0)
                + " cr=" + communicationReady + " ch=" + clientChannels.size());

        // 填充多玩家手牌映射
        Map<String, List<Card>> handMap = new HashMap<>();
        List<String> playerOrder = new ArrayList<>();
        if (gameState.getPlayers() != null) {
            for (com.example.cardgame.model.Player p : gameState.getPlayers()) {
                if (p != null) {
                    handMap.put(p.getPlayerId(), p.getHandCards());
                    playerOrder.add(p.getPlayerId());
                }
            }
        }

        InitGamePayload payload = new InitGamePayload(
                handMap,
                playerOrder,
                gameState.getCurrentPlayerId(),
                gameState
        );

        BluetoothMessage message = messageCodec.buildInitGameMessage(
                localPlayerId, "ALL", payload);

        sendBluetoothMessage(message, "同步游戏状态 currentPlayer:" + gameState.getCurrentPlayerId());
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

        for (Map.Entry<String, SenderReceiverPair> entry : clientChannels.entrySet()) {
            String addr = entry.getKey();
            String existingPlayer = deviceToPlayerId.get(addr);
            // 不给新加入的玩家自己发
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

    /**
     * 告知新加入的客户端房间内已有玩家的信息。
     * 必须在 clientChannels 已包含新客户端、deviceToPlayerId 已建立映射后调用。
     */
    private void sendExistingPlayersToNewClient(BluetoothSender newClientSender, String newPlayerId) {
        for (Map.Entry<String, String> entry : deviceToPlayerId.entrySet()) {
            String existingAddr = entry.getKey();
            String existingPlayerId = entry.getValue();
            if (existingPlayerId.equals(newPlayerId)) {
                continue;
            }

            BluetoothConnectionManager.ClientConnection conn = connectionManager.getConnection(existingAddr);
            String existingName = conn != null ? conn.deviceName : "Player";
            int slot = playerIdToSlotNumber(existingPlayerId);

            JoinPayload payload = new JoinPayload(existingName, existingPlayerId, slot);
            BluetoothMessage msg = messageCodec.buildPlayerJoinedMessage(
                    localPlayerId, "ALL", payload);
            try {
                newClientSender.sendMessage(msg);
                Log.i("CardGame", "[INFO] [蓝牙] 发送已有玩家 " + existingPlayerId + " 信息给新客户端 " + newPlayerId);
            } catch (Exception e) {
                Log.e("CardGame", "[ERROR] [蓝牙] 发送已有玩家 " + existingPlayerId + " 失败", e);
            }
        }
    }

    private int playerIdToSlotNumber(String playerId) {
        switch (playerId) {
            case "P2": return 1;
            case "P3": return 2;
            case "P4": return 3;
            default: return -1;
        }
    }

    private void updateNetworkBridgeRemotePlayers() {
        List<String> remoteIds = new ArrayList<>(playerIdToDevice.keySet());
        networkGameBridge.setPlayerContext(localPlayerId, remoteIds);
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

            boolean isGameMessage = isGameMessageType(message.getMessageType());

            for (Map.Entry<String, SenderReceiverPair> entry : clientChannels.entrySet()) {
                try {
                    String deviceAddress = entry.getKey();
                    SenderReceiverPair pair = entry.getValue();

                    if (pair.sender == null || !pair.sender.isActive()) {
                        continue;
                    }

                    if (isGameMessage) {
                        ReliableMessageSender reliable = reliableSenders.get(deviceAddress);
                        if (reliable != null) {
                            reliable.sendReliable(message);
                        } else {
                            pair.sender.sendMessage(message);
                        }
                    } else {
                        pair.sender.sendMessage(message);
                    }

                    Log.d("CardGame", "[DEBUG] [蓝牙] [发送] 消息已广播 | 类型:"
                            + message.getMessageType()
                            + " 到:" + deviceAddress
                            + " 内容:" + summary);

                } catch (Exception exception) {
                    Log.e("CardGame", "[ERROR] [蓝牙] [发送] 广播失败 | " + summary, exception);
                }
            }

            if (eventListener != null) {
                eventListener.onMessageSent(message.getMessageType(), summary);
            }
        }
    }

    private boolean isGameMessageType(MessageType type) {
        return type == MessageType.PLAY_ACTION
                || type == MessageType.PASS_ACTION
                || type == MessageType.INIT_GAME
                || type == MessageType.GAME_OVER;
    }

    /**
     * CLIENT 模式：仅发送到 HOST（不走广播循环）。
     */
    private void sendBluetoothMessageRaw(BluetoothMessage message, String summary) {
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

            if (eventListener != null) {
                eventListener.onMessageSent(message.getMessageType(), summary);
            }
        } catch (Exception exception) {
            handleConnectionError("发送蓝牙消息失败: " + summary, exception);
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

        // ——— 可靠投递：对带序列号的消息自动 ACK ———
        // 星型拓扑中，所有消息都经过 HOST 的可靠通道投递。
        // CLIENT 收到转发消息时 senderPlayerId 是原始发送者（非 HOST），
        // 但实际的可靠通道是 HOST↔CLIENT。此时 playerIdToDevice 查不到
        // 原始发送者，fallback 到唯一的 reliable sender 将 ACK 发回 HOST，
        // HOST 的 handleAck 根据 ACK 的 senderPlayerId 路由到正确的通道。
        int seq = message.getSequenceNumber();
        if (seq > 0 && message.getMessageType() != MessageType.ACK) {
            String senderDevice = playerIdToDevice.get(message.getSenderPlayerId());
            if (senderDevice != null) {
                ReliableMessageSender reliable = reliableSenders.get(senderDevice);
                if (reliable != null) {
                    reliable.sendAckFor(seq, message.getSenderPlayerId());
                }
            } else if (!isHost()) {
                // CLIENT 模式：只有一路连接到 HOST，fallback 发 ACK 回 HOST
                ReliableMessageSender reliable = reliableSenders.values().stream().findFirst().orElse(null);
                if (reliable != null) {
                    reliable.sendAckFor(seq, message.getSenderPlayerId());
                }
            }
        }

        if (eventListener != null) {
            eventListener.onMessageReceived(
                    message.getMessageType(),
                    "来自:" + message.getSenderPlayerId()
            );
        }

        // 处理新消息类型
        switch (message.getMessageType()) {
            case JOIN:
                handleJoinMessage(message);
                break;

            case JOIN_ACK:
                handleJoinAckMessage(message);
                break;

            case PLAYER_JOINED:
                handlePlayerJoinedMessage(message);
                break;

            case PLAYER_LEFT:
                handlePlayerLeftMessage(message);
                break;

            case ACK:
                handleAckMessage(message);
                break;

            default:
                // 所有游戏相关消息（INIT_GAME, PLAY_ACTION, PASS_ACTION, GAME_OVER, ERROR, HEARTBEAT）
                // HOST 模式下：广播给所有客户端（含发送者，实现 CLIENT 端确认回环）
                if (isHost() && communicationReady) {
                    broadcastToAllClients(message);
                }

                // 交给 bridge 处理
                networkGameBridge.handleMessage(message);
                break;
        }
    }

    private void handleJoinMessage(BluetoothMessage message) {
        Log.d("CardGame", "[DEBUG] [蓝牙] 收到JOIN请求 from=" + message.getSenderPlayerId());
        // HOST 端通过 accept 循环处理，此方法作为备用
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

            // 同步 NetworkGameBridge（否则 bridge 的 localPlayerId 仍为 "CLIENT"，导致 configurePlayerTypes 出错）
            networkGameBridge.setPlayerContext(assignedPlayerId, new ArrayList<>());

            Log.i("CardGame", "[INFO] [蓝牙] HOST分配角色: " + assignedPlayerId
                    + ", slot=" + assignedSlotIndex);

            if (eventListener != null) {
                eventListener.onPlayerAssigned(assignedPlayerId, assignedSlotIndex);
            }

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
     * 处理收到的 ACK 消息，告知 ReliableMessageSender 清除 pending。
     * ACK 的 senderPlayerId 是发送 ACK 的客户端，据此路由到对应的可靠通道。
     */
    private void handleAckMessage(BluetoothMessage message) {
        try {
            AckPayload payload = messageCodec.decodeAckPayload(message.getPayloadJson());
            int ackSeq = payload.getAcknowledgedSeq();
            String senderPlayerId = message.getSenderPlayerId();
            String senderDevice = playerIdToDevice.get(senderPlayerId);

            if (senderDevice != null) {
                ReliableMessageSender reliable = reliableSenders.get(senderDevice);
                if (reliable != null) {
                    reliable.handleAck(ackSeq);
                }
            } else if (!isHost()) {
                // CLIENT 模式
                ReliableMessageSender reliable = reliableSenders.values().stream().findFirst().orElse(null);
                if (reliable != null) {
                    reliable.handleAck(ackSeq);
                }
            }
        } catch (Exception e) {
            Log.e("CardGame", "[ERROR] [蓝牙] 解析ACK失败", e);
        }
    }

    /**
     * 可靠投递失败（已重传3次仍无 ACK），标记该连接死亡。
     */
    private void onReliableDeliveryFailed(String deviceAddress, String playerId) {
        Log.e("CardGame", "[RELIABLE] 连接死亡 | device=" + deviceAddress + " player=" + playerId);
        if (eventListener != null) {
            eventListener.onError("蓝牙连接丢失: " + playerId, null);
        }
    }

    /**
     * HOST 模式下：将收到的消息广播给所有客户端（含发送者，实现 CLIENT 端确认回环）。
     */
    private void broadcastToAllClients(BluetoothMessage originalMessage) {
        for (Map.Entry<String, String> entry : deviceToPlayerId.entrySet()) {
            String targetDevice = entry.getKey();
            String targetPlayerId = entry.getValue();

            SenderReceiverPair pair = clientChannels.get(targetDevice);
            if (pair == null || pair.sender == null || !pair.sender.isActive()) {
                continue;
            }

            try {
                ReliableMessageSender reliable = reliableSenders.get(targetDevice);
                if (reliable != null) {
                    reliable.sendReliable(originalMessage);
                } else {
                    pair.sender.sendMessage(originalMessage);
                }

                Log.d("CardGame", "[DEBUG] [蓝牙] 消息已广播 | 类型:"
                        + originalMessage.getMessageType()
                        + " 来自:" + originalMessage.getSenderPlayerId()
                        + " 到:" + targetPlayerId);

            } catch (Exception e) {
                Log.e("CardGame", "[ERROR] [蓝牙] 广播失败 | to=" + targetPlayerId, e);
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
        handleConnectionError("蓝牙接收数据失败", exception);
    }

    public boolean isConnected() {
        return connectionManager.isConnected();
    }

    public boolean isHost() {
        return "HOST".equals(role);
    }

    public boolean isCommunicationReady() {
        return communicationReady;
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

    public void readyForGame() {
        HermesLog.log("READY readyForGame communicationReady=" + communicationReady
                + " channels=" + clientChannels.size());
        this.communicationReady = true;
        this.acceptingClients = false;
        this.roomFinalized = true;
        connectionManager.closeServerSocket();
        HermesLog.log("READY done");
    }

    public void notifyAiPlayerAdded(String playerId, int slotIndex) {
        HermesLog.log("AI_ADD " + playerId + " slot=" + slotIndex + " channels=" + clientChannels.size());
        if (clientChannels.isEmpty()) return;

        JoinPayload joinPayload = new JoinPayload("AI player", playerId, slotIndex);
        BluetoothMessage msg = messageCodec.buildPlayerJoinedMessage(localPlayerId, "ALL", joinPayload);

        for (Map.Entry<String, SenderReceiverPair> entry : clientChannels.entrySet()) {
            try {
                entry.getValue().sender.sendMessage(msg);
            } catch (Exception e) {
                Log.e("CardGame", "[ERROR] broadcast AI failed", e);
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

            for (ReliableMessageSender reliable : reliableSenders.values()) {
                reliable.shutdown();
            }

            clientChannels.clear();
            reliableSenders.clear();
            deviceToPlayerId.clear();
            playerIdToDevice.clear();
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
                    "Player " + playerId,
                    slotIndex);
        }
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

        Log.e("CardGame", "[ERROR] [蓝牙] 连接断开 | 原因:" + message, exception);

        if (eventListener != null) {
            eventListener.onDisconnected(message);
            eventListener.onError(message, exception);
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
