package com.example.cardgame.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
import java.util.List;
import java.util.Random;

public class GameActivity extends AppCompatActivity {

    private RecyclerView rvHandCards;
    private CardAdapter cardAdapter;
    private List<String> handCards;
    private List<String> selectedCardIds;
    private LinearLayout playAreaSelf;
    private LinearLayout playAreaTop;
    private LinearLayout playAreaLeft;
    private LinearLayout playAreaRight;
    private boolean gameOverDialogShown = false;

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

        setupOpponents();

        // 初始化出牌区
        playAreaSelf = findViewById(R.id.play_area_self);
        playAreaTop = findViewById(R.id.play_area_top);
        playAreaLeft = findViewById(R.id.play_area_left);
        playAreaRight = findViewById(R.id.play_area_right);

        rvHandCards = findViewById(R.id.rv_hand_cards);
        rvHandCards.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        selectedCardIds = new ArrayList<>();
        handCards = new ArrayList<>();

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

        findViewById(R.id.btn_play).setOnClickListener(v -> {
            if (gameActionHandler != null) {
                PlayResult result = gameActionHandler.submitPlay(new ArrayList<>(selectedCardIds));
                if (result != null) {
                    Toast.makeText(this, result.getMessage(), Toast.LENGTH_SHORT).show();
                    if (result.isSuccess()) {
                        refreshUI();
                    }
                }
            } else {
                Toast.makeText(this, "出牌功能开发中", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_pass).setOnClickListener(v -> {
            if (gameActionHandler != null) {
                PassResult result = gameActionHandler.passTurn();
                if (result != null) {
                    Toast.makeText(this, result.getMessage(), Toast.LENGTH_SHORT).show();
                    refreshUI();
                }
            } else {
                Toast.makeText(this, "过牌功能开发中", Toast.LENGTH_SHORT).show();
            }
        });

        Button btnExitGame = findViewById(R.id.btn_exit_game);
        btnExitGame.setOnClickListener(v -> {
            Intent intent = new Intent(GameActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void refreshUI() {
        if (gameActionHandler == null) return;

        GameViewData data = gameActionHandler.getGameViewData();
        if (data == null) return;
<<<<<<< feature/gameover-ui-zhy

=======
        //根据游戏状态控制按钮启用/禁用
        Button btnPlay = findViewById(R.id.btn_play);
        Button btnPass = findViewById(R.id.btn_pass);
        if (data.isGameOver()) {
            btnPlay.setEnabled(false);
            btnPass.setEnabled(false);
        } else {
            btnPlay.setEnabled(true);
            btnPass.setEnabled(true);
        }
        Log.d("GameCheck", "当前手牌: " + data.getMyHandCards());
>>>>>>> main
        List<String> myHandCards = data.getMyHandCards();
        if (myHandCards != null) {
            handCards = new ArrayList<>(myHandCards);
        } else {
            handCards = new ArrayList<>();
        }

        selectedCardIds = new ArrayList<>(data.getSelectedCardIds());

        updateOpponentsFromViewData(data);
        updatePlayAreas(data);

        if (cardAdapter == null) {
            cardAdapter = new CardAdapter(this, handCards, position -> {
                String cardId = handCards.get(position);
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

        // 检查游戏结束
        if (data.isGameOver() && !gameOverDialogShown) {
            showGameOverDialog(data);
        }
    }

    private void updatePlayAreas(GameViewData data) {
        if (playAreaSelf == null) return;
        playAreaSelf.removeAllViews();

        String lastPlayText = data.getLastPlayText();
        if (lastPlayText != null && !lastPlayText.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("出牌: " + lastPlayText);
            tv.setTextColor(getColor(android.R.color.white));
            tv.setTextSize(12f);
            playAreaSelf.addView(tv);
        }
    }

    private void updateOpponentsFromViewData(GameViewData data) {
        List<PlayerViewData> players = data.getPlayers();
        if (players == null || players.size() < 4) return;

        PlayerViewData opponentLeft = players.get(1);
        PlayerViewData opponentTop = players.get(2);
        PlayerViewData opponentRight = players.get(3);

        TextView nameTop = findViewById(R.id.tv_name_top);
        nameTop.setText(opponentTop.getPlayerName() + " (" + opponentTop.getRemainingCardCount() + ")");

        TextView nameLeft = findViewById(R.id.tv_name_left);
        nameLeft.setText(opponentLeft.getPlayerName() + " (" + opponentLeft.getRemainingCardCount() + ")");

        TextView nameRight = findViewById(R.id.tv_name_right);
        nameRight.setText(opponentRight.getPlayerName() + " (" + opponentRight.getRemainingCardCount() + ")");

        int colorTurn = getColor(android.R.color.holo_orange_dark);
        int colorNormal = getColor(android.R.color.white);
        nameTop.setTextColor(opponentTop.isCurrentTurn() ? colorTurn : colorNormal);
        nameLeft.setTextColor(opponentLeft.isCurrentTurn() ? colorTurn : colorNormal);
        nameRight.setTextColor(opponentRight.isCurrentTurn() ? colorTurn : colorNormal);
    }

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
        });

        rvHandCards.setAdapter(cardAdapter);
        rvHandCards.post(this::centerHandCards);
    }

    private void centerHandCards() {
        if (handCards == null || handCards.isEmpty() || rvHandCards == null) return;

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        float density = getResources().getDisplayMetrics().density;
        int cardWidthPx = (int) (CARD_WIDTH_DP * density);
        int overlapPx = (int) (CARD_OVERLAP_DP * density);

        int totalWidth = cardWidthPx + (handCards.size() - 1) * (cardWidthPx + overlapPx);
        int padding = (screenWidth - totalWidth) / 2;
        if (padding < 0) padding = 0;
        int minMarginPx = (int) (8 * density);
        padding = Math.max(padding, minMarginPx);
        rvHandCards.setPadding(padding, 0, padding, 0);
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

        hand.sort((card1, card2) -> {
            String suit1 = card1.substring(0, 1);
            String rank1 = card1.substring(1);
            String suit2 = card2.substring(0, 1);
            String rank2 = card2.substring(1);
            int rankCompare = rankPriority.get(rank2) - rankPriority.get(rank1);
            if (rankCompare != 0) return rankCompare;
            return suitPriority.get(suit2) - suitPriority.get(suit1);
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

    private void showGameOverDialog(GameViewData data) {
        if (gameOverDialogShown) return;
        gameOverDialogShown = true;

        List<PlayerViewData> players = data.getPlayers();
        if (players == null || players.isEmpty()) return;

        // 按剩余牌数排序（升序，剩余牌数少排名高）
        List<PlayerViewData> sorted = new ArrayList<>(players);
        sorted.sort((a, b) -> Integer.compare(a.getRemainingCardCount(), b.getRemainingCardCount()));

        // 构建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_game_over, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();

        // 设置自定义字体（游戏结束标题）
        TextView tvTitle = dialogView.findViewById(R.id.tv_game_over_title);
        Typeface typeface = Typeface.createFromAsset(getAssets(), "my_custom_font.ttf");
        tvTitle.setTypeface(typeface);

        // 设置获胜者名称
        TextView tvWinner = dialogView.findViewById(R.id.tv_winner);
        tvWinner.setText(data.getWinnerName());

        // 设置排名列表
        RecyclerView rvRanking = dialogView.findViewById(R.id.rv_ranking);
        rvRanking.setLayoutManager(new LinearLayoutManager(this));
        RankingAdapter adapter = new RankingAdapter(sorted);
        rvRanking.setAdapter(adapter);

        // 返回按钮（圆形 ImageButton）
        ImageButton btnBackHome = dialogView.findViewById(R.id.btn_back_home);
        btnBackHome.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(GameActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        dialog.show();
    }
}