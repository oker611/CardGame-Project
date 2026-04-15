package com.example.cardgame.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Game {
    private int game_id;
    private int room_id;
    private String status;
    private int current_player_id;
    private int first_player_id;
    private String last_play_type;
    private int last_play_cards_count;
    private long created_at;
    private long updated_at;

    //运行时辅助变量 (用于记录对局实时状态)
    private String last_play_cards_record = ""; // 记录上一手打出的具体牌
    private int pass_count = 0;                 // 记录连续过牌的人数

    // 用于大小比较的权重表
    private static final List<String> RANK_ORDER = Arrays.asList("THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "JACK", "QUEEN", "KING", "ACE", "TWO");
    private static final List<String> SUIT_ORDER = Arrays.asList("DIAMOND", "CLUB", "HEART", "SPADE");

    public Game(int game_id, int room_id) {
        this.game_id = game_id;
        this.room_id = room_id;
        this.created_at =System.currentTimeMillis();
        this.status = "INIT";
    }

    /**
     * 发牌逻辑
     */
    public void startGame(List<Player> roomPlayers, List<Card> deck) {
        this.status = "PLAYING";
        this.updateTime();
        //洗牌
        Collections.shuffle(deck);
        //表明这副牌是属于当前这局游戏的
        for (Card card : deck) {
            card.bindGame(this.game_id);
        }

        //创建了一个包含 4 个空字符串的数组，用来暂时存放四个玩家各自的手牌数据。
        String[] handCardsText = new String[]{"", "", "", ""};
        //当 i 从 0 循环到 51 时，卡牌会被像发牌员一样，依次分发给玩家
        for (int i = 0; i < deck.size(); i++) {
            Player player = roomPlayers.get(i % 4);
            Card card = deck.get(i);
            card.assignToPlayer(player.getPlayer_id());

            if (handCardsText[i % 4].length() > 0) handCardsText[i % 4] += ",";
            handCardsText[i % 4] += card.getUnique_identifier();

            //确立先手
            if (card.getSuit().equals("DIAMOND") && card.getRank().equals("THREE")) {
                player.setHas_diamond_3(true);
                this.first_player_id = player.getPlayer_id();
                this.current_player_id = player.getPlayer_id();
            }
        }

        //更新玩家最终状态
        for (int i = 0; i < 4; i++) {
            roomPlayers.get(i).setHand_cards(handCardsText[i]);
            roomPlayers.get(i).setCards_remaining(13);
        }
    }

    /**
     * 核心出牌引擎，确定是否可以出牌
     */
    public boolean submitPlay(Player player, String cards, List<Player> roomPlayers) {
        if (this.status.equals("ENDED")) return false;
        if (player.getPlayer_id() != this.current_player_id) return false;

        String playType = identifyCardType(cards);
        if (playType.equals("INVALID")) return false;

        String[] cardArr = cards.split(",");
        int count = cardArr.length;

        // 规则验证
        if (this.last_play_cards_record.isEmpty()) {
            // 首轮自由出牌
            if (this.current_player_id == this.first_player_id && !cards.contains("DIAMOND_THREE")) {
                return false; // 先手必须出包含方块3的牌
            }
        } else {
            // 接牌轮次，必须牌型一致且数量一致
            if (!playType.equals(this.last_play_type) || count != this.last_play_cards_count) {
                return false;
            }
            // 必须大过上家
            if (!isBigger(cards, this.last_play_cards_record)) {
                return false;
            }
        }

        // 验证通过，执行玩家手牌扣减
        if (!player.playCards(cards)) return false;

        // 更新游戏状态
        this.last_play_cards_record = cards;
        this.recordLastPlay(playType, count);
        this.pass_count = 0; // 有人出牌，清空过牌计数器

        // 检查是否获胜
        if (player.getHand_cards().isEmpty()) {
            this.endGame();
            return true;
        }

        // 切换下一位玩家
        switchToNextPlayer(roomPlayers);
        return true;
    }

    /**
     * 过牌逻辑
     */
    public void pass(Player player, List<Player> roomPlayers) {
        //未实现
    }

    /**
     * 牌型识别
     */
    private String identifyCardType(String cards) {
        if (cards == null || cards.isEmpty()) return "INVALID";
        String[] cardArr = cards.split(",");
        if (cardArr.length == 1) return "SINGLE";

        if (cardArr.length == 2) {
            String rank1 = cardArr[0].split("_")[1];
            String rank2 = cardArr[1].split("_")[1];
            if (rank1.equals(rank2)) return "PAIR";
        }
        // 暂不支持其他牌型,后续补充
        return "INVALID";
    }

    /**
     * 算法：比对两组牌的大小
     */
    private boolean isBigger(String playCards, String lastCards) {

        return true;//未实现
    }



    /**
     * 辅助方法：切换到下一位未出完牌的玩家
     */
    private void switchToNextPlayer(List<Player> roomPlayers) {
        //未实现
    }

    // UML 方法
    public void switchPlayer(int playerId) {
        this.current_player_id = playerId;
        this.updateTime();
    }

    public void recordLastPlay(String type, int count) {
        this.last_play_type = type;
        this.last_play_cards_count = count;
        this.updateTime();
    }

    public void endGame() {
        this.status = "ENDED";
        this.updateTime();
    }

    public void updateTime() {
        this.updated_at = System.currentTimeMillis();
    }

    // Getters
    public int getCurrent_player_id() { return current_player_id; }
    public String getStatus() { return status; }
}