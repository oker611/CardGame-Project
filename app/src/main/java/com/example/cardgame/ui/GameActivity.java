package com.example.cardgame.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.MotionEvent;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.cardview.widget.CardView;
import android.content.SharedPreferences;
import android.graphics.Color;

import com.example.cardgame.CardGameApplication;
import com.example.cardgame.R;
import com.example.cardgame.controller.BluetoothActionHandler;
import com.example.cardgame.controller.GameActionHandler;
import com.example.cardgame.controller.GameController;
import com.example.cardgame.dto.GameViewData;
import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.dto.PlayerViewData;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.Suit;
import com.example.cardgame.model.Rank;
import com.example.cardgame.rule.PatternRecognizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.HashMap;

import com.example.cardgame.event.GameEventListener;
import com.example.cardgame.event.GameEvent;
import com.example.cardgame.event.EventBus;
import com.example.cardgame.event.CardPlayedEvent;
import com.example.cardgame.event.PlayerPassedEvent;
import com.example.cardgame.event.TurnChangedEvent;
import com.example.cardgame.event.GameOverEvent;
import com.example.cardgame.rule.RuleConfig;

public class GameActivity extends AppCompatActivity implements GameController.CountdownUICallback, GameEventListener {

    private RecyclerView rvHandCards;
    private CardAdapter cardAdapter;
    private List<String> handCards;
    private List<String> selectedCardIds;
    private LinearLayout playAreaTop;
    private LinearLayout playAreaLeft;
    private LinearLayout playAreaRight;
    private RuleConfig ruleConfig;

    private boolean gameOverDialogShown = false;

    private static final float CARD_WIDTH_DP = 50f;
    private static final float CARD_HEIGHT_DP = 72f;
    private static final float CARD_OVERLAP_DP = -8f;

    @Nullable
    private GameActionHandler gameActionHandler;

    @Nullable
    private BluetoothActionHandler bluetoothActionHandler;

    private boolean isBluetoothGame = false;
    private boolean isHost = false;
    private String localPlayerId = "P1";

    private final Handler bluetoothRefreshHandler = new Handler(Looper.getMainLooper());

    // 倒计时 UI 控件
    private TextView tvCountdown;

    private LinearLayout actionButtonsContainer;
    private LinearLayout playCardsContainer;
    private Button btnPlayInline;
    private Button btnPassInline;

    // 道具栏控件
    private LinearLayout propCardTracker;
    private LinearLayout propSeeThrough;
    private LinearLayout propPatternHint;
    private ImageView ivPropTracker;
    private ImageView ivPropSeeThrough;
    private ImageView ivPropPatternHint;
    private TextView tvPropTracker;
    private TextView tvPropSeeThrough;
    private TextView tvPropPatternHint;

    // 道具可用状态
    private boolean isTrackerEnabled = false;
    private boolean isSeeThroughEnabled = false;
    private boolean isPatternHintEnabled = false;


    // 牌型提示条控件
    private LinearLayout patternHintBar;
    private TextView hintSingle, hintPair, hintTriple, hintStraight, hintFlush, hintIron, hintFullHouse, hintStraightFlush;

    private FrameLayout cardTrackerLayout;

    private final Runnable bluetoothRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (gameActionHandler != null && isBluetoothGame) {
                // 蓝牙对局中只让房主驱动 AI，避免客户端也跑 AI 导致两端状态分叉
                if (isHost) {
                    gameActionHandler.triggerNextAction();
                }
            }

            bluetoothRefreshHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        EventBus.getInstance().register(this);

        // 初始化倒计时 TextView
        tvCountdown = findViewById(R.id.tv_countdown);
        if (tvCountdown != null) {
            tvCountdown.setVisibility(View.GONE);
        }

        // 初始化内联按钮
        actionButtonsContainer = findViewById(R.id.action_buttons_container);
        playCardsContainer = findViewById(R.id.play_cards_container);

        Log.d("CardGame", "actionButtonsContainer=" + actionButtonsContainer);
        Log.d("CardGame", "playCardsContainer=" + playCardsContainer);
        btnPlayInline = findViewById(R.id.btn_play_inline);
        btnPassInline = findViewById(R.id.btn_pass_inline);

        if (btnPlayInline != null) {
            btnPlayInline.setOnClickListener(v -> {
                if (gameActionHandler != null) {
                    PlayResult result = gameActionHandler.submitPlay(new ArrayList<>(selectedCardIds));
                    if (result != null) {
                        Toast.makeText(this, result.getMessage(), Toast.LENGTH_SHORT).show();
                        if (result.isSuccess()) {
                            fullRefresh();
                        }
                    }
                }
            });
        }

