package com.example.cardgame.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;   // 新增
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.example.cardgame.R;

public class RoomLobbyActivity extends AppCompatActivity {

    // UI 组件
    private TextView tvTitle;
    private ImageButton btnBack;   // 改为 ImageButton
    private Button btnStartGame, btnDisconnect, btnAddAi;
    private TextView tvNeedCount;
    private LinearLayout llAiControl;

    // 玩家卡片相关
    private CardView[] playerCards = new CardView[4];
    private TextView[] tvNames = new TextView[4];
    private TextView[] tvStatus = new TextView[4];
    private View[] ivCrowns = new View[4];

    // 数据
    private boolean isHost;
    private int currentPlayerCount;
    private boolean[] isAi = new boolean[4];
    private boolean[] isConnected = new boolean[4];

    private static final int MAX_PLAYERS = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_lobby);

        isHost = getIntent().getBooleanExtra("is_host", false);

        initViews();
        setupTitleFont();
        setupListeners();

        if (isHost) {
            isConnected[0] = true;
            isAi[0] = false;
            tvNames[0].setText(getPlayerName());
            tvStatus[0].setText("已连接");
            ivCrowns[0].setVisibility(View.VISIBLE);

            for (int i = 1; i < MAX_PLAYERS; i++) {
                isConnected[i] = false;
                isAi[i] = false;
                tvNames[i].setText("等待加入");
                tvStatus[i].setText("未连接");
                ivCrowns[i].setVisibility(View.GONE);
            }
            currentPlayerCount = 1;
            llAiControl.setVisibility(View.VISIBLE);
            updateAiControl();
        } else {
            isConnected[0] = true;
            isAi[0] = false;
            tvNames[0].setText("房主");
            tvStatus[0].setText("已连接");
            ivCrowns[0].setVisibility(View.VISIBLE);

            isConnected[1] = true;
            isAi[1] = false;
            tvNames[1].setText(getPlayerName());
            tvStatus[1].setText("已连接");
            ivCrowns[1].setVisibility(View.GONE);

            currentPlayerCount = 2;
            llAiControl.setVisibility(View.GONE);
        }
        updateStartButtonState();
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tv_title);
        btnBack = findViewById(R.id.btn_back);          // 现在类型匹配
        btnStartGame = findViewById(R.id.btn_start_game);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        btnAddAi = findViewById(R.id.btn_add_ai);
        tvNeedCount = findViewById(R.id.tv_need_count);
        llAiControl = findViewById(R.id.ll_ai_control);

        playerCards[0] = findViewById(R.id.card_player1);
        playerCards[1] = findViewById(R.id.card_player2);
        playerCards[2] = findViewById(R.id.card_player3);
        playerCards[3] = findViewById(R.id.card_player4);

        tvNames[0] = findViewById(R.id.tv_name1);
        tvNames[1] = findViewById(R.id.tv_name2);
        tvNames[2] = findViewById(R.id.tv_name3);
        tvNames[3] = findViewById(R.id.tv_name4);

        tvStatus[0] = findViewById(R.id.tv_status1);
        tvStatus[1] = findViewById(R.id.tv_status2);
        tvStatus[2] = findViewById(R.id.tv_status3);
        tvStatus[3] = findViewById(R.id.tv_status4);

        ivCrowns[0] = findViewById(R.id.iv_crown1);
        ivCrowns[1] = findViewById(R.id.iv_crown2);
        ivCrowns[2] = findViewById(R.id.iv_crown3);
        ivCrowns[3] = findViewById(R.id.iv_crown4);
    }

    private void setupTitleFont() {
        Typeface typeface = Typeface.createFromAsset(getAssets(), "my_custom_font.ttf");
        tvTitle.setTypeface(typeface);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnDisconnect.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("断开连接")
                    .setMessage(isHost ? "确定要取消房间吗？" : "确定要断开连接吗？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        Intent intent = new Intent(RoomLobbyActivity.this, RoomSelectionActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        btnStartGame.setOnClickListener(v -> {
            if (currentPlayerCount == MAX_PLAYERS) {
                Intent intent = new Intent(RoomLobbyActivity.this, GameActivity.class);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "人数不足4人，无法开始游戏", Toast.LENGTH_SHORT).show();
            }
        });

        btnAddAi.setOnClickListener(v -> addAiPlayer());
    }

    private void addAiPlayer() {
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (!isConnected[i] && !isAi[i]) {
                isAi[i] = true;
                isConnected[i] = true;
                tvNames[i].setText("AI 玩家");
                tvStatus[i].setText("已连接");
                ivCrowns[i].setVisibility(View.GONE);
                currentPlayerCount++;
                updateAiControl();
                updateStartButtonState();
                break;
            }
        }
    }

    private void updateAiControl() {
        int need = MAX_PLAYERS - currentPlayerCount;
        tvNeedCount.setText("还需 " + need + " 人");
        if (need <= 0) {
            btnAddAi.setEnabled(false);
            btnAddAi.setText("已满员");
        } else {
            btnAddAi.setEnabled(true);
            btnAddAi.setText("+ AI");
        }
    }

    private void updateStartButtonState() {
        btnStartGame.setEnabled(currentPlayerCount == MAX_PLAYERS);
    }

    private String getPlayerName() {
        return getSharedPreferences("game_prefs", MODE_PRIVATE)
                .getString("player_name", "玩家");
    }
}