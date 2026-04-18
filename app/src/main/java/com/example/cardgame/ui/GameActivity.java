package com.example.cardgame.ui;

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

import com.example.cardgame.CardGameApplication;
import com.example.cardgame.R;
import com.example.cardgame.controller.GameActionHandler;
import com.example.cardgame.dto.GameViewData;
import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.dto.PlayerViewData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class GameActivity extends AppCompatActivity {

    private RecyclerView rvHandCards;
    private CardAdapter cardAdapter;
    private List<String> handCards;           // 当前玩家手牌（联调阶段先直接使用 cardId）
    private List<String> selectedCardIds;     // 当前选中的牌ID

    // 卡片尺寸常量（与 item_card.xml 中 CardView 的宽高一致）
    private static final float CARD_WIDTH_DP = 50f;
    private static final float CARD_OVERLAP_DP = -8f;

    @Nullable
    private GameActionHandler gameActionHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        gameActionHandler = CardGameApplication.getGameActionHandler();
        Log.d("GameActivity", "gameActionHandler = " + gameActionHandler);
        System.out.println("[CardGame][UI] onCreate, gameActionHandler=" + gameActionHandler);

        setupOpponents();

        rvHandCards = findViewById(R.id.rv_hand_cards);
        rvHandCards.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        selectedCardIds = new ArrayList<>();
        handCards = new ArrayList<>();

        // 优先走真实联调模式；拿不到 handler 时才回退到 mock
        if (gameActionHandler != null) {
            System.out.println("[CardGame][UI] gameActionHandler ready, start real game flow");
            gameActionHandler.startNewGame();
            refreshUI();
            Toast.makeText(this, "真实联调模式", Toast.LENGTH_SHORT).show();
        } else {
            System.out.println("[CardGame][UI] gameActionHandler is null, fallback to mock mode");
            useMockDataForDemo();
            Toast.makeText(this, "模拟数据模式（UI演示）", Toast.LENGTH_LONG).show();
        }

        // 出牌按钮
        findViewById(R.id.btn_play).setOnClickListener(v -> {
            System.out.println("[CardGame][UI] Click play button, selectedCardIds=" + selectedCardIds);

            if (gameActionHandler != null) {
                PlayResult result = gameActionHandler.submitPlay(new ArrayList<>(selectedCardIds));
                if (result != null) {
                    Toast.makeText(this, result.getMessage(), Toast.LENGTH_SHORT).show();
                    System.out.println("[CardGame][UI] Play result=" + result.getMessage());

                    if (result.isSuccess()) {
                        refreshUI();
                    }
                } else {
                    System.out.println("[CardGame][UI] Play result is null");
                    Toast.makeText(this, "PlayResult is null", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "出牌功能开发中（等待团队接口）", Toast.LENGTH_SHORT).show();
            }
        });

        // 过牌按钮
        findViewById(R.id.btn_pass).setOnClickListener(v -> {
            System.out.println("[CardGame][UI] Click pass button");

            if (gameActionHandler != null) {
                PassResult result = gameActionHandler.passTurn();
                if (result != null) {
                    Toast.makeText(this, result.getMessage(), Toast.LENGTH_SHORT).show();
                    System.out.println("[CardGame][UI] Pass result=" + result.getMessage());
                    refreshUI();
                } else {
                    System.out.println("[CardGame][UI] Pass result is null");
                    Toast.makeText(this, "PassResult is null", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "过牌功能开发中（等待团队接口）", Toast.LENGTH_SHORT).show();
            }
        });

        // 退出游戏按钮
        Button btnExitGame = findViewById(R.id.btn_exit_game);
        btnExitGame.setOnClickListener(v -> {
            System.out.println("[CardGame][UI] Click exit button");

            Intent intent = new Intent(GameActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    /**
     * 从团队接口获取最新数据并刷新 UI
     */
    private void refreshUI() {
        Log.d("GameActivity", "refreshUI called");
        System.out.println("[CardGame][UI] refreshUI called");

        if (gameActionHandler == null) {
            System.out.println("[CardGame][UI] refreshUI aborted: gameActionHandler is null");
            return;
        }

        GameViewData data = gameActionHandler.getGameViewData();
        if (data == null) {
            System.out.println("[CardGame][UI] refreshUI aborted: GameViewData is null");
            return;
        }

        // 联调阶段这里默认取 myHandCards
        List<String> myHandCards = data.getMyHandCards();
        Log.d("GameActivity", "myHandCards = " + myHandCards);
        System.out.println("[CardGame][UI] myHandCards=" + myHandCards);

        if (myHandCards != null) {
            handCards = new ArrayList<>(myHandCards);
        } else {
            Log.e("GameActivity", "无法获取手牌列表，请团队在 GameViewData 中添加 myHandCards 字段");
            System.out.println("[CardGame][UI] myHandCards is null");
            handCards = new ArrayList<>();
        }

        if (data.getSelectedCardIds() != null) {
            selectedCardIds = new ArrayList<>(data.getSelectedCardIds());
        } else {
            selectedCardIds = new ArrayList<>();
        }

        updateOpponentsFromViewData(data);

        if (cardAdapter == null) {
            cardAdapter = new CardAdapter(this, handCards, position -> {
                if (position < 0 || position >= handCards.size()) {
                    return;
                }

                String cardId = handCards.get(position);
                System.out.println("[CardGame][UI] Click hand card, cardId=" + cardId);

                if (gameActionHandler != null) {
                    gameActionHandler.toggleCardSelection(cardId);
                    refreshUI();
                }
            });
            rvHandCards.setAdapter(cardAdapter);
        } else {
            cardAdapter.updateData(handCards);
        }

        rvHandCards.post(this::centerHandCards);
    }

    /**
     * 根据 GameViewData 更新对手信息
     * 假设 players 列表顺序为：[玩家1(自己), 玩家2(上家), 玩家3(对家), 玩家4(下家)]
     */
    private void updateOpponentsFromViewData(GameViewData data) {
        List<PlayerViewData> players = data.getPlayers();
        if (players == null || players.size() < 4) {
            System.out.println("[CardGame][UI] updateOpponentsFromViewData skipped: players data invalid");
            return;
        }

        PlayerViewData opponentTop = players.get(1);
        PlayerViewData opponentLeft = players.get(2);
        PlayerViewData opponentRight = players.get(3);

        TextView nameTop = findViewById(R.id.tv_name_top);
        nameTop.setText(opponentTop.getPlayerName() + " (" + opponentTop.getRemainingCardCount() + ")");

        TextView nameLeft = findViewById(R.id.tv_name_left);
        nameLeft.setText(opponentLeft.getPlayerName() + " (" + opponentLeft.getRemainingCardCount() + ")");

        TextView nameRight = findViewById(R.id.tv_name_right);
        nameRight.setText(opponentRight.getPlayerName() + " (" + opponentRight.getRemainingCardCount() + ")");

        if (opponentTop.isCurrentTurn()) {
            nameTop.setTextColor(getColor(android.R.color.holo_orange_dark));
        } else {
            nameTop.setTextColor(getColor(android.R.color.white));
        }

        if (opponentLeft.isCurrentTurn()) {
            nameLeft.setTextColor(getColor(android.R.color.holo_orange_dark));
        } else {
            nameLeft.setTextColor(getColor(android.R.color.white));
        }

        if (opponentRight.isCurrentTurn()) {
            nameRight.setTextColor(getColor(android.R.color.holo_orange_dark));
        } else {
            nameRight.setTextColor(getColor(android.R.color.white));
        }
    }

    /**
     * 临时模拟数据方法（仅用于 UI 演示，待团队接口完成后删除）
     */
    private void useMockDataForDemo() {
        handCards = generateRandomHand();
        sortHandByRule(handCards);
        selectedCardIds = new ArrayList<>();

        cardAdapter = new CardAdapter(this, handCards, position -> {
            String card = handCards.get(position);
            Toast.makeText(GameActivity.this, "选中: " + card, Toast.LENGTH_SHORT).show();

            if (selectedCardIds.contains(card)) {
                selectedCardIds.remove(card);
            } else {
                selectedCardIds.add(card);
            }

            System.out.println("[CardGame][UI][MOCK] toggle card=" + card + ", selectedCardIds=" + selectedCardIds);
        });

        rvHandCards.setAdapter(cardAdapter);
        rvHandCards.post(this::centerHandCards);
    }

    private void centerHandCards() {
        if (handCards == null || handCards.isEmpty() || rvHandCards == null) {
            return;
        }

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
        if (padding < 0) {
            padding = 0;
        }

        int minMarginPx = (int) (8 * density);
        padding = Math.max(padding, minMarginPx);
        rvHandCards.setPadding(padding, 0, padding, 0);

        Log.d("GameActivity", "手牌数量: " + handCards.size() + ", 总宽度px: " + totalWidth + ", 左右padding: " + padding);
    }

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
            if (rankCompare != 0) {
                return rankCompare;
            } else {
                return suitPriority.get(suit2) - suitPriority.get(suit1);
            }
        });
    }

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