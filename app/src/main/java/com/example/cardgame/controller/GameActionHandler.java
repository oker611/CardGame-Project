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
}