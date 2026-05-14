package com.example.cardgame.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.cardgame.CardGameApplication;
import com.example.cardgame.R;
import com.example.cardgame.controller.BluetoothActionHandler;
import com.example.cardgame.dto.BluetoothViewData;

import java.util.List;

public class RoomLobbyActivity extends AppCompatActivity {

    private TextView tvTitle;
    private ImageButton btnBack;
    private Button btnStartGame, btnDisconnect, btnAddAi;
    private TextView tvNeedCount;
    private LinearLayout llAiControl;

    private CardView[] playerCards = new CardView[4];
    private TextView[] tvNames = new TextView[4];
    private TextView[] tvStatus = new TextView[4];
    private View[] ivCrowns = new View[4];

    private boolean isHost;
    private boolean gameStarted = false;
    private int currentPlayerCount;
    private boolean[] isAi = new boolean[4];
    private boolean[] isConnected = new boolean[4];

    private static final int MAX_PLAYERS = 4;

    private BluetoothActionHandler bluetoothActionHandler;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String localPlayerId = "P1";

    private final Runnable refreshBluetoothStateRunnable = new Runnable() {
        @Override
        public void run() {
            refreshBluetoothState();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_lobby);

        bluetoothActionHandler = CardGameApplication.getBluetoothActionHandler(this);

        isHost = getIntent().getBooleanExtra("is_host", false);
        localPlayerId = getIntent().getStringExtra("local_player_id");
        if (localPlayerId == null || localPlayerId.trim().isEmpty()) {
            localPlayerId = isHost ? "P1" : "CLIENT";  // 客户端身份待 HOST 分配
        }

        initViews();
        setupTitleFont();
        setupListeners();
        initRoomState();

        handler.post(refreshBluetoothStateRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refreshBluetoothStateRunnable);
    }

    private void initRoomState() {
        if (isHost) {
            isConnected[0] = true;
            isAi[0] = false;
            tvNames[0].setText(getPlayerName());
            tvStatus[0].setText("房主 / 本机");
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
        } else {
            // CLIENT 模式：初始只知道房主和自己
            isConnected[0] = true;
            isAi[0] = false;
            tvNames[0].setText("房主");
            tvStatus[0].setText("等待确认");
            ivCrowns[0].setVisibility(View.VISIBLE);

            // 根据 playerId 确定初始 slot（P2→1, P3→2, P4→3, 其他→1）
            int mySlot = 1;
            if (localPlayerId != null) {
                switch (localPlayerId) {
                    case "P3": mySlot = 2; break;
                    case "P4": mySlot = 3; break;
                    default: mySlot = 1; break;
                }
            }
            isConnected[mySlot] = true;
            isAi[mySlot] = false;
            tvNames[mySlot].setText(getPlayerName());
            tvStatus[mySlot].setText("本机");
            ivCrowns[mySlot].setVisibility(View.GONE);

            for (int i = 0; i < MAX_PLAYERS; i++) {
                if (isConnected[i]) continue;
                isConnected[i] = false;
                isAi[i] = false;
                tvNames[i].setText("等待加入");
                tvStatus[i].setText("未连接");
                ivCrowns[i].setVisibility(View.GONE);
            }

            currentPlayerCount = 2;
            llAiControl.setVisibility(View.GONE);
        }

        updateAiControl();
        updateStartButtonState();
    }

    private void refreshBluetoothState() {
        if (bluetoothActionHandler == null) {
            return;
        }

        BluetoothViewData viewData = bluetoothActionHandler.getBluetoothViewData();
        if (viewData == null) {
            return;
        }

        // ——— 从 ConnectedDevices 列表刷新 slot ———
        List<BluetoothViewData.ConnectedDevice> connectedDevices = viewData.getConnectedDevices();
        if (connectedDevices != null) {
            for (BluetoothViewData.ConnectedDevice device : connectedDevices) {
                int slot = device.slotIndex;
                if (slot < 0 || slot >= MAX_PLAYERS) continue;

                // 跳过自己的 slot
                if (device.playerId.equals(localPlayerId)) continue;

                isConnected[slot] = true;
                isAi[slot] = false;
                tvNames[slot].setText(device.deviceName != null && !device.deviceName.isEmpty()
                        ? device.deviceName
                        : device.playerId);
                tvStatus[slot].setText("已连接");
                ivCrowns[slot].setVisibility(View.GONE);
            }
        }

        // ——— 状态文本 ———
        String statusText = viewData.getStatusText();
        if (statusText != null && !statusText.isEmpty()) {
            if (isHost) {
                tvStatus[0].setText(statusText);
            } else {
                tvStatus[1].setText(statusText);
            }
        }

        // ——— CLIENT 端：检测 assignedPlayerId ———
        if (!isHost) {
            String assignedId = viewData.getAssignedPlayerId();
            if (assignedId != null && !assignedId.equals(localPlayerId)) {
                localPlayerId = assignedId;
                int slot = viewData.getAssignedSlotIndex();
                if (slot >= 0 && slot < MAX_PLAYERS) {
                    // 更新本机 slot
                    isConnected[slot] = true;
                    isAi[slot] = false;
                    tvNames[slot].setText(getPlayerName());
                    tvStatus[slot].setText("本机");
                    ivCrowns[slot].setVisibility(View.GONE);
                }
            }
        }

        currentPlayerCount = countConnectedPlayers();

        // ——— 错误 ———
        if (viewData.getErrorMessage() != null && !viewData.getErrorMessage().trim().isEmpty()) {
            System.out.println("[CardGame][UI][BLUETOOTH] error=" + viewData.getErrorMessage());
        }

        // ——— CLIENT 端自动进入游戏 ———
        if (!isHost
                && !gameStarted
                && "INIT_GAME".equals(viewData.getLastReceivedMessageType())) {
            gameStarted = true;

            Toast.makeText(this, "房主已开始游戏", Toast.LENGTH_SHORT).show();

            handler.removeCallbacks(refreshBluetoothStateRunnable);

            Intent intent = new Intent(RoomLobbyActivity.this, GameActivity.class);
            intent.putExtra("is_bluetooth_game", true);
            intent.putExtra("is_host", false);
            intent.putExtra("local_player_id", localPlayerId);
            startActivity(intent);
            finish();
            return;
        }

        updateAiControl();
        updateStartButtonState();
    }

