package com.example.cardgame.util;

public final class Logger {

    private static final String TAG = "CardGame";

    private Logger() {
        // Utility class
    }

    public static void log(String category, String message) {
        System.out.println("[" + TAG + "][" + category + "] " + message);
    }

    public static void start(String message) {
        log("START", message);
    }

    public static void deal(String message) {
        log("DEAL", message);
    }

    public static void turn(String message) {
        log("TURN", message);
    }

    public static void play(String message) {
        log("PLAY", message);
    }

    public static void validation(String message) {
        log("VALIDATION", message);
    }

    public static void pass(String message) {
        log("PASS", message);
    }

    public static void win(String message) {
        log("WIN", message);
    }

    public static void controller(String message) {
        log("CONTROLLER", message);
    }

    public static void error(String message) {
        log("ERROR", message);
    }
}