        if (btnPassInline != null) {
            btnPassInline.setOnClickListener(v -> {
                if (gameActionHandler != null) {
                    PassResult result = gameActionHandler.passTurn();
                    if (result != null) {
                        Toast.makeText(this, result.getMessage(), Toast.LENGTH_SHORT).show();
                        fullRefresh();
                    }
                }
            });
        }
        gameActionHandler = CardGameApplication.getGameActionHandler();
        Log.d("GameActivity", "gameActionHandler = " + gameActionHandler);

        // 设置倒计时回调（如果 GameActionHandler 是 GameController 实例）
        if (gameActionHandler instanceof GameController) {
            ((GameController) gameActionHandler).setCountdownCallback(this);
        }

        isBluetoothGame = getIntent().getBooleanExtra("is_bluetooth_game", false);
        isHost = getIntent().getBooleanExtra("is_host", false);
        localPlayerId = getIntent().getStringExtra("local_player_id");
        String ruleType = getIntent().getStringExtra("rule_type");
        if (ruleType == null) ruleType = "南方规则";
        this.ruleConfig = "北方规则".equals(ruleType) ? RuleConfig.NORTHERN : RuleConfig.SOUTHERN;

        if (!isBluetoothGame) {
            localPlayerId = "P1";
        } else if (localPlayerId == null || localPlayerId.trim().isEmpty()) {
            localPlayerId = isHost ? "P1" : "CLIENT";
        }

        setupOpponents();
        initPropBar();

        // 初始化牌型提示条控件
        patternHintBar = findViewById(R.id.pattern_hint_bar);
        hintSingle = findViewById(R.id.hint_single);
        hintPair = findViewById(R.id.hint_pair);
        hintTriple = findViewById(R.id.hint_triple);
        hintStraight = findViewById(R.id.hint_straight);
        hintFlush = findViewById(R.id.hint_flush);
        hintIron = findViewById(R.id.hint_iron);
        hintStraightFlush = findViewById(R.id.hint_straight_flush);
        hintFullHouse = findViewById(R.id.hint_full_house);

        // 初始化记牌器面板
        cardTrackerLayout = findViewById(R.id.card_tracker_layout);

        playAreaTop = findViewById(R.id.play_area_top);
        playAreaLeft = findViewById(R.id.play_area_left);
        playAreaRight = findViewById(R.id.play_area_right);

