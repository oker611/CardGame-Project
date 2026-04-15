package com.example.cardgame;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.Game;
import com.example.cardgame.model.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // 定义日志的标签，方便在 Logcat 中搜索
    private static final String TAG = "GameTest";

    private static final List<String> SUITS = Arrays.asList("DIAMOND", "CLUB", "HEART", "SPADE");
    private static final List<String> RANKS = Arrays.asList("THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "JACK", "QUEEN", "KING", "ACE", "TWO");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 加载默认布局

        // 开始执行测试
        runDealCardsTest();
    }

    /**
     * 测试发牌逻辑的专用方法
     */
    private void runDealCardsTest() {
        Log.d(TAG, "========== 锄大地 发牌逻辑测试开始 ==========");

        // 1. 模拟创建 4 个玩家
        List<Player> players = new ArrayList<>();
        players.add(new Player(101, "玩家_A"));
        players.add(new Player(102, "玩家_B"));
        players.add(new Player(103, "玩家_C"));
        players.add(new Player(104, "玩家_D"));
        Log.d(TAG, "已成功加入 4 名玩家进入房间。");

        // 2. 模拟生成一副完整的 52 张扑克牌
        List<Card> deck = new ArrayList<>();
        int cardId = 1;
        for (String suit : SUITS) {
            for (String rank : RANKS) {
                // 实例化 52 张卡牌实体 (card_id, suit, rank)
                deck.add(new Card(cardId++, suit, rank));
            }
        }
        Log.d(TAG, "已成功生成一幅新牌，共 " + deck.size() + " 张。");

        // 3. 创建游戏对局引擎 (模拟 game_id=1000, room_id=888)
        Game game = new Game(1000, 888);

        // 4. 🚀 核心动作：调用你写好的发牌逻辑！
        Log.d(TAG, "正在洗牌与发牌...");
        game.startGame(players, deck);

        // 5. 打印验证结果
        Log.d(TAG, "对局状态更新为: " + game.getStatus());
        for (Player p : players) {
            Log.d(TAG, "--------------------------------------------------");
            Log.d(TAG, "玩家ID: " + p.getPlayer_id() + " (" + p.getPlayerInfo() + ")");
            // 打印该玩家拼接好的手牌长字符串
            Log.d(TAG, "手牌数据: " + p.getHand_cards());
            Log.d(TAG, "是否拥有方块3先手机会: " + p.checkDiamond3());
        }

        Log.d(TAG, "--------------------------------------------------");
        // game.getCurrent_player_id() 在发牌结束后会被指向持有方块3的先手玩家
        Log.d(TAG, "🔥 系统判定首轮出牌的玩家 ID 为: " + game.getCurrent_player_id());
        Log.d(TAG, "========== 锄大地 发牌逻辑测试结束 ==========");
    }
}

