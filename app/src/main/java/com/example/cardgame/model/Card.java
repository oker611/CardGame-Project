package com.example.cardgame.model;

public class Card {
    private int card_id;             // PK
    private int game_id;             // FK
    private int player_id;           // FK
    private String suit;             // 花色
    private String rank;             // 点数
    private String unique_identifier;// 唯一标识

    public Card(int card_id, String suit, String rank) {
        this.card_id = card_id;
        this.suit = suit;
        this.rank = rank;
        this.unique_identifier = suit + "_" + rank;
    }

    public void bindGame(int gameId) {
        this.game_id = gameId;
    }

    public void assignToPlayer(int playerId) {
        this.player_id = playerId;
    }

    public String getCardInfo() {
        return this.unique_identifier;
    }

    public String getSuit() { return suit; }
    public String getRank() { return rank; }
    public String getUnique_identifier()
    {
        return
                unique_identifier;
    }
}
