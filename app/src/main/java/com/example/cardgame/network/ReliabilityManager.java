package com.example.cardgame.network;

import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 可靠性管理器：心跳调度、超时检测、ACK 重试。
 * 通过回调与 BluetoothGateway 交互，不直接操作 I/O。
 */
public class ReliabilityManager {

    private static final String TAG = "CardGame";
    private static final long INTERVAL_SEC = 5;
    private static final long TIMEOUT_SEC = 15;
    private static final long ACK_TIMEOUT_MS = 3000;
    private static final int ACK_MAX_RETRIES = 3;

    private final ConcurrentHashMap<String, Long> lastHeartbeatByAddress;
    private final ConcurrentHashMap<String, BluetoothGateway.PendingMessage> pendingByChannel;

    private ScheduledExecutorService executor;
    private volatile boolean running;

    // 回调：由 BluetoothGateway 注入实际操作
    private Runnable onHeartbeatTick;
    private ChannelTimeoutHandler onChannelTimeout;

    public interface ChannelTimeoutHandler {
        void onTimeout(String deviceAddress);
    }

    public ReliabilityManager(
            ConcurrentHashMap<String, Long> lastHeartbeatByAddress,
            ConcurrentHashMap<String, BluetoothGateway.PendingMessage> pendingByChannel) {
        this.lastHeartbeatByAddress = lastHeartbeatByAddress;
        this.pendingByChannel = pendingByChannel;
    }

    public void setOnHeartbeatTick(Runnable r) { this.onHeartbeatTick = r; }
    public void setOnChannelTimeout(ChannelTimeoutHandler h) { this.onChannelTimeout = h; }

    // ========== 生命周期 ==========

    public void start() {
        if (running) return;
        running = true;
        executor = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "CardGame-Heartbeat"));
        executor.scheduleAtFixedRate(this::tick, INTERVAL_SEC, INTERVAL_SEC, TimeUnit.SECONDS);
    }

    public void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        lastHeartbeatByAddress.clear();
        pendingByChannel.clear();
    }

    // ========== 心跳追踪 ==========

    public void recordHeartbeat(String deviceAddress) {
        lastHeartbeatByAddress.put(deviceAddress, System.currentTimeMillis());
    }

    public void removeChannel(String deviceAddress) {
        lastHeartbeatByAddress.remove(deviceAddress);
    }

    // ========== ACK 追踪 ==========

    public void trackPending(String deviceAddress, BluetoothMessage message) {
        if (!isAckableType(message.getMessageType())) return;
        pendingByChannel.put(deviceAddress, new BluetoothGateway.PendingMessage(message));
    }

    public void ackReceived(String deviceAddress) {
        pendingByChannel.remove(deviceAddress);
    }

    public void removePending(String deviceAddress) {
        pendingByChannel.remove(deviceAddress);
    }

    public boolean needsAck(MessageType type) { return isAckableType(type); }

    private static boolean isAckableType(MessageType type) {
        return type == MessageType.INIT_GAME || type == MessageType.PLAY_ACTION
                || type == MessageType.PASS_ACTION || type == MessageType.GAME_OVER;
    }

    // ========== ACK 重试用 Pending 访问 ==========

    public BluetoothGateway.PendingMessage getPending(String deviceAddress) {
        return pendingByChannel.get(deviceAddress);
    }

    public Map<String, BluetoothGateway.PendingMessage> getPendingMap() {
        return pendingByChannel;
    }

    // ========== 定时任务 ==========

    private void tick() {
        if (!running) return;
        long now = System.currentTimeMillis();
        long timeoutMs = TIMEOUT_SEC * 1000;

        // 1. 心跳发送（委托给 BluetoothGateway）
        if (onHeartbeatTick != null) onHeartbeatTick.run();

        // 2. 心跳超时检测（按设备地址逐路检查）
        for (Map.Entry<String, Long> entry : lastHeartbeatByAddress.entrySet()) {
            if ((now - entry.getValue()) > timeoutMs) {
                Log.e(TAG, "[ERROR] 心跳超时 device=" + entry.getKey());
                if (onChannelTimeout != null) onChannelTimeout.onTimeout(entry.getKey());
            }
        }

        // 3. ACK 重试
        for (Map.Entry<String, BluetoothGateway.PendingMessage> entry : pendingByChannel.entrySet()) {
            String addr = entry.getKey();
            BluetoothGateway.PendingMessage pending = entry.getValue();
            if ((now - pending.sentAt) > ACK_TIMEOUT_MS) {
                if (pending.retryCount < ACK_MAX_RETRIES) {
                    pending.retryCount++;
                    pending.sentAt = now;
                    Log.w(TAG, "[WARN] ACK 重试 device=" + addr + " retry=" + pending.retryCount);
                } else {
                    Log.e(TAG, "[ERROR] ACK 重试耗尽 device=" + addr);
                    pendingByChannel.remove(addr);
                }
            }
        }
    }
}
