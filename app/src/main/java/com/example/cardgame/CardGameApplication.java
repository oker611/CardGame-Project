package com.example.cardgame;

import android.app.Application;
import android.content.Context;

import com.example.cardgame.controller.BluetoothActionHandler;
import com.example.cardgame.controller.BluetoothController;
import com.example.cardgame.controller.GameActionHandler;
import com.example.cardgame.controller.GameController;
import com.example.cardgame.engine.GameEngine;

public class CardGameApplication extends Application {

    private static GameEngine gameEngine;
    private static GameActionHandler gameActionHandler;
    private static BluetoothActionHandler bluetoothActionHandler;

    @Override
    public void onCreate() {
        super.onCreate();

        gameEngine = new GameEngine();
        gameActionHandler = new GameController(gameEngine);

        System.out.println("[CardGame][APP] Application initialized, GameActionHandler ready.");
    }

    public static GameActionHandler getGameActionHandler() {
        return gameActionHandler;
    }

    public static GameEngine getGameEngine() {
        return gameEngine;
    }

    public static synchronized BluetoothActionHandler getBluetoothActionHandler(Context context) {
        if (bluetoothActionHandler == null) {
            bluetoothActionHandler = new BluetoothController(
                    context.getApplicationContext(),
                    gameEngine
            );
        }
        return bluetoothActionHandler;
    }
}