package com.example.cardgame;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.example.cardgame.controller.BluetoothActionHandler;
import com.example.cardgame.controller.BluetoothController;
import com.example.cardgame.controller.GameActionHandler;
import com.example.cardgame.controller.GameController;
import com.example.cardgame.engine.GameEngine;

public class CardGameApplication extends Application {

    private static final String TAG = "CardGame";

    private GameEngine gameEngine;
    private GameActionHandler gameActionHandler;
    private BluetoothActionHandler bluetoothActionHandler;

    @Override
    public void onCreate() {
        super.onCreate();

        gameEngine = new GameEngine();
        gameActionHandler = new GameController(gameEngine);

        Log.d(TAG, "onCreate() - Application initialized, GameActionHandler ready.");
    }

    public GameActionHandler getGameActionHandler() {
        return gameActionHandler;
    }

    public synchronized BluetoothActionHandler getBluetoothActionHandler(Context context) {
        if (bluetoothActionHandler == null) {
            bluetoothActionHandler = new BluetoothController(
                    context.getApplicationContext(),
                    gameEngine
            );
        }
        return bluetoothActionHandler;
    }
}