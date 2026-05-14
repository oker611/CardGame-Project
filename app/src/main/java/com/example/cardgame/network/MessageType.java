package com.example.cardgame.network;

public enum MessageType {
    INIT_GAME,
    PLAY_ACTION,
    PASS_ACTION,
    GAME_OVER,
    HEARTBEAT,
    ERROR,
    JOIN,
    JOIN_ACK,
    PLAYER_JOINED,
    PLAYER_LEFT
}