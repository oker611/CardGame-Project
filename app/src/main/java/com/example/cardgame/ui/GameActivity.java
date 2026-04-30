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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.cardview.widget.CardView;

import com.example.cardgame.CardGameApplication;
import com.example.cardgame.R;
import com.example.cardgame.controller.GameActionHandler;
import com.example.cardgame.dto.GameViewData;
import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.dto.PlayerViewData;

import java.util.ArrayList;
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
    private static final float CARD_HEIGHT_DP = 72f;
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

        playAreaSelf = findViewById(R.id.play_area_self);
        playAreaTop = findViewById(R.id.play_area_top);
        playAreaLeft = findViewById(R.id.play_area_left);
        playAreaRight = findViewById(R.id.play_area_right);

        rvHandCards = findViewById(R.id.rv_hand_cards);
        rvHandCards.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        );

        selectedCardIds = new ArrayList<>();
        handCards = new ArrayList<>();
        if (gameActionHandler != null) {
            gameActionHandler.setUiRefreshCallback(() -> runOnUiThread(this::refreshUI));
        }

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
                    if (result.isSuccess()) refreshUI();
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

        Button btnPlay = findViewById(R.id.btn_play);
        Button btnPass = findViewById(R.id.btn_pass);
        btnPlay.setEnabled(!data.isGameOver());
        btnPass.setEnabled(!data.isGameOver());

        Log.d("GameCheck", "当前手牌: " + data.getMyHandCards());
        Log.d("GameCheck", "最后出牌: " + data.getLastPlayCards());

        List<String> myHandCards = data.getMyHandCards();
        handCards = (myHandCards != null) ? new ArrayList<>(myHandCards) : new ArrayList<>();
        selectedCardIds = new ArrayList<>(data.getSelectedCardIds());

        updateOpponentsFromViewData(data);
        updatePlayAreas(data);   // 关键的更新

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
        if (data.isGameOver() && !gameOverDialogShown) {
            showGameOverDialog(data);
        }
    }

    /**
     * 更新四个出牌区：根据每个玩家是否有出牌记录和 Pass 状态，
     * 显示牌图片或者“不出”文字。
     */
    private void updatePlayAreas(GameViewData data) {
        clearPlayAreas();
        if (data == null) return;

        List<PlayerViewData> players = data.getPlayers();
        Map<String, List<String>> playerLastPlayCards = data.getPlayerLastPlayCards();

        if (players == null || players.size() < 4 || playerLastPlayCards == null) {
            // 兜底：只显示自己区域的桌面最后一手牌
            renderCardsToArea(playAreaSelf, data.getLastPlayCards());
            return;
        }

        // 顺序: 0自己,1左,2上,3右
        renderPlayerArea(playAreaSelf, players.get(0), playerLastPlayCards);
        renderPlayerArea(playAreaLeft, players.get(1), playerLastPlayCards);
        renderPlayerArea(playAreaTop, players.get(2), playerLastPlayCards);
        renderPlayerArea(playAreaRight, players.get(3), playerLastPlayCards);
    }

    /**
     * 为单个玩家渲染出牌区：
     * 如果没有出牌记录（或空列表）且该玩家本轮已 Pass，则显示“不出”；
     * 否则显示其最后一次出的牌（图片）。
     */
    private void renderPlayerArea(LinearLayout area, PlayerViewData player, Map<String, List<String>> lastPlayCards) {
        if (area == null) return;
        area.removeAllViews();
        area.setGravity(Gravity.CENTER);

        List<String> cards = lastPlayCards.get(player.getPlayerId());
        if ((cards == null || cards.isEmpty()) && player.isPassed()) {
            // 显示“不出”文字
            TextView textView = new TextView(this);
            textView.setText("不出");
            textView.setTextColor(getColor(android.R.color.white));
            textView.setTextSize(18f);
            textView.setGravity(Gravity.CENTER);
            area.addView(textView);
        } else if (cards != null && !cards.isEmpty()) {
            // 正常显示牌图片
            renderCardsToArea(area, cards);
        }
        // 没有出牌且没有 Pass 则留空（理论上不会有这种情况）
    }

    private void clearPlayAreas() {
        if (playAreaSelf != null) playAreaSelf.removeAllViews();
        if (playAreaTop != null) playAreaTop.removeAllViews();
        if (playAreaLeft != null) playAreaLeft.removeAllViews();
        if (playAreaRight != null) playAreaRight.removeAllViews();
    }

    private void renderCardsToArea(LinearLayout playArea, List<String> cards) {
        if (playArea == null) return;
        playArea.removeAllViews();
        playArea.setGravity(Gravity.CENTER);
        if (cards == null || cards.isEmpty()) return;

        float density = getResources().getDisplayMetrics().density;
        int cardWidthPx = (int) (36 * density);
        int cardHeightPx = (int) (56 * density);
        int overlapPx = (int) (-8 * density);

        for (int i = 0; i < cards.size(); i++) {
            String cardStr = cards.get(i);
            View cardView = getLayoutInflater().inflate(R.layout.item_play_card, playArea, false);
            CardView cv = cardView.findViewById(R.id.card_view);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardWidthPx, cardHeightPx);
            if (i > 0) params.leftMargin = overlapPx;
            params.gravity = Gravity.CENTER_VERTICAL;
            cv.setLayoutParams(params);

            ImageView iv = cardView.findViewById(R.id.iv_play_card);
            int resId = getCardDrawableResource(cardStr);
            if (resId != 0) iv.setImageResource(resId);
            else iv.setImageResource(android.R.drawable.ic_menu_gallery);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            playArea.addView(cardView);
        }
    }

    private int getCardDrawableResource(String cardId) {
        if (cardId == null || cardId.length() < 2) return 0;
        String suitPart;
        char suitChar = cardId.charAt(0);
        switch (suitChar) {
            case '♥': suitPart = "heart"; break;
            case '♠': suitPart = "spade"; break;
            case '♦': suitPart = "diamond"; break;
            case '♣': suitPart = "club"; break;
            default: return 0;
        }
        String rank = cardId.substring(1);
        String rankPart;
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

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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

    // 以下方法保持原有不变（useMockDataForDemo, centerHandCards, generateRandomHand, sortHandByRule, setupOpponents, showGameOverDialog）
    private void useMockDataForDemo() {
        handCards = generateRandomHand();
        sortHandByRule(handCards);
        selectedCardIds = new ArrayList<>();
        cardAdapter = new CardAdapter(this, handCards, position -> {
            String card = handCards.get(position);
            Toast.makeText(GameActivity.this, "选中: " + card, Toast.LENGTH_SHORT).show();
            if (selectedCardIds.contains(card)) selectedCardIds.remove(card);
            else selectedCardIds.add(card);
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
        Typeface typeface = Typeface.createFromAsset(getAssets(), "my_custom_font.ttf");
        tvTitle.setTypeface(typeface);
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