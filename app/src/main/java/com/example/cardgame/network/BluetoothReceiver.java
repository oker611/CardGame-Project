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

    private volatile boolean listening;
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

        if (inputStream == null) {
            notifyReceiveError(new IOException("Bluetooth inputStream is null"));
            return;
        }

        listening = true;

        receiveThread = new Thread(this::receiveLoop, "CardGame-BluetoothReceiver");
        receiveThread.start();

        Log.i("CardGame", "[INFO] [蓝牙] [接收] 接收线程启动 | 状态:started");
    }

    public void stopListening() {
        listening = false;

        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }

        Log.i("CardGame", "[INFO] [蓝牙] [接收] 接收线程停止 | 状态:stopped");
    }

    private void receiveLoop() {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            );

            while (listening) {
                String rawJson = reader.readLine();

                if (rawJson == null) {
                    if (listening) {
                        throw new IOException("Bluetooth input stream closed");
                    }
                    return;
                }

                Log.d("CardGame", "[DEBUG] [蓝牙] [接收] 消息接收 | 内容:" + rawJson);
                handleRawMessage(rawJson);
            }
        } catch (Exception exception) {
            if (listening) {
                listening = false;
                notifyReceiveError(exception);
            } else {
                Log.i("CardGame", "[INFO] [蓝牙] [接收] 主动停止接收 | 原因:receiver stopped");
            }
        }
    }

    private void handleRawMessage(String rawJson) {
        try {
            BluetoothMessage message = messageCodec.decode(rawJson);

            if (message == null || message.getMessageType() == null) {
                throw new IOException("Invalid bluetooth message: " + rawJson);
            }

            if (messageListener != null) {
                messageListener.onMessageReceived(message);
            }
        } catch (Exception exception) {
            notifyReceiveError(exception);
        }
    }

    private void notifyReceiveError(Exception exception) {
        Log.e("CardGame", "[ERROR] [蓝牙] [接收] 接收异常 | 原因:"
                + (exception == null ? "unknown" : exception.getMessage()), exception);

        if (messageListener != null) {
            messageListener.onReceiveError(exception);
        }
    }
}