package com.example.cardgame.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
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
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cardgame.CardGameApplication;
import com.example.cardgame.R;
import com.example.cardgame.controller.GameActionHandler;
import com.example.cardgame.controller.GameController;
import com.example.cardgame.dto.GameViewData;
import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.dto.PlayerViewData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        if (gameActionHandler instanceof GameController) {
            ((GameController) gameActionHandler).setUiRefreshCallback(() ->
                    runOnUiThread(this::refreshUI)
            );
        }

        setupOpponents();

        playAreaSelf = findViewById(R.id.play_area_self);
        playAreaTop = findViewById(R.id.play_area_top);
        playAreaLeft = findViewById(R.id.play_area_left);
        playAreaRight = findViewById(R.id.play_area_right);

        rvHandCards = findViewById(R.id.rv_hand_cards);
        rvHandCards.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        selectedCardIds = new ArrayList<>();
        handCards = new ArrayList<>();

        if (gameActionHandler != null) {
            gameActionHandler.startNewGame();
            refreshUI();
            Toast.makeText(this, "真实联调模式", Toast.LENGTH_SHORT).show();
        } else {
            useMockDataForDemo();
            Toast.makeText(this, "模拟数据模式（UI演示）", Toast.LENGTH_LONG).show();
        }

        findViewById(R.id.btn_play).setOnClickListener(v -> {
            if (gameActionHandler != null) {
                GameViewData data = gameActionHandler.getGameViewData();
                if (data != null && data.getPlayers() != null) {
                    for (PlayerViewData player : data.getPlayers()) {
                        if (player.isCurrentTurn() && !player.isHuman()) {
                            Toast.makeText(this, "当前是AI或远程玩家的回合，请等待...", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                }
                PlayResult result = gameActionHandler.submitPlay(new ArrayList<>(selectedCardIds));
                if (result != null) {
                    Toast.makeText(this, result.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "出牌功能开发中", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_pass).setOnClickListener(v -> {
            if (gameActionHandler != null) {
                GameViewData data = gameActionHandler.getGameViewData();
                if (data != null && data.getPlayers() != null) {
                    for (PlayerViewData player : data.getPlayers()) {
                        if (player.isCurrentTurn() && !player.isHuman()) {
                            Toast.makeText(this, "当前是AI或远程玩家的回合，请等待...", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                }
                PassResult result = gameActionHandler.passTurn();
                if (result != null) {
                    Toast.makeText(this, result.getMessage(), Toast.LENGTH_SHORT).show();
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

        Button btnPlay = findViewById(R.id.btn_play);
        Button btnPass = findViewById(R.id.btn_pass);
        btnPlay.setEnabled(!data.isGameOver());
        btnPass.setEnabled(!data.isGameOver());

        handCards = (data.getMyHandCards() != null) ? new ArrayList<>(data.getMyHandCards()) : new ArrayList<>();
        selectedCardIds = new ArrayList<>(data.getSelectedCardIds());

        updateOpponentsFromViewData(data);
        updatePlayAreas(data);

        if (cardAdapter == null) {
            cardAdapter = new CardAdapter(this, handCards, position -> {
                String cardId = handCards.get(position);
                if (gameActionHandler != null) {
                    gameActionHandler.toggleCardSelection(cardId);
                }
            });
            rvHandCards.setAdapter(cardAdapter);
        } else {
            cardAdapter.updateData(handCards);
        }

        rvHandCards.post(this::centerHandCards);

        if (data.isGameOver() && !gameOverDialogShown) {
            showGameOverDialog(data);
        }
    }

    private LinearLayout getPlayAreaForPlayer(String playerId) {
        if (playerId == null) return null;
        switch (playerId) {
            case "P1": return playAreaSelf;
            case "P2": return playAreaTop;
            case "P3": return playAreaLeft;
            case "P4": return playAreaRight;
            default: return null;
        }
    }

    private void updatePlayAreas(GameViewData data) {
        if (playAreaSelf != null) playAreaSelf.removeAllViews();
        if (playAreaTop != null) playAreaTop.removeAllViews();
        if (playAreaLeft != null) playAreaLeft.removeAllViews();
        if (playAreaRight != null) playAreaRight.removeAllViews();

        Map<String, List<String>> playerLastPlay = data.getPlayerLastPlayCards();
        if (playerLastPlay == null) return;

        float density = getResources().getDisplayMetrics().density;
        int overlapPx = (int) (-8 * density); // 重叠 8dp
        // 强制设定小卡片尺寸（单位 px）
        int cardWidthPx = (int) (40 * density);
        int cardHeightPx = (int) (64 * density);

        for (Map.Entry<String, List<String>> entry : playerLastPlay.entrySet()) {
            String playerId = entry.getKey();
            List<String> cards = entry.getValue();
            LinearLayout targetArea = getPlayAreaForPlayer(playerId);
            if (targetArea == null || cards == null || cards.isEmpty()) continue;

            for (int i = 0; i < cards.size(); i++) {
                String cardStr   = cards.get(i);

                // 加载小卡片布局
                View cardView = getLayoutInflater().inflate(R.layout.item_play_card, targetArea, false);
                // 获取 CardView 容器（注意布局文件中 id 为 card_view）
                CardView cv = cardView.findViewById(R.id.card_view);
                // 设置 CardView 的尺寸和左边距
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardWidthPx, cardHeightPx);
                if (i > 0) {
                    params.leftMargin = overlapPx;
                } else {
                    params.leftMargin = 0;
                }
                params.gravity = Gravity.CENTER_VERTICAL;
                cv.setLayoutParams(params);

                // 设置牌面图片
                ImageView iv = cardView.findViewById(R.id.iv_play_card);
                int resId = getCardDrawableResource(cardStr);
                iv.setImageResource(resId != 0 ? resId : android.R.drawable.ic_menu_gallery);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);

                targetArea.addView(cardView);
            }
            targetArea.setGravity(Gravity.CENTER);
        }
    }

    // 获取卡片图片资源ID（与 CardAdapter 逻辑一致）
    private int getCardDrawableResource(String cardId) {
        if (cardId == null || cardId.length() < 2) return 0;
        String suitPart = "";
        String rankPart = "";
        char suitChar = cardId.charAt(0);
        switch (suitChar) {
            case '♥': suitPart = "heart"; break;
            case '♠': suitPart = "spade"; break;
            case '♦': suitPart = "diamond"; break;
            case '♣': suitPart = "club"; break;
            default: return 0;
        }
        String rank = cardId.substring(1);
        switch (rank) {
            case "A": rankPart = "ace"; break;
            case "J": rankPart = "jack"; break;
            case "Q": rankPart = "queen"; break;
            case "K": rankPart = "king"; break;
            default: rankPart = rank; break;
        }
        String fileName = suitPart + "_" + rankPart;
        return getResources().getIdentifier(fileName, "drawable", getPackageName());
    }

    private void updateOpponentsFromViewData(GameViewData data) {
        List<PlayerViewData> players = data.getPlayers();
        if (players == null || players.size() < 4) return;

        PlayerViewData opponentLeft = players.get(1);
        PlayerViewData opponentTop = players.get(2);
        PlayerViewData opponentRight = players.get(3);

        TextView nameTop = findViewById(R.id.tv_name_top);
        TextView nameLeft = findViewById(R.id.tv_name_left);
        TextView nameRight = findViewById(R.id.tv_name_right);

        nameTop.setText(opponentTop.getPlayerName() + " (" + opponentTop.getRemainingCardCount() + "张)");
        nameLeft.setText(opponentLeft.getPlayerName() + " (" + opponentLeft.getRemainingCardCount() + "张)");
        nameRight.setText(opponentRight.getPlayerName() + " (" + opponentRight.getRemainingCardCount() + "张)");

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
            refreshUI();
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
        Map<String, Integer> rankPriority = new HashMap<>();
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

        Map<String, Integer> suitPriority = new HashMap<>();
        suitPriority.put("♠", 4);
        suitPriority.put("♥", 3);
        suitPriority.put("♣", 2);
        suitPriority.put("♦", 1);

        hand.sort((card1, card2) -> {
            String rank1 = card1.substring(1);
            String rank2 = card2.substring(1);
            int rankCompare = rankPriority.get(rank2) - rankPriority.get(rank1);
            if (rankCompare != 0) return rankCompare;
            String suit1 = card1.substring(0, 1);
            String suit2 = card2.substring(0, 1);
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

        List<PlayerViewData> sorted = new ArrayList<>(players);
        sorted.sort((a, b) -> Integer.compare(a.getRemainingCardCount(), b.getRemainingCardCount()));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_game_over, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();

        TextView tvTitle = dialogView.findViewById(R.id.tv_game_over_title);
        try {
            Typeface typeface = Typeface.createFromAsset(getAssets(), "my_custom_font.ttf");
            tvTitle.setTypeface(typeface);
        } catch (Exception e) {
            Log.w("GameActivity", "自定义字体加载失败", e);
        }

        TextView tvWinner = dialogView.findViewById(R.id.tv_winner);
        tvWinner.setText(data.getWinnerName());

        RecyclerView rvRanking = dialogView.findViewById(R.id.rv_ranking);
        rvRanking.setLayoutManager(new LinearLayoutManager(this));
        RankingAdapter adapter = new RankingAdapter(sorted);
        rvRanking.setAdapter(adapter);

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