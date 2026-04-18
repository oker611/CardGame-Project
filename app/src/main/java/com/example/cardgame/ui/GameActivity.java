package com.example.cardgame.ui;

import java.util.Random;
import java.util.Collections;

import com.example.cardgame.R;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cardgame.controller.GameActionHandler;
import com.example.cardgame.dto.GameViewData;
import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.CardGameApplication;
import com.example.cardgame.dto.PlayerViewData;

import java.util.ArrayList;
import java.util.List;

public class GameActivity extends AppCompatActivity {

    private RecyclerView rvHandCards;
    private CardAdapter cardAdapter;
    private List<String> handCards;           // 当前玩家手牌（牌面文字列表）
    private List<String> selectedCardIds;     // 当前选中的牌ID

    // 卡片尺寸常量（与 item_card.xml 中 CardView 的宽高一致）
    private static final float CARD_WIDTH_DP = 50f;
    private static final float CARD_OVERLAP_DP = -8f;

    // 团队接口实例（需要团队提供全局获取方式）
    @Nullable
    private GameActionHandler gameActionHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // TODO: 从 Application 或单例获取 GameActionHandler 实例
        gameActionHandler = CardGameApplication.getGameActionHandler();
        Log.d("GameActivity", "gameActionHandler = " + gameActionHandler);

        // 设置对手头像和昵称（模拟数据，不受影响）
        setupOpponents();

        // 手牌区
        rvHandCards = findViewById(R.id.rv_hand_cards);
        rvHandCards.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // 临时强制使用模拟数据，绕过 GameController 的空指针问题
        useMockDataForDemo();
        Toast.makeText(this, "模拟数据模式（UI演示）", Toast.LENGTH_LONG).show();

//        // ========== 原有模拟代码已注释，改用团队接口 ==========
//        if (gameActionHandler != null) {
//            // 通过团队接口开始新游戏（假设游戏未开始）
//            gameActionHandler.startNewGame();
//            // 刷新界面（从 handler 获取数据）
//            refreshUI();
//        } else {
//            // 若接口未就绪，临时使用模拟数据（仅用于 UI 演示，待团队接口完成后删除）
//            useMockDataForDemo();
//            Toast.makeText(this, "当前使用模拟数据，等待团队接口", Toast.LENGTH_LONG).show();
//        }

        // 出牌按钮
        findViewById(R.id.btn_play).setOnClickListener(v -> {
            if (gameActionHandler != null) {
                // 调用团队出牌接口
                PlayResult result = gameActionHandler.submitPlay(selectedCardIds);
                Toast.makeText(this, result.getMessage(), Toast.LENGTH_SHORT).show();
                if (result.isSuccess()) {
                    refreshUI(); // 刷新界面
                }
            } else {
                // 模拟模式下的出牌演示
                Toast.makeText(this, "出牌功能开发中（等待团队接口）", Toast.LENGTH_SHORT).show();
            }
        });

        // 过牌按钮
        findViewById(R.id.btn_pass).setOnClickListener(v -> {
            if (gameActionHandler != null) {
                PassResult result = gameActionHandler.passTurn();
                Toast.makeText(this, result.getMessage(), Toast.LENGTH_SHORT).show();
                refreshUI();
            } else {
                Toast.makeText(this, "过牌功能开发中（等待团队接口）", Toast.LENGTH_SHORT).show();
            }
        });

