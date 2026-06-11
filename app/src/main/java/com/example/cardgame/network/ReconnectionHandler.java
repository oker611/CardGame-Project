package com.example.cardgame.network;

import android.util.Log;

import com.example.cardgame.model.GameState;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 断线重连处理器：管理 HOST 端重连监听线程和 CLIENT 端重连尝试。
 * 共享 BluetoothGateway 的通道和状态 Map。
 */
public class ReconnectionHandler {

    private static final String TAG = "CardGame";
    private static final long PENDING_TIMEOUT_MS = 10000;

    private final BluetoothConnectionManager connectionManager;
    private final BluetoothMessageCodec messageCodec;
    private final BluetoothMessageListener messageListener;
    private final Map<String, BluetoothGateway.SenderReceiverPair> clientChannels;
    private final ConcurrentHashMap<String, BluetoothGateway.PendingReconnect> pendingReconnections;

    private Thread listenerThread;
    private volatile boolean accepting;

    public ReconnectionHandler(
            BluetoothConnectionManager connectionManager,
            BluetoothMessageCodec messageCodec,
            BluetoothMessageListener messageListener,
            Map<String, BluetoothGateway.SenderReceiverPair> clientChannels,
            ConcurrentHashMap<String, BluetoothGateway.PendingReconnect> pendingReconnections) {
        this.connectionManager = connectionManager;
        this.messageCodec = messageCodec;
        this.messageListener = messageListener;
        this.clientChannels = clientChannels;
        this.pendingReconnections = pendingReconnections;
    }

    // ========== HOST: 监听重连 ==========

    public void startListener() {
        if (listenerThread != null) return;
        accepting = true;
        listenerThread = new Thread(() -> {
            Log.i(TAG, "[INFO] 重连监听已启动");
            while (accepting && connectionManager.isServerSocketOpen()) {
                try {
                    BluetoothConnectionManager.ClientConnection conn =
                            connectionManager.acceptRawConnection();
                    if (conn == null) continue;

                    BluetoothSender sender = new BluetoothSender(conn.outputStream, messageCodec);
                    BluetoothReceiver receiver = new BluetoothReceiver(
                            conn.inputStream, messageCodec, messageListener);
                    receiver.startListening();

                    BluetoothGateway.SenderReceiverPair pair =
                            new BluetoothGateway.SenderReceiverPair(sender, receiver);
                    pendingReconnections.put(conn.deviceAddress,
                            new BluetoothGateway.PendingReconnect(pair, conn.deviceAddress));
                    Log.i(TAG, "[INFO] 重连请求待验证 device=" + conn.deviceAddress);
                } catch (IOException e) {
                    if (accepting) Log.w(TAG, "[WARN] 重连监听 accept 异常", e);
                }
            }
            Log.i(TAG, "[INFO] 重连监听已退出");
        }, "CardGame-ReconnectListener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void stopListener() {
        accepting = false;
        connectionManager.interruptAccept();
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
        for (BluetoothGateway.PendingReconnect pr : pendingReconnections.values()) pr.close();
        pendingReconnections.clear();
    }

    // ========== 连接查找 ==========

    public String findPendingAddress() {
        for (String addr : pendingReconnections.keySet()) return addr;
        return null;
    }

    public BluetoothGateway.PendingReconnect removePending(String address) {
        return pendingReconnections.remove(address);
    }

    // ========== 过期清理 ==========

    public void cleanupStale() {
        long now = System.currentTimeMillis();
        pendingReconnections.entrySet().removeIf(entry -> {
            if (now - entry.getValue().acceptedAt > PENDING_TIMEOUT_MS) {
                entry.getValue().close();
                Log.w(TAG, "[WARN] 过期重连已清理 device=" + entry.getKey());
                return true;
            }
            return false;
        });
    }
}
