package com.example.cardgame.network;

import com.example.cardgame.network.payload.ErrorPayload;
import com.example.cardgame.network.payload.GameOverPayload;
import com.example.cardgame.network.payload.InitGamePayload;
import com.example.cardgame.network.payload.PassActionPayload;
import com.example.cardgame.network.payload.PlayActionPayload;
import com.google.gson.Gson;

import java.util.UUID;

public class BluetoothMessageCodec {

    private static final int PROTOCOL_VERSION = 1;

    private final Gson gson = new Gson();

    public String encode(BluetoothMessage message) {
        return gson.toJson(message);
    }

    public BluetoothMessage decode(String rawJson) {
        return gson.fromJson(rawJson, BluetoothMessage.class);
    }

    public BluetoothMessage buildInitGameMessage(
            String senderPlayerId,
            String receiverPlayerId,
            InitGamePayload payload
    ) {
        return buildMessage(
                MessageType.INIT_GAME,
                senderPlayerId,
                receiverPlayerId,
                gson.toJson(payload),
                null
        );
    }

    public BluetoothMessage buildPlayActionMessage(
            String senderPlayerId,
            String receiverPlayerId,
            PlayActionPayload payload
    ) {
        return buildMessage(
                MessageType.PLAY_ACTION,
                senderPlayerId,
                receiverPlayerId,
                gson.toJson(payload),
                null
        );
    }

    public BluetoothMessage buildPassActionMessage(
            String senderPlayerId,
            String receiverPlayerId,
            PassActionPayload payload
    ) {
        return buildMessage(
                MessageType.PASS_ACTION,
                senderPlayerId,
                receiverPlayerId,
                gson.toJson(payload),
                null
        );
    }

    public BluetoothMessage buildGameOverMessage(
            String senderPlayerId,
            String receiverPlayerId,
            GameOverPayload payload
    ) {
        return buildMessage(
                MessageType.GAME_OVER,
                senderPlayerId,
                receiverPlayerId,
                gson.toJson(payload),
                null
        );
    }

    public BluetoothMessage buildErrorMessage(
            String senderPlayerId,
            String receiverPlayerId,
            ErrorPayload payload
    ) {
        return buildMessage(
                MessageType.ERROR,
                senderPlayerId,
                receiverPlayerId,
                gson.toJson(payload),
                payload.getErrorMessage()
        );
    }

    public BluetoothMessage buildHeartbeatMessage(
            String senderPlayerId,
            String receiverPlayerId
    ) {
        return buildMessage(
                MessageType.HEARTBEAT,
                senderPlayerId,
                receiverPlayerId,
                "",
                null
        );
    }

    public InitGamePayload decodeInitGamePayload(String payloadJson) {
        return gson.fromJson(payloadJson, InitGamePayload.class);
    }

    public PlayActionPayload decodePlayActionPayload(String payloadJson) {
        return gson.fromJson(payloadJson, PlayActionPayload.class);
    }

    public PassActionPayload decodePassActionPayload(String payloadJson) {
        return gson.fromJson(payloadJson, PassActionPayload.class);
    }

    public GameOverPayload decodeGameOverPayload(String payloadJson) {
        return gson.fromJson(payloadJson, GameOverPayload.class);
    }

    public ErrorPayload decodeErrorPayload(String payloadJson) {
        return gson.fromJson(payloadJson, ErrorPayload.class);
    }

    private BluetoothMessage buildMessage(
            MessageType messageType,
            String senderPlayerId,
            String receiverPlayerId,
            String payloadJson,
            String errorMessage
    ) {
        return new BluetoothMessage(
                UUID.randomUUID().toString(),
                PROTOCOL_VERSION,
                messageType,
                senderPlayerId,
                receiverPlayerId,
                System.currentTimeMillis(),
                payloadJson,
                errorMessage
        );
    }
}