        // 退出游戏按钮
        Button btnExitGame = findViewById(R.id.btn_exit_game);
        btnExitGame.setOnClickListener(v -> {
            Intent intent = new Intent(GameActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    /**
     * 从团队接口获取最新数据并刷新 UI
     * 依赖：GameViewData 必须提供当前玩家的手牌列表（myHandCards）和选中牌ID列表（selectedCardIds）
     * 若接口尚未提供这些字段，UI 无法正确显示手牌，需要团队补充。
     */
    private void refreshUI() {
        Log.d("GameActivity", "refreshUI called");
        if (gameActionHandler == null) return;

        GameViewData data = gameActionHandler.getGameViewData();

        // ========== 获取当前玩家手牌（需要团队在 GameViewData 中添加字段） ==========
        // TODO: 要求团队在 GameViewData 中增加 List<String> myHandCards
        List<String> myHandCards = data.getMyHandCards(); // 假设方法存在，实际目前没有
        Log.d("GameActivity", "myHandCards = " + myHandCards);
        if (myHandCards != null) {
            handCards = myHandCards;
        } else {
            // 临时处理：无法获取手牌，显示空列表（并记录错误）
            Log.e("GameActivity", "无法获取手牌列表，请团队在 GameViewData 中添加 myHandCards 字段");
            handCards = new ArrayList<>();
        }

        // 获取选中的牌ID（接口已提供）
        selectedCardIds = data.getSelectedCardIds();

        // 更新对手信息（剩余牌数、回合提示等）
        updateOpponentsFromViewData(data);

        // 刷新手牌适配器
        if (cardAdapter == null) {
            cardAdapter = new CardAdapter(this, handCards, position -> {
                String cardId = handCards.get(position);
                if (gameActionHandler != null) {
                    gameActionHandler.toggleCardSelection(cardId);
                    refreshUI(); // 重新获取选中状态并刷新
                }
            });
            rvHandCards.setAdapter(cardAdapter);
        } else {
            // 需要 CardAdapter 增加 updateData 方法，或直接重新设置数据
            cardAdapter.updateData(handCards);
        }

        // 重新计算居中
        rvHandCards.post(() -> centerHandCards());
    }

    /**
     * 根据 GameViewData 更新对手信息（头像、名称、剩余牌数、回合指示等）
     * 依赖：GameViewData 中的 players 列表（PlayerViewData）包含所有玩家信息
     */
    /**
     * 根据 GameViewData 更新对手信息（头像、名称、剩余牌数、回合指示等）
     * 假设 players 列表顺序为：[玩家1(自己), 玩家2(上家), 玩家3(对家), 玩家4(下家)]
     */
    private void updateOpponentsFromViewData(GameViewData data) {
        List<PlayerViewData> players = data.getPlayers();
        if (players == null || players.size() < 4) {
            // 数据不足，继续使用模拟数据
            return;
        }

        // 对手顺序：索引1 = 上方（玩家2），索引2 = 左侧（玩家3），索引3 = 右侧（玩家4）
        PlayerViewData opponentTop = players.get(1);
        PlayerViewData opponentLeft = players.get(2);
        PlayerViewData opponentRight = players.get(3);

        // 更新上方对手
        TextView nameTop = findViewById(R.id.tv_name_top);
        nameTop.setText(opponentTop.getPlayerName() + " (" + opponentTop.getRemainingCardCount() + ")");

        // 更新左侧对手
        TextView nameLeft = findViewById(R.id.tv_name_left);
        nameLeft.setText(opponentLeft.getPlayerName() + " (" + opponentLeft.getRemainingCardCount() + ")");

        // 更新右侧对手
        TextView nameRight = findViewById(R.id.tv_name_right);
        nameRight.setText(opponentRight.getPlayerName() + " (" + opponentRight.getRemainingCardCount() + ")");

        // 可选：根据是否当前回合改变对手名称颜色（例如当前玩家回合高亮）
        if (opponentTop.isCurrentTurn()) {
            nameTop.setTextColor(getColor(android.R.color.holo_orange_dark));
        } else {
            nameTop.setTextColor(getColor(android.R.color.white));
        }
        // 同样处理左右对手...
    }

    /**
     * 临时模拟数据方法（仅用于 UI 演示，待团队接口完成后删除）
     * 完全复制原有逻辑，不依赖团队接口
     */
    private void useMockDataForDemo() {
        // 原有模拟生成和排序代码（直接复制原逻辑）
        handCards = generateRandomHand();
        sortHandByRule(handCards);
        selectedCardIds = new ArrayList<>();

        cardAdapter = new CardAdapter(this, handCards, position -> {
            String card = handCards.get(position);
            Toast.makeText(GameActivity.this, "选中: " + card, Toast.LENGTH_SHORT).show();
            // 模拟选中状态切换
            if (selectedCardIds.contains(card)) {
                selectedCardIds.remove(card);
            } else {
                selectedCardIds.add(card);
            }
        });
        rvHandCards.setAdapter(cardAdapter);
        rvHandCards.post(() -> centerHandCards());
    }

    // ========== 以下为原有方法（保留未改，仅用于模拟数据） ==========
    // 这些方法在 useMockDataForDemo() 中调用，团队接口就绪后可删除

    private void centerHandCards() {
        if (handCards == null || handCards.isEmpty() || rvHandCards == null) return;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        float density = getResources().getDisplayMetrics().density;
        int cardWidthPx = (int) (CARD_WIDTH_DP * density);
        int overlapPx = (int) (CARD_OVERLAP_DP * density);
        int totalWidth;
        if (handCards.size() == 1) {
            totalWidth = cardWidthPx;
        } else {
            totalWidth = cardWidthPx + (handCards.size() - 1) * (cardWidthPx + overlapPx);
        }
        int padding = (screenWidth - totalWidth) / 2;
        if (padding < 0) padding = 0;
        int minMarginPx = (int) (8 * density);
        padding = Math.max(padding, minMarginPx);
        rvHandCards.setPadding(padding, 0, padding, 0);
        Log.d("GameActivity", "手牌数量: " + handCards.size() + ", 总宽度px: " + totalWidth + ", 左右padding: " + padding);
    }

    // 原有随机生成手牌方法（仅模拟）
    private List<String> generateRandomHand() {
        List<String> allCards = new ArrayList<>();
        String[] suits = {"♥", "♠", "♦", "♣"};
        String[] ranks = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
        for (String suit : suits) {
            for (String rank : ranks) {
                allCards.add(suit + rank);
            }
        }
        List<String> hand = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < 13; i++) {
            int index = random.nextInt(allCards.size());
            hand.add(allCards.remove(index));
        }
        return hand;
    }

    // 原有排序方法（仅模拟）
    private void sortHandByRule(List<String> hand) {
        java.util.Map<String, Integer> rankPriority = new java.util.HashMap<>();
        rankPriority.put("2", 13);
        rankPriority.put("A", 12);
        rankPriority.put("K", 11);
        rankPriority.put("Q", 10);
        rankPriority.put("J", 9);
        rankPriority.put("10", 8);
        rankPriority.put("9", 7);
        rankPriority.put("8", 6);
        rankPriority.put("7", 5);
        rankPriority.put("6", 4);
        rankPriority.put("5", 3);
        rankPriority.put("4", 2);
        rankPriority.put("3", 1);
        java.util.Map<String, Integer> suitPriority = new java.util.HashMap<>();
        suitPriority.put("♠", 4);
        suitPriority.put("♥", 3);
        suitPriority.put("♣", 2);
        suitPriority.put("♦", 1);
        Collections.sort(hand, (card1, card2) -> {
            String suit1 = card1.substring(0, 1);
            String rank1 = card1.substring(1);
            String suit2 = card2.substring(0, 1);
            String rank2 = card2.substring(1);
            int rankCompare = rankPriority.get(rank2) - rankPriority.get(rank1);
            if (rankCompare != 0) return rankCompare;
            else return suitPriority.get(suit2) - suitPriority.get(suit1);
        });
    }

    // 原有对手模拟数据（仅模拟）
    private void setupOpponents() {
        ImageView avatarTop = findViewById(R.id.iv_avatar_top);
        TextView nameTop = findViewById(R.id.tv_name_top);
        avatarTop.setImageResource(R.drawable.default_avatar);
        nameTop.setText("玩家2");
        ImageView avatarLeft = findViewById(R.id.iv_avatar_left);
        TextView nameLeft = findViewById(R.id.tv_name_left);
        avatarLeft.setImageResource(R.drawable.default_avatar);
        nameLeft.setText("玩家3");
        ImageView avatarRight = findViewById(R.id.iv_avatar_right);
        TextView nameRight = findViewById(R.id.tv_name_right);
        avatarRight.setImageResource(R.drawable.default_avatar);
        nameRight.setText("玩家4");
    }
}