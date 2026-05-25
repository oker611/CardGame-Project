package com.example.cardgame.network;

import android.util.Log;

import com.example.cardgame.util.HermesLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class BluetoothReceiver {

    private static final int READ_BUFFER_SIZE = 1024;
    private static final int MAX_LINE_BYTES = 256 * 1024;

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

        HermesLog.log("RECV thread started (raw byte mode)");
    }

    public void stopListening() {
        listening = false;

        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }

        HermesLog.log("RECV thread stopped");
    }

    private void receiveLoop() {
        byte[] buffer = new byte[READ_BUFFER_SIZE];
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();

        try {
            while (listening) {
                int bytesRead = inputStream.read(buffer);

                if (bytesRead == -1) {
                    if (listening) {
                        throw new IOException("Bluetooth input stream closed (read returned -1)");
                    }
                    return;
                }

                for (int i = 0; i < bytesRead; i++) {
                    byte b = buffer[i];

                    if (b == '\n') {
                        String rawJson = lineBuffer.toString("UTF-8");
                        lineBuffer.reset();

                        if (rawJson.endsWith("\r")) {
                            rawJson = rawJson.substring(0, rawJson.length() - 1);
                        }

                        if (!rawJson.isEmpty()) {
                            if (rawJson.contains("INIT_GAME")) {
                                HermesLog.log("RECV INIT_GAME len=" + rawJson.length());
                            }
                            handleRawMessage(rawJson);
                        }
                    } else {
                        lineBuffer.write(b);

                        if (lineBuffer.size() > MAX_LINE_BYTES) {
                            lineBuffer.reset();
                            throw new IOException(
                                    "Bluetooth message line exceeds max size (" + MAX_LINE_BYTES + " bytes)");
                        }
                    }
                }
            }
        } catch (Exception exception) {
            if (listening) {
                listening = false;
                HermesLog.log("RECV ERROR type="
                        + (exception != null ? exception.getClass().getSimpleName() : "null")
                        + " msg=" + (exception != null ? exception.getMessage() : "null"));
                notifyReceiveError(exception);
            } else {
                HermesLog.log("RECV stopped normally");
            }
        } finally {
            try {
                lineBuffer.close();
            } catch (IOException ignored) {
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
        String exceptionType = exception != null ? exception.getClass().getSimpleName() : "null";
        String exceptionMsg = exception != null ? exception.getMessage() : "unknown";

        HermesLog.log("RECV notifyError type=" + exceptionType + " msg=" + exceptionMsg);

        if (messageListener != null) {
            messageListener.onReceiveError(exception);
        }
    }
}
