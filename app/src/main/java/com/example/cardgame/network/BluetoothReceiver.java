package com.example.cardgame.network;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class BluetoothReceiver {

    private final InputStream inputStream;
    private final BluetoothMessageCodec messageCodec;
    private final BluetoothMessageListener messageListener;

    private boolean listening;
    private Thread receiveThread;

    public BluetoothReceiver(
            InputStream inputStream,
            BluetoothMessageCodec messageCodec,
            BluetoothMessageListener messageListener
    ) {
        this.inputStream = inputStream;
        this.messageCodec = messageCodec;
        this.messageListener = messageListener;
    }

    public void startListening() {
        if (listening) {
            return;
        }

        listening = true;

        receiveThread = new Thread(this::receiveLoop);
        receiveThread.start();
    }

    public void stopListening() {
        listening = false;

        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }
    }

    private void receiveLoop() {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            );

            while (listening) {
                String rawJson = reader.readLine();

                if (rawJson == null) {
                    throw new IOException("Bluetooth input stream closed");
                }

                Log.d("CardGame", "[DEBUG] [蓝牙] [接收] 消息接收 | 内容:" + rawJson);
                handleRawMessage(rawJson);
            }
        } catch (Exception exception) {
            listening = false;
            messageListener.onReceiveError(exception);
        }
    }

    private void handleRawMessage(String rawJson) {
        try {
            BluetoothMessage message = messageCodec.decode(rawJson);
            messageListener.onMessageReceived(message);
        } catch (Exception exception) {
            messageListener.onReceiveError(exception);
        }
    }
}