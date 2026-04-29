package com.example.cardgame.controller;

import com.example.cardgame.dto.GameViewData;
import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;

import java.util.List;

public interface GameActionHandler {

    void startNewGame();

    PlayResult submitPlay(List<String> selectedCardIds);

    PassResult passTurn();

    void toggleCardSelection(String cardId);

    GameViewData getGameViewData();

    /**
     * UI 刷新回调。
     *
     * 用途：
     * AI 自动出牌 / 自动 Pass 后，Controller 可以主动通知 Activity 刷新界面。
     *
     * default 空实现是为了兼容其他实现类。
     */
    default void setUiRefreshCallback(Runnable callback) {
        // default no-op
    }
}