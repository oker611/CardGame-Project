package com.example.cardgame.ui;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.cardgame.CardGameApplication;
import com.example.cardgame.R;
import com.example.cardgame.controller.BluetoothActionHandler;
import com.example.cardgame.util.BluetoothPermissionHelper;

public class RoomSettingsActivity extends AppCompatActivity {

    private static final int REQUEST_BLUETOOTH_PERMISSION = 2001;
    private static final int DISCOVERABLE_DURATION_SECONDS = 300;   // 添加这一行

    private RadioGroup rgRule;
    private CheckBox cbCardTracker, cbSeeThrough, cbPatternHint;
    private RadioGroup rgAiStrategy;
    private Button btnBack, btnStartBluetooth;
    private BluetoothActionHandler bluetoothActionHandler;

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    startBluetoothRoomFlow();
                } else {
                    Toast.makeText(this, "蓝牙未开启，无法创建房间", Toast.LENGTH_LONG).show();
                }
            }
    );

    private final ActivityResultLauncher<Intent> discoverableLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_CANCELED) {
                    Toast.makeText(this, "未允许设备被发现，其他玩家可能搜不到房间", Toast.LENGTH_LONG).show();
                } else {
                    createBluetoothRoomAndEnterLobby();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_settings);
        boolean isPractice = getIntent().getBooleanExtra("is_practice", false);
        bluetoothActionHandler = CardGameApplication.getBluetoothActionHandler(this);

        TextView tvTitle = findViewById(R.id.tv_title);
        Typeface typeface = Typeface.createFromAsset(getAssets(), "my_custom_font.ttf");
        tvTitle.setTypeface(typeface);

        // 初始化控件
        rgRule = findViewById(R.id.rg_rule);
        cbCardTracker = findViewById(R.id.cb_card_tracker);
        cbSeeThrough = findViewById(R.id.cb_see_through);
        cbPatternHint = findViewById(R.id.cb_pattern_hint);
        rgAiStrategy = findViewById(R.id.rg_ai_strategy);
        ImageButton btnAiStrategyHelp = findViewById(R.id.btn_ai_strategy_help);
        btnBack = findViewById(R.id.btn_back_settings);
        btnStartBluetooth = findViewById(R.id.btn_start_bluetooth);

        btnBack.setOnClickListener(v -> finish());
        btnAiStrategyHelp.setOnClickListener(v -> showAiStrategyHelpDialog());

        if (isPractice) {
            btnStartBluetooth.setText("开始游戏");
            btnStartBluetooth.setOnClickListener(v -> startPracticeGame());
        } else {
            btnStartBluetooth.setText("开始蓝牙连接");
            btnStartBluetooth.setOnClickListener(v -> startBluetoothRoomFlow());
        }
    }

    private void showAiStrategyHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("AI 策略说明")
                .setMessage("普通：均衡打法，适合新手或想体验基础难度的玩家。\n\n激进：AI 倾向主动出大牌压制，适合喜欢快节奏、挑战性的玩家。\n\n保守：AI 倾向保留大牌、谨慎出牌，适合想练习破局的玩家。\n\n可在房间设置中随时切换，影响本局及后续对局。")
                .setPositiveButton("知道了", null)
                .show();
    }

    @SuppressLint("MissingPermission")
    private void startBluetoothRoomFlow() {
        if (!BluetoothPermissionHelper.isBluetoothAvailable()) {
            Toast.makeText(this, "当前设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!BluetoothPermissionHelper.hasHostBluetoothPermissions(this)) {
            ActivityCompat.requestPermissions(
                    this,
                    BluetoothPermissionHelper.getHostBluetoothPermissions(),
                    REQUEST_BLUETOOTH_PERMISSION
            );
            return;
        }

        if (!BluetoothPermissionHelper.isBluetoothEnabled()) {
            requestEnableBluetooth();
            return;
        }

        requestDiscoverableBeforeCreateRoom();
    }

    private void startPracticeGame() {
        String selectedRule = getSelectedRule();
        boolean cardTrackerEnabled = cbCardTracker.isChecked();
        boolean seeThroughEnabled = cbSeeThrough.isChecked();
        boolean patternHintEnabled = cbPatternHint.isChecked();
        String aiStrategy = getSelectedAiStrategy();

        getSharedPreferences("game_prefs", MODE_PRIVATE)
                .edit()
                .putString("game_rule", selectedRule)
                .putBoolean("prop_card_tracker", cardTrackerEnabled)
                .putBoolean("prop_see_through", seeThroughEnabled)
                .putBoolean("prop_pattern_hint", patternHintEnabled)
                .putString("ai_strategy", aiStrategy)
                .apply();

        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra("is_bluetooth_game", false);
        intent.putExtra("is_host", false);
        intent.putExtra("local_player_id", "P1");
        intent.putExtra("rule_type", selectedRule);
        startActivity(intent);
        finish();
    }

    private void requestEnableBluetooth() {
        try {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBluetoothLauncher.launch(enableIntent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开蓝牙，请到系统设置中手动开启", Toast.LENGTH_LONG).show();
        }
    }

    private void requestDiscoverableBeforeCreateRoom() {
        try {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION_SECONDS);
            discoverableLauncher.launch(discoverableIntent);
        } catch (Exception e) {
            Toast.makeText(this, "无法设置设备可被发现，请检查蓝牙权限", Toast.LENGTH_LONG).show();
        }
    }

    private void createBluetoothRoomAndEnterLobby() {
        if (bluetoothActionHandler == null) {
            Toast.makeText(this, "蓝牙控制器初始化失败", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "正在创建蓝牙房间，等待其他玩家加入...", Toast.LENGTH_SHORT).show();

        String selectedRule = getSelectedRule();
        boolean cardTrackerEnabled = cbCardTracker.isChecked();
        boolean seeThroughEnabled = cbSeeThrough.isChecked();
        boolean patternHintEnabled = cbPatternHint.isChecked();
        String aiStrategy = getSelectedAiStrategy();

        getSharedPreferences("game_prefs", MODE_PRIVATE)
                .edit()
                .putString("game_rule", selectedRule)
                .putBoolean("prop_card_tracker", cardTrackerEnabled)
                .putBoolean("prop_see_through", seeThroughEnabled)
                .putBoolean("prop_pattern_hint", patternHintEnabled)
                .putString("ai_strategy", aiStrategy)
                .apply();

        bluetoothActionHandler.createBluetoothRoom("P1");

        Intent intent = new Intent(RoomSettingsActivity.this, RoomLobbyActivity.class);
        intent.putExtra("is_host", true);
        intent.putExtra("local_player_id", "P1");
        intent.putExtra("rule_type", selectedRule);
        startActivity(intent);
        finish();
    }

    private String getSelectedAiStrategy() {
        int checkedId = rgAiStrategy.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_ai_aggressive) return "AGGRESSIVE";
        if (checkedId == R.id.rb_ai_defensive) return "DEFENSIVE";
        return "NORMAL";
    }

    private String getSelectedRule() {
        int checkedId = rgRule.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_south_rule) return "南方规则";
        if (checkedId == R.id.rb_north_rule) return "北方规则";
        return "南方规则";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (BluetoothPermissionHelper.hasHostBluetoothPermissions(this)) {
                startBluetoothRoomFlow();
            } else {
                Toast.makeText(this, "缺少蓝牙权限，无法创建房间", Toast.LENGTH_LONG).show();
            }
        }
    }
}