        rvHandCards = findViewById(R.id.rv_hand_cards);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false) {
            @Override
            public boolean canScrollHorizontally() {
                return false;  // 禁止水平滚动
            }
        };
        rvHandCards.setLayoutManager(layoutManager);

        selectedCardIds = new ArrayList<>();
        handCards = new ArrayList<>();

        if (gameActionHandler != null) {
            gameActionHandler.setUiRefreshCallback(() -> runOnUiThread(this::fullRefresh));
        }

        if (gameActionHandler != null) {
            bluetoothActionHandler = CardGameApplication.getBluetoothActionHandler(this);

            gameActionHandler.setBluetoothActionHandler(bluetoothActionHandler);

            if (isBluetoothGame) {
                gameActionHandler.setBluetoothMode(true, isHost, localPlayerId);
            } else {
                gameActionHandler.setBluetoothMode(false, false, "P1");
            }

            if (isBluetoothGame) {
                System.out.println("[CardGame][UI] Bluetooth game mode, host="
                        + isHost + ", localPlayerId=" + localPlayerId);

                if (isHost) {
                    gameActionHandler.setSelectedRuleType(ruleType);
                    gameActionHandler.startNewGame();
                    Toast.makeText(this, "蓝牙房主模式：已开局并同步", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "蓝牙加入者模式：等待房主同步开局", Toast.LENGTH_SHORT).show();
                }

                fullRefresh();
                bluetoothRefreshHandler.post(bluetoothRefreshRunnable);
            } else {
                System.out.println("[CardGame][UI] gameActionHandler ready, start real game flow");
                gameActionHandler.setSelectedRuleType(ruleType);
                gameActionHandler.startNewGame();
                fullRefresh();
                Toast.makeText(this, "真实联调模式", Toast.LENGTH_SHORT).show();
            }
        } else {
            System.out.println("[CardGame][UI] gameActionHandler is null, fallback to mock mode");
            useMockDataForDemo();
            Toast.makeText(this, "模拟数据模式（UI演示）", Toast.LENGTH_LONG).show();
        }


        Button btnExitGame = findViewById(R.id.btn_exit_game);
        btnExitGame.setOnClickListener(v -> {
            bluetoothRefreshHandler.removeCallbacks(bluetoothRefreshRunnable);

            Intent intent = new Intent(GameActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getInstance().unregister(this);
        bluetoothRefreshHandler.removeCallbacks(bluetoothRefreshRunnable);
    }

    private void fullRefresh() {
        if (gameActionHandler == null) return;

        GameViewData data = gameActionHandler.getGameViewData();
        if (data == null) return;

        List<String> myHandCards = data.getMyHandCards();
        handCards = (myHandCards != null) ? new ArrayList<>(myHandCards) : new ArrayList<>();

        // 不再使用底部按钮，全部由内联按钮控制
        // 因此删除 btnPlay / btnPass 的设置

        Log.d("GameCheck", "当前手牌: " + data.getMyHandCards());
        Log.d("GameCheck", "最后出牌: " + data.getLastPlayCards());
        Log.d("GameCheck", "selectedCardIds: " + selectedCardIds);

        updateOpponentsFromViewData(data);
        updatePlayAreas(data);
        // updateActionButtons(data);  // 已由 TurnChangedEvent 直接控制

        if (cardAdapter == null) {
            cardAdapter = new CardAdapter(this, handCards, position -> {
                String cardDisplay = handCards.get(position);
                if (gameActionHandler != null) {
                    gameActionHandler.toggleCardSelection(cardDisplay);
                    if (selectedCardIds.contains(cardDisplay)) {
                        selectedCardIds.remove(cardDisplay);
                    } else {
                        selectedCardIds.add(cardDisplay);
                    }
                    updatePatternHint();
                    if (cardAdapter != null) {
                        cardAdapter.notifyItemChanged(position);
                    }
                }
            });
            rvHandCards.setAdapter(cardAdapter);
        } else {
            cardAdapter.updateData(handCards);
        }

        rvHandCards.post(this::centerHandCards);
        updatePatternHint();

        if (cardTrackerLayout != null && cardTrackerLayout.getVisibility() == View.VISIBLE) {
            updateCardTracker();
        }

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

        Log.d("CardGame", "updatePlayAreas: players=" + (players != null ? players.size() : "null"));

        if (players == null || players.size() < 4 || playerLastPlayCards == null) {
            Log.d("CardGame", "updatePlayAreas: fallback to renderCardsToContainer");
            renderCardsToContainer(playCardsContainer, data.getLastPlayCards());
            return;
        }

        // 打印每个玩家的信息
        for (PlayerViewData p : players) {
            Log.d("CardGame", "Player: " + p.getPlayerId() + ", isPassed=" + p.isPassed()
                    + ", cards=" + playerLastPlayCards.get(p.getPlayerId()));
        }

        // 自己的出牌区（独立处理）
        renderSelfPlayArea(players.get(0), playerLastPlayCards);
        // 其他玩家的出牌区
        renderPlayerArea(playAreaLeft, players.get(1), playerLastPlayCards);
        renderPlayerArea(playAreaTop, players.get(2), playerLastPlayCards);
        renderPlayerArea(playAreaRight, players.get(3), playerLastPlayCards);
    }


    private void updateActionButtons(GameViewData data) {
        if (data == null) return;

        String currentPlayerId = data.getCurrentPlayerId();
        boolean isMyTurn = localPlayerId.equals(currentPlayerId);

        Log.d("CardGame", "updateActionButtons: localPlayerId=" + localPlayerId
                + ", currentPlayerId=" + currentPlayerId
                + ", isMyTurn=" + isMyTurn);

        boolean isCurrentPlayerHuman = false;
        if (data.getPlayers() != null) {
            for (PlayerViewData p : data.getPlayers()) {
                if (p.getPlayerId().equals(currentPlayerId)) {
                    isCurrentPlayerHuman = p.isHuman();
                    break;
                }
            }
        }

        boolean shouldShowButtons = !data.isGameOver() && isMyTurn && isCurrentPlayerHuman;

        Log.d("CardGame", "shouldShowButtons=" + shouldShowButtons);
        if (actionButtonsContainer != null && playCardsContainer != null) {
            if (shouldShowButtons) {
                // 轮到我了，显示按钮，隐藏出牌展示区
                actionButtonsContainer.setVisibility(View.VISIBLE);
                playCardsContainer.setVisibility(View.GONE);
            } else {
                // 不是我回合，隐藏按钮，显示出牌展示区
                actionButtonsContainer.setVisibility(View.GONE);
                playCardsContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    private void updateActionButtonsAndHighlight() {
        if (gameActionHandler == null) return;
        GameViewData data = gameActionHandler.getGameViewData();
        if (data == null) return;

        // 只更新对手高亮和按钮
        updateOpponentsFromViewData(data);
        updateActionButtons(data);
    }

    /**
     * 为单个玩家渲染出牌区：
     * 如果没有出牌记录且该玩家本轮已 Pass，则显示“不出”；
     * 否则显示其最后一次出的牌。
     */
    private void renderPlayerArea(LinearLayout area,
                                  PlayerViewData player,
                                  Map<String, List<String>> lastPlayCards) {
        if (area == null) return;

        // 其他玩家的出牌区（原有逻辑）
        area.removeAllViews();
        area.setGravity(Gravity.CENTER);

        List<String> cards = lastPlayCards.get(player.getPlayerId());

        if ((cards == null || cards.isEmpty()) && player.isPassed()) {
            TextView textView = new TextView(this);
            textView.setText("不出");
            textView.setTextColor(getColor(android.R.color.white));
            textView.setTextSize(18f);
            textView.setGravity(Gravity.CENTER);
            area.addView(textView);
        } else if (cards != null && !cards.isEmpty()) {
            renderCardsToArea(area, cards);
        }
    }

    private void renderCardsToArea(LinearLayout playArea, List<String> cards) {
        if (playArea == null) return;

        playArea.removeAllViews();
        playArea.setGravity(Gravity.CENTER);

        if (cards == null || cards.isEmpty()) return;

        // ✅ 对出牌排序：按点数升序（3最小，2最大）
        List<String> sortedCards = new ArrayList<>(cards);
        sortedCards.sort((a, b) -> {
            int weightA = getCardRankWeight(a);
            int weightB = getCardRankWeight(b);
            return Integer.compare(weightA, weightB);
        });

        float density = getResources().getDisplayMetrics().density;
        int cardWidthPx = (int) (36 * density);
        int cardHeightPx = (int) (56 * density);
        int overlapPx = (int) (-8 * density);

        for (int i = 0; i < sortedCards.size(); i++) {
            String cardStr = sortedCards.get(i);
            View cardView = getLayoutInflater().inflate(R.layout.item_play_card, playArea, false);

            CardView cv = cardView.findViewById(R.id.card_view);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardWidthPx, cardHeightPx);
            if (i > 0) params.leftMargin = overlapPx;
            params.gravity = Gravity.CENTER_VERTICAL;
            cv.setLayoutParams(params);

            ImageView iv = cardView.findViewById(R.id.iv_play_card);
            int resId = getCardDrawableResource(cardStr);
            if (resId != 0) {
                iv.setImageResource(resId);
            } else {
                iv.setImageResource(android.R.drawable.ic_menu_gallery);
            }
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);

            playArea.addView(cardView);
        }
    }

    private void clearPlayAreas() {
        if (playAreaTop != null) playAreaTop.removeAllViews();
        if (playAreaLeft != null) playAreaLeft.removeAllViews();
        if (playAreaRight != null) playAreaRight.removeAllViews();
        // 注意：不清理 playAreaSelf，因为自己的区域由 playCardsContainer 独立管理
    }

    private void renderSelfPlayArea(PlayerViewData player, Map<String, List<String>> lastPlayCards) {
        if (playCardsContainer == null) return;

        Log.d("CardGame", "renderSelfPlayArea called for player=" + player.getPlayerId()
                + ", isPassed=" + player.isPassed());

        playCardsContainer.removeAllViews();
        playCardsContainer.setGravity(Gravity.CENTER);

        List<String> cards = lastPlayCards.get(player.getPlayerId());
        Log.d("CardGame", "renderSelfPlayArea: cards=" + cards);

        if ((cards == null || cards.isEmpty()) && player.isPassed()) {
            TextView textView = new TextView(this);
            textView.setText("不出");
            textView.setTextColor(getColor(android.R.color.white));
            textView.setTextSize(18f);
            textView.setGravity(Gravity.CENTER);
            playCardsContainer.addView(textView);
            Log.d("CardGame", "renderSelfPlayArea: showing '不出'");
        } else if (cards != null && !cards.isEmpty()) {
            renderCardsToContainer(playCardsContainer, cards);
            Log.d("CardGame", "renderSelfPlayArea: showing " + cards.size() + " cards");
        } else {
            Log.d("CardGame", "renderSelfPlayArea: no cards and not passed, showing nothing");
        }
    }

    private void renderCardsToContainer(LinearLayout container, List<String> cards) {
        if (container == null) return;

        container.removeAllViews();
        container.setGravity(Gravity.CENTER);

        if (cards == null || cards.isEmpty()) return;

        // 对出牌排序
        List<String> sortedCards = new ArrayList<>(cards);
        sortedCards.sort((a, b) -> {
            int weightA = getCardRankWeight(a);
            int weightB = getCardRankWeight(b);
            return Integer.compare(weightA, weightB);
        });

        float density = getResources().getDisplayMetrics().density;
        int cardWidthPx = (int) (36 * density);
        int cardHeightPx = (int) (56 * density);
        int overlapPx = (int) (-8 * density);

        for (int i = 0; i < sortedCards.size(); i++) {
            String cardStr = sortedCards.get(i);
            View cardView = getLayoutInflater().inflate(R.layout.item_play_card, container, false);

            CardView cv = cardView.findViewById(R.id.card_view);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardWidthPx, cardHeightPx);
            if (i > 0) params.leftMargin = overlapPx;
            params.gravity = Gravity.CENTER_VERTICAL;
            cv.setLayoutParams(params);

            ImageView iv = cardView.findViewById(R.id.iv_play_card);
            int resId = getCardDrawableResource(cardStr);
            if (resId != 0) {
                iv.setImageResource(resId);
            } else {
                iv.setImageResource(android.R.drawable.ic_menu_gallery);
            }
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);

            container.addView(cardView);
        }
    }

    private int getCardRankWeight(String cardId) {
        if (cardId == null || cardId.length() < 2) return 0;
        String rank = cardId.substring(1);
        switch (rank) {
            case "2":
                return 13;
            case "A":
                return 12;
            case "K":
                return 11;
            case "Q":
                return 10;
            case "J":
                return 9;
            case "10":
                return 8;
            case "9":
                return 7;
            case "8":
                return 6;
            case "7":
                return 5;
            case "6":
                return 4;
            case "5":
                return 3;
            case "4":
                return 2;
            case "3":
                return 1;
            default:
                return 0;
        }
    }

    private int getCardDrawableResource(String cardId) {
        if (cardId == null || cardId.length() < 2) return 0;

        String suitPart;
        char suitChar = cardId.charAt(0);

        switch (suitChar) {
            case '♥':
                suitPart = "heart";
                break;
            case '♠':
                suitPart = "spade";
                break;
            case '♦':
                suitPart = "diamond";
                break;
            case '♣':
                suitPart = "club";
                break;
            default:
                return 0;
        }

        String rank = cardId.substring(1);
        String rankPart;

        switch (rank) {
            case "A":
                rankPart = "ace";
                break;
            case "J":
                rankPart = "jack";
                break;
            case "Q":
                rankPart = "queen";
                break;
            case "K":
                rankPart = "king";
                break;
            default:
                rankPart = rank;
                break;
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

        int expectedLeftMargin = (screenWidth - totalWidth) / 2;
        if (expectedLeftMargin < 0) expectedLeftMargin = 0;

        Log.d("CenterDebug", "牌数=" + handCards.size() + ", 期望左边距=" + expectedLeftMargin);

        // 使用 setX 直接设置绝对位置
        rvHandCards.setX(expectedLeftMargin);
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

    private void initPropBar() {
        // 获取控件
        propCardTracker = findViewById(R.id.prop_card_tracker);
        propSeeThrough = findViewById(R.id.prop_see_through);
        propPatternHint = findViewById(R.id.prop_pattern_hint);
        ivPropTracker = findViewById(R.id.iv_prop_tracker);
        ivPropSeeThrough = findViewById(R.id.iv_prop_see_through);
        ivPropPatternHint = findViewById(R.id.iv_prop_pattern_hint);
        tvPropTracker = findViewById(R.id.tv_prop_tracker);
        tvPropSeeThrough = findViewById(R.id.tv_prop_see_through);
        tvPropPatternHint = findViewById(R.id.tv_prop_pattern_hint);

        // 读取房间设置
        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        if (!isBluetoothGame) {
            // 练习场模式，所有道具可用
            isTrackerEnabled = true;
            isSeeThroughEnabled = true;
            isPatternHintEnabled = true;
        } else {
            // 蓝牙模式，根据房间设置
            isTrackerEnabled = prefs.getBoolean("prop_card_tracker", false);
            isSeeThroughEnabled = prefs.getBoolean("prop_see_through", false);
            isPatternHintEnabled = prefs.getBoolean("prop_pattern_hint", false);
            Log.d("PropDebug", "isBluetoothGame=" + isBluetoothGame);
            Log.d("PropDebug", "tracker=" + prefs.getBoolean("prop_card_tracker", false));
            Log.d("PropDebug", "seeThrough=" + prefs.getBoolean("prop_see_through", false));
            Log.d("PropDebug", "patternHint=" + prefs.getBoolean("prop_pattern_hint", false));
        }

        // 更新 UI 颜色和可用性
        updatePropUI();

        // 设置点击事件
        propCardTracker.setOnClickListener(v -> {
            if (isTrackerEnabled) {
                if (cardTrackerLayout.getVisibility() == View.VISIBLE) {
                    cardTrackerLayout.setVisibility(View.GONE);
                } else {
                    updateCardTracker();
                    cardTrackerLayout.setVisibility(View.VISIBLE);
                }
            } else {
                Toast.makeText(this, "房间未开启记牌器道具", Toast.LENGTH_SHORT).show();
            }
        });

        propSeeThrough.setOnClickListener(v -> {
            if (isSeeThroughEnabled) {
                // 透视道具：显示幽默弹窗
                new AlertDialog.Builder(this)
                        .setTitle("🔮 透视")
                        .setMessage("你运气不错！🎉\n\n（这只是个玩笑，并没有真正的透视功能！）")
                        .setPositiveButton("哈哈", null)
                        .show();
            } else {
                Toast.makeText(this, "房间未开启透视道具", Toast.LENGTH_SHORT).show();
            }
        });

        // ========== 牌型提示道具：点击显示/隐藏提示条，并更新高亮 ==========
        propPatternHint.setOnClickListener(v -> {
            if (isPatternHintEnabled) {
                if (patternHintBar.getVisibility() == View.VISIBLE) {
                    patternHintBar.setVisibility(View.GONE);
                } else {
                    patternHintBar.setVisibility(View.VISIBLE);
                    updatePatternHint(); // 显示时立即更新当前选中的牌型
                }
            } else {
                Toast.makeText(this, "房间未开启牌型提示道具", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updatePropUI() {
        // 记牌器
        if (isTrackerEnabled) {
            propCardTracker.setEnabled(true);
            ivPropTracker.setColorFilter(Color.parseColor("#FFD700"));
            tvPropTracker.setTextColor(Color.parseColor("#FFD700"));
        } else {
            propCardTracker.setEnabled(false);
            ivPropTracker.setColorFilter(Color.parseColor("#888888"));
            tvPropTracker.setTextColor(Color.parseColor("#888888"));
        }

        // 透视
        if (isSeeThroughEnabled) {
            propSeeThrough.setEnabled(true);
            ivPropSeeThrough.setColorFilter(Color.parseColor("#FFD700"));
            tvPropSeeThrough.setTextColor(Color.parseColor("#FFD700"));
        } else {
            propSeeThrough.setEnabled(false);
            ivPropSeeThrough.setColorFilter(Color.parseColor("#888888"));
            tvPropSeeThrough.setTextColor(Color.parseColor("#888888"));
        }

        // 牌型提示
        if (isPatternHintEnabled) {
            propPatternHint.setEnabled(true);
            ivPropPatternHint.setColorFilter(Color.parseColor("#FFD700"));
            tvPropPatternHint.setTextColor(Color.parseColor("#FFD700"));
        } else {
            propPatternHint.setEnabled(false);
            ivPropPatternHint.setColorFilter(Color.parseColor("#888888"));
            tvPropPatternHint.setTextColor(Color.parseColor("#888888"));
        }
    }

    private Card convertDisplayToCard(String display) {
        if (display == null || display.length() < 2) return null;
        String suitSymbol = display.substring(0, 1);
        String rankStr = display.substring(1);

        Suit suit;
        switch (suitSymbol) {
            case "♥": suit = Suit.HEARTS; break;
            case "♠": suit = Suit.SPADES; break;
            case "♦": suit = Suit.DIAMONDS; break;
            case "♣": suit = Suit.CLUBS; break;
            default: return null;
        }

        Rank rank;
        switch (rankStr) {
            case "2": rank = Rank.TWO; break;
            case "A": rank = Rank.ACE; break;
            case "K": rank = Rank.KING; break;
            case "Q": rank = Rank.QUEEN; break;
            case "J": rank = Rank.JACK; break;
            case "10": rank = Rank.TEN; break;
            case "9": rank = Rank.NINE; break;
            case "8": rank = Rank.EIGHT; break;
            case "7": rank = Rank.SEVEN; break;
            case "6": rank = Rank.SIX; break;
            case "5": rank = Rank.FIVE; break;
            case "4": rank = Rank.FOUR; break;
            case "3": rank = Rank.THREE; break;
            default: return null;
        }

        return new Card(null, suit, rank);
    }

    private Rank getRankFromDisplay(String display) {
        if (display == null || display.length() < 2) return null;
        String rankStr = display.substring(1);
        switch (rankStr) {
            case "2": return Rank.TWO;
            case "A": return Rank.ACE;
            case "K": return Rank.KING;
            case "Q": return Rank.QUEEN;
            case "J": return Rank.JACK;
            case "10": return Rank.TEN;
            case "9": return Rank.NINE;
            case "8": return Rank.EIGHT;
            case "7": return Rank.SEVEN;
            case "6": return Rank.SIX;
            case "5": return Rank.FIVE;
            case "4": return Rank.FOUR;
            case "3": return Rank.THREE;
            default: return null;
        }
    }

    private void updatePatternHint() {
        if (patternHintBar == null || patternHintBar.getVisibility() != View.VISIBLE) return;
        if (!isPatternHintEnabled) return;

        // 重置所有文字为灰色
        setAllHintTextColor(Color.parseColor("#888888"));

        if (selectedCardIds == null || selectedCardIds.isEmpty()) return;

        List<Card> selectedCards = new ArrayList<>();
        for (String display : selectedCardIds) {
            Card card = convertDisplayToCard(display);
            if (card != null) selectedCards.add(card);
        }
        if (selectedCards.isEmpty()) return;

        PatternRecognizer recognizer = new PatternRecognizer(ruleConfig);
        PatternRecognizer.PatternInfo info = recognizer.recognizePattern(selectedCards);
        if (info.getType() == PatternRecognizer.PatternType.INVALID) return;

        switch (info.getType()) {
            case SINGLE:
                hintSingle.setTextColor(Color.parseColor("#FFD700"));
                break;
            case PAIR:
                hintPair.setTextColor(Color.parseColor("#FFD700"));
                break;
            case TRIPLE:
                hintTriple.setTextColor(Color.parseColor("#FFD700"));
                break;
            case STRAIGHT:
                hintStraight.setTextColor(Color.parseColor("#FFD700"));
                break;
            case FLUSH:
                hintFlush.setTextColor(Color.parseColor("#FFD700"));
                break;
            case IRON_BRANCH:
                hintIron.setTextColor(Color.parseColor("#FFD700"));
                break;
            case STRAIGHT_FLUSH:
                hintStraightFlush.setTextColor(Color.parseColor("#FFD700"));
                break;
            case FULL_HOUSE:   // 新增：葫芦
                hintFullHouse.setTextColor(Color.parseColor("#FFD700"));
                break;
            default:
                break;
        }
    }

    private void updateCardTracker() {
        if (gameActionHandler == null) return;
        GameViewData data = gameActionHandler.getGameViewData();
        if (data == null) return;

        // 移除旧视图
        cardTrackerLayout.removeAllViews();

        TableLayout table = new TableLayout(this);
        table.setLayoutParams(new TableLayout.LayoutParams(
                TableLayout.LayoutParams.WRAP_CONTENT,
                TableLayout.LayoutParams.WRAP_CONTENT));

        // 点数顺序（2 A K Q J 10 9 8 7 6 5 4 3）
        String[] rankOrder = {"2", "A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3"};
        Rank[] rankEnums = {Rank.TWO, Rank.ACE, Rank.KING, Rank.QUEEN, Rank.JACK, Rank.TEN,
                Rank.NINE, Rank.EIGHT, Rank.SEVEN, Rank.SIX, Rank.FIVE, Rank.FOUR, Rank.THREE};

        // 统计已打出的牌（从 GameViewData 获取累计列表）
        Map<Rank, Integer> played = new HashMap<>();
        for (Rank r : rankEnums) played.put(r, 0);
        List<Card> allPlayed = data.getAllPlayedCards();
        if (allPlayed != null) {
            for (Card card : allPlayed) {
                Rank r = card.getRank();
                if (played.containsKey(r)) {
                    played.put(r, played.get(r) + 1);
                }
            }
        }

        // 第一行：点数
        TableRow headerRow = new TableRow(this);
        for (int i = 0; i < rankOrder.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(rankOrder[i]);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(Color.BLACK);
            tv.setTextSize(11);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setPadding(4, 4, 4, 4);
            tv.setBackgroundResource(R.drawable.cell_border);
            headerRow.addView(tv);
        }
        table.addView(headerRow);

        // 第二行：剩余数量 = 4 - 已打出张数
        TableRow dataRow = new TableRow(this);
        for (int i = 0; i < rankOrder.length; i++) {
            int remain = 4 - played.get(rankEnums[i]);
            TextView tv = new TextView(this);
            tv.setText(String.valueOf(remain));
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(11);
            tv.setPadding(4, 4, 4, 4);
            if (remain > 0) {
                tv.setTextColor(Color.parseColor("#002060"));
            } else {
                tv.setTextColor(Color.GRAY);
            }
            tv.setBackgroundResource(R.drawable.cell_border);
            dataRow.addView(tv);
        }
        table.addView(dataRow);

        cardTrackerLayout.addView(table);
    }

    private void setAllHintTextColor(int color) {
        if (hintSingle != null) hintSingle.setTextColor(color);
        if (hintPair != null) hintPair.setTextColor(color);
        if (hintTriple != null) hintTriple.setTextColor(color);
        if (hintStraight != null) hintStraight.setTextColor(color);
        if (hintFlush != null) hintFlush.setTextColor(color);
        if (hintIron != null) hintIron.setTextColor(color);
        if (hintStraightFlush != null) hintStraightFlush.setTextColor(color);
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

        // ✅ 计算本机玩家的排名（放在 dialog.show() 之前）
        int myRank = -1;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getPlayerId().equals(localPlayerId)) {
                myRank = i + 1;
                break;
            }
        }

        TextView tvMyRank = dialogView.findViewById(R.id.tv_my_rank);
        if (tvMyRank != null && myRank != -1) {
            tvMyRank.setText("您的排名：第 " + myRank + " 名");
        } else if (tvMyRank != null) {
            tvMyRank.setText("您的排名：第 -- 名");
        }

        ImageButton btnBackHome = dialogView.findViewById(R.id.btn_back_home);
        btnBackHome.setOnClickListener(v -> {
            dialog.dismiss();
            bluetoothRefreshHandler.removeCallbacks(bluetoothRefreshRunnable);
            Intent intent = new Intent(GameActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        dialog.show();
    }

    // ========== 实现 GameController.CountdownUICallback 接口 ==========
    @Override
    public void showCountdown() {
        runOnUiThread(() -> {
            if (tvCountdown != null) {
                tvCountdown.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void updateCountdown(int secondsLeft) {
        runOnUiThread(() -> {
            if (tvCountdown != null) {
                tvCountdown.setText(String.format("无牌可出，%d秒后自动跳过", secondsLeft));
            }
        });
    }

    @Override
    public void hideCountdown() {
        runOnUiThread(() -> {
            if (tvCountdown != null) {
                tvCountdown.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onEvent(GameEvent event) {
        if (event instanceof CardPlayedEvent) {
            CardPlayedEvent e = (CardPlayedEvent) event;
            Log.d("EventBus", "收到出牌事件: playerId=" + e.getPlayerId());
            runOnUiThread(this::fullRefresh);
        }
        else if (event instanceof PlayerPassedEvent) {
            PlayerPassedEvent e = (PlayerPassedEvent) event;
            Log.d("EventBus", "收到过牌事件: playerId=" + e.getPlayerId());
            runOnUiThread(this::fullRefresh);
        }
        else if (event instanceof TurnChangedEvent) {
            TurnChangedEvent e = (TurnChangedEvent) event;
            String newPlayerId = e.getNewCurrentPlayerId();
            Log.d("EventBus", "收到回合切换事件: newPlayerId=" + newPlayerId);
            runOnUiThread(() -> {
                fullRefresh();
                if (actionButtonsContainer != null && playCardsContainer != null) {
                    boolean isMyTurn = localPlayerId.equals(newPlayerId);
                    if (isMyTurn) {
                        actionButtonsContainer.setVisibility(View.VISIBLE);
                        playCardsContainer.setVisibility(View.GONE);
                    } else {
                        actionButtonsContainer.setVisibility(View.GONE);
                        playCardsContainer.setVisibility(View.VISIBLE);
                    }
                }
            });
        } else if (event instanceof GameOverEvent) {
            GameOverEvent e = (GameOverEvent) event;
            Log.d("EventBus", "收到游戏结束事件: winnerId=" + e.getWinnerId());
            runOnUiThread(() -> {
                if (!gameOverDialogShown) {
                    fullRefresh();
                }
            });
        }
    }
}