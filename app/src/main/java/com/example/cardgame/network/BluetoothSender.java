package com.example.cardgame.network;

import android.util.Log;

import com.example.cardgame.util.HermesLog;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class BluetoothSender {

    private final OutputStream outputStream;
    private final BluetoothMessageCodec messageCodec;
    private final BufferedWriter writer;

    private volatile boolean active;

    public BluetoothSender(OutputStream outputStream, BluetoothMessageCodec messageCodec) {
        this.outputStream = outputStream;
        this.messageCodec = messageCodec;
        this.writer = new BufferedWriter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        this.active = true;
    }

    public void sendMessage(BluetoothMessage message) throws IOException {
        if (!active) {
            throw new IOException("BluetoothSender is not active");
        }

        String rawJson = messageCodec.encode(message);
        sendRaw(rawJson);
    }

    public synchronized void sendRaw(String rawJson) throws IOException {
        if (!active) {
            throw new IOException("BluetoothSender is not active");
        }

        HermesLog.log("SEND_RAW len=" + rawJson.length()
                + " preview=" + rawJson.substring(0, Math.min(60, rawJson.length())));

        writer.write(rawJson);
        writer.newLine();
        writer.flush();

        HermesLog.log("SEND_RAW flushed OK len=" + rawJson.length());

        Log.d("CardGame", "[DEBUG] [蓝牙] [发送] 消息发送 | 内容:" + rawJson);
    }

    public boolean isActive() {
        return active;
    }

    public void stop() {
        active = false;
        try {
            writer.close();
        } catch (IOException ignored) {
        }
    }
}
