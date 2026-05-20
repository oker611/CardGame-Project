package com.example.cardgame.network;

import android.util.Log;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 蓝牙可靠消息投递器。
 * <p>
 * 每个 ReliableMessageSender 绑定一条 BluetoothSender（即一路蓝牙连接）。
 * HOST 端每个 CLIENT 各一个实例，CLIENT 端一个实例对应 HOST。
 * <p>
 * 可靠投递流程：
 * <pre>
 *   发送方: sendReliable(msg) → 带 seq → 写入 pending → 等待 ACK
 *   接收方: 收到带 seq 消息 → sendAck(seq) → 处理消息
 *   发送方: 收到 ACK(seq) → 移除 pending
 *   200ms/400ms/800ms 三次重传 → 仍未 ACK → 标记连接死亡
 * </pre>
 */
public class ReliableMessageSender {

    private static final String TAG = "CardGame";
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {200, 400, 800};
    private static final long ACK_TIMEOUT_MS = 3000;
    private static final long RETRY_SCAN_INTERVAL_MS = 200;

    private final BluetoothSender sender;
    private final BluetoothMessageCodec codec;
    private final String localPlayerId;
    private final String remoteDeviceAddr;
    private final Runnable onConnectionDead;

    private final AtomicInteger nextSeq = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, PendingMessage> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = true;

    /**
     * @param sender            底层 BluetoothSender
     * @param codec             消息编解码器
     * @param localPlayerId     本机玩家 ID（用于 ACK 消息的 senderPlayerId）
     * @param remoteDeviceAddr  远端设备地址（仅日志用途）
     * @param onConnectionDead  连接死亡回调（在重传线程调用，注意线程安全）
     */
    public ReliableMessageSender(
            BluetoothSender sender,
            BluetoothMessageCodec codec,
            String localPlayerId,
            String remoteDeviceAddr,
            Runnable onConnectionDead
    ) {
        this.sender = sender;
        this.codec = codec;
        this.localPlayerId = localPlayerId;
        this.remoteDeviceAddr = remoteDeviceAddr;
        this.onConnectionDead = onConnectionDead;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CardGame-Retrier-" + remoteDeviceAddr.substring(
                    Math.max(0, remoteDeviceAddr.length() - 4)));
            t.setDaemon(true);
            return t;
        });

        // 启动重传扫描
        scheduler.scheduleWithFixedDelay(this::scanPendingMessages,
                RETRY_SCAN_INTERVAL_MS, RETRY_SCAN_INTERVAL_MS, TimeUnit.MILLISECONDS);

        Log.i(TAG, "[RELIABLE] 已创建 | target=" + remoteDeviceAddr);
    }

    /**
     * 以可靠模式发送消息。分配序列号，加入重传队列。
     *
     * @param message 待发送消息（其 sequenceNumber 会被覆盖）
     * @return 分配的序列号
     * @throws IOException 首次发送失败时抛出
     */
    public int sendReliable(BluetoothMessage message) throws IOException {
        int seq = nextSeq.getAndIncrement();
        message.setSequenceNumber(seq);

        pending.put(seq, new PendingMessage(message, seq));

        try {
            sender.sendMessage(message);
            Log.d(TAG, "[RELIABLE] 发送 seq=" + seq
                    + " type=" + message.getMessageType()
                    + " to=" + remoteDeviceAddr);
        } catch (IOException e) {
            pending.remove(seq);
            throw e;
        }

        return seq;
    }

    /**
     * 处理收到的 ACK 消息，清除对应 pending 条目。
     *
     * @param ackSeq 已确认的序列号
     */
    public void handleAck(int ackSeq) {
        PendingMessage removed = pending.remove(ackSeq);
        if (removed != null) {
            Log.d(TAG, "[RELIABLE] ACK 收到 seq=" + ackSeq
                    + " target=" + remoteDeviceAddr
                    + " pending=" + pending.size());
        }
    }

    /**
     * 收到对方带 seq 的消息时，发送 ACK 回去。
     *
     * @param receivedSeq 收到的消息序列号
     * @param receiverPlayerId 对方玩家 ID（ACK 的接收方）
     */
    public void sendAckFor(int receivedSeq, String receiverPlayerId) {
        try {
            BluetoothMessage ack = codec.buildAckMessage(localPlayerId, receiverPlayerId, receivedSeq);
            sender.sendMessage(ack);
            Log.d(TAG, "[RELIABLE] 发送ACK seq=" + receivedSeq
                    + " to=" + receiverPlayerId);
        } catch (IOException e) {
            Log.e(TAG, "[RELIABLE] 发送ACK失败 seq=" + receivedSeq, e);
        }
    }

    /**
     * 扫描 pending 消息：超时的重传，超过最大次数的标记死亡。
     */
    private void scanPendingMessages() {
        if (!running) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, PendingMessage>> it = pending.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Integer, PendingMessage> entry = it.next();
            PendingMessage pm = entry.getValue();

            if (now - pm.firstSentTime > ACK_TIMEOUT_MS) {
                if (pm.retryCount >= MAX_RETRIES) {
                    // 达到最大重传次数
                    it.remove();
                    Log.e(TAG, "[RELIABLE] 消息丢失！seq=" + pm.seq
                            + " type=" + pm.message.getMessageType()
                            + " retries=" + pm.retryCount
                            + " target=" + remoteDeviceAddr);

                    if (onConnectionDead != null) {
                        try {
                            onConnectionDead.run();
                        } catch (Exception e) {
                            Log.e(TAG, "[RELIABLE] onConnectionDead 回调异常", e);
                        }
                    }
                } else {
                    // 重传
                    pm.retryCount++;
                    long delay = RETRY_DELAYS_MS[Math.min(pm.retryCount - 1, RETRY_DELAYS_MS.length - 1)];
                    try {
                        sender.sendMessage(pm.message);
                        Log.w(TAG, "[RELIABLE] 重传 seq=" + pm.seq
                                + " retry=" + pm.retryCount
                                + "/" + MAX_RETRIES
                                + " delay=" + delay + "ms"
                                + " target=" + remoteDeviceAddr);
                        // 重置计时（重传后重新计时）
                        pm.firstSentTime = now;
                    } catch (IOException e) {
                        Log.e(TAG, "[RELIABLE] 重传失败 seq=" + pm.seq, e);
                        // 不减 retryCount，下轮再试；但如果 sender 本身坏了，后续也都会失败
                    }
                }
            }
        }
    }

    /**
     * 发送非可靠消息（不分配 seq，不等待 ACK）。
     */
    public void sendUnreliable(BluetoothMessage message) throws IOException {
        sender.sendMessage(message);
    }

    public boolean isActive() {
        return sender.isActive();
    }

    public void shutdown() {
        running = false;
        scheduler.shutdown();
        pending.clear();
        Log.i(TAG, "[RELIABLE] 已关闭 | target=" + remoteDeviceAddr);
    }

    // ========================================================================
    //  内部类
    // ========================================================================

    private static class PendingMessage {
        final BluetoothMessage message;
        final int seq;
        long firstSentTime;
        int retryCount;

        PendingMessage(BluetoothMessage message, int seq) {
            this.message = message;
            this.seq = seq;
            this.firstSentTime = System.currentTimeMillis();
            this.retryCount = 0;
        }
    }
}