    private int countConnectedPlayers() {
        int count = 0;
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (isConnected[i] || isAi[i]) {
                count++;
            }
        }
        return count;
    }

    private boolean hasRealRemotePlayer() {
        if (bluetoothActionHandler == null) {
            return false;
        }

        BluetoothViewData viewData = bluetoothActionHandler.getBluetoothViewData();
        if (viewData == null) {
            return false;
        }

        return viewData.isConnected();
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tv_title);
        btnBack = findViewById(R.id.btn_back);
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
                        if (bluetoothActionHandler != null) {
                            bluetoothActionHandler.disconnectBluetooth();
                        }

                        Intent intent = new Intent(RoomLobbyActivity.this, RoomSelectionActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        btnStartGame.setOnClickListener(v -> {
            if (!isHost) {
                Toast.makeText(this, "等待房主开始游戏", Toast.LENGTH_SHORT).show();
                return;
            }

            if (currentPlayerCount == MAX_PLAYERS) {
                gameStarted = true;

                handler.removeCallbacks(refreshBluetoothStateRunnable);

                boolean hasRealRemotePlayer = hasRealRemotePlayer();

                Intent intent = new Intent(RoomLobbyActivity.this, GameActivity.class);

                if (hasRealRemotePlayer) {
                    // HOST + 客户端(们) + AI：蓝牙对局
                    intent.putExtra("is_bluetooth_game", true);
                    intent.putExtra("is_host", true);
                    intent.putExtra("local_player_id", "P1");

                    Toast.makeText(this, "蓝牙对局（4人）开始", Toast.LENGTH_SHORT).show();
                } else {
                    // HOST + 3AI：本地 AI 对局
                    if (bluetoothActionHandler != null) {
                        bluetoothActionHandler.disconnectBluetooth();
                    }

                    intent.putExtra("is_bluetooth_game", false);
                    intent.putExtra("is_host", false);
                    intent.putExtra("local_player_id", "P1");

                    Toast.makeText(this, "本地 AI 对局开始", Toast.LENGTH_SHORT).show();
                }

                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "人数不足4人，无法开始游戏", Toast.LENGTH_SHORT).show();
            }
        });

        btnAddAi.setOnClickListener(v -> addAiPlayer());
    }

    private void addAiPlayer() {
        if (!isHost) {
            Toast.makeText(this, "只有房主可以添加 AI", Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (!isConnected[i] && !isAi[i]) {
                isAi[i] = true;
                isConnected[i] = true;
                tvNames[i].setText("AI 玩家");
                tvStatus[i].setText("已连接");
                ivCrowns[i].setVisibility(View.GONE);

                currentPlayerCount = countConnectedPlayers();
                updateAiControl();
                updateStartButtonState();
                break;
            }
        }
    }

    private void updateAiControl() {
        if (!isHost) {
            return;
        }

        int need = MAX_PLAYERS - currentPlayerCount;
        if (need < 0) {
            need = 0;
        }

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
        if (!isHost) {
            btnStartGame.setText("等待房主开始");
            btnStartGame.setEnabled(false);
            return;
        }

        btnStartGame.setText("开始游戏");
        btnStartGame.setEnabled(currentPlayerCount == MAX_PLAYERS);
    }

    private String getPlayerName() {
        return getSharedPreferences("game_prefs", MODE_PRIVATE)
                .getString("player_name", "玩家");
    }
}
