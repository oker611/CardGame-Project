package com.example.cardgame.model;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class Player {
    private int player_id;
    private int room_id;
    private int game_id;
    private String nickname;
    private String hand_cards;
    private boolean has_diamond_3;
    private int cards_remaining;
    private boolean has_finished;
    private long created_at;

    public Player(int player_id, String nickname) {
        this.player_id = player_id;
        this.nickname = nickname;
        this.created_at = System.currentTimeMillis();
    }

    public void joinGame(int roomId, int gameId) {
        this.room_id = roomId;
        this.game_id = gameId;
    }

    /**
     * Play Cards
     * @param cards 准备出的牌（以 text/String 形式传入，如 "DIAMOND_THREE,SPADE_FIVE"）
     * @return boolean 表示玩家是否确实拥有这些牌并且成功扣除
     */
    public boolean playCards(String cards) {
        if (cards == null || cards.trim().isEmpty()) {
            return false;
        }

        // 解析传入的 cards 文本为数组
        String[] cardsToPlay = cards.split(",");

        // 将玩家当前的 hand_cards 字符串转换为 List 以便操作
        String currentHandStr = (this.hand_cards == null) ? "" : this.hand_cards;
        List<String> currentHandList = new ArrayList<>(Arrays.asList(currentHandStr.split(",")));

        // 校验玩家是否真的拥有要出的这些牌
        for (String card : cardsToPlay) {
            if (!currentHandList.contains(card)) {
                return false; // 试图出没有的牌，失败
            }
        }

        // 从 hand_cards 列表中移除打出的牌
        for (String card : cardsToPlay) {
            currentHandList.remove(card);
        }

        // 将更新后的列表重新拼接为逗号分隔的字符串
        StringBuilder newHandStr = new StringBuilder();
        for (int i = 0; i < currentHandList.size(); i++) {
            newHandStr.append(currentHandList.get(i));
            if (i < currentHandList.size() - 1) {
                newHandStr.append(",");
            }
        }

        this.hand_cards = newHandStr.toString();

        //  扣减 cards_remaining 数量
        this.cards_remaining -= cardsToPlay.length;

        //  如果 cards_remaining == 0，调用 markFinished()
        if (this.cards_remaining == 0) {
            this.markFinished();
        }

        return true;
    }

    public boolean checkDiamond3() {
        return this.has_diamond_3;
    }

    public void markFinished() {
        this.has_finished = true;
    }

    public String getPlayerInfo() {
        return "Player: " + nickname + ", Cards Left: " + cards_remaining;
    }

    public int getPlayer_id() { return player_id; }
    public void setHand_cards(String hand_cards) { this.hand_cards = hand_cards; }
    public String getHand_cards() { return hand_cards; }
    public void setHas_diamond_3(boolean has_diamond_3) { this.has_diamond_3 = has_diamond_3; }
    public void setCards_remaining(int cards_remaining) { this.cards_remaining = cards_remaining; }
}