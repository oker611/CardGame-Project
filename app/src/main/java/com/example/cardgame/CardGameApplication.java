package com.example.cardgame;

import android.app.Application;

import com.example.cardgame.controller.GameActionHandler;
import com.example.cardgame.controller.GameController;
import com.example.cardgame.engine.GameEngine;

public class CardGameApplication extends Application {

    private static GameActionHandler gameActionHandler;

    @Override
    public void onCreate() {
        super.onCreate();

        GameEngine engine = new GameEngine();
        gameActionHandler = new GameController(engine);

        System.out.println("[CardGame][APP] Application initialized, GameActionHandler ready.");
    }

    public static GameActionHandler getGameActionHandler() {
        return gameActionHandler;
    }
}