package com.example.cardgame.ui;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.cardgame.CardGameApplication;
import com.example.cardgame.R;
import com.example.cardgame.controller.BluetoothActionHandler;
import com.example.cardgame.util.BluetoothPermissionHelper;

public class RoomSettingsActivity extends AppCompatActivity {

    private static final int REQUEST_BLUETOOTH_PERMISSION = 2001;
    private static final int REQUEST_ENABLE_BLUETOOTH = 2002;
    private static final int REQUEST_DISCOVERABLE = 2003;
    private static final int DISCOVERABLE_DURATION_SECONDS = 300;

    private RadioGroup rgRounds, rgRule, rgPlayStyle;
    private CheckBox cbCardTracker, cbAntiCheat, cbTimeoutDismiss, cbSwapCards;
    private Button btnBack, btnStartBluetooth;

    private BluetoothActionHandler bluetoothActionHandler;
    private boolean waitingDiscoverableResult = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_settings);

        bluetoothActionHandler = CardGameApplication.getBluetoothActionHandler(this);

        TextView tvTitle = findViewById(R.id.tv_title);
        Typeface typeface = Typeface.createFromAsset(getAssets(), "my_custom_font.ttf");
        tvTitle.setTypeface(typeface);

        rgRounds = findViewById(R.id.rg_rounds);
        rgRule = findViewById(R.id.rg_rule);
        rgPlayStyle = findViewById(R.id.rg_play_style);
        cbCardTracker = findViewById(R.id.cb_card_tracker);
        cbAntiCheat = findViewById(R.id.cb_anti_cheat);
        cbTimeoutDismiss = findViewById(R.id.cb_timeout_dismiss);
        cbSwapCards = findViewById(R.id.cb_swap_cards);
        btnBack = findViewById(R.id.btn_back_settings);
        btnStartBluetooth = findViewById(R.id.btn_start_bluetooth);

        btnBack.setOnClickListener(v -> finish());

        btnStartBluetooth.setOnClickListener(v -> startBluetoothRoomFlow());
    }

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

    private void requestEnableBluetooth() {
        try {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开蓝牙，请到系统设置中手动开启", Toast.LENGTH_LONG).show();
        }
    }

    private void requestDiscoverableBeforeCreateRoom() {
        try {
            waitingDiscoverableResult = true;

            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(
                    BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                    DISCOVERABLE_DURATION_SECONDS
            );
            startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE);
        } catch (Exception e) {
            waitingDiscoverableResult = false;
            Toast.makeText(this, "无法设置设备可被发现，请检查蓝牙权限", Toast.LENGTH_LONG).show();
        }
    }

    private void createBluetoothRoomAndEnterLobby() {
        if (bluetoothActionHandler == null) {
            Toast.makeText(this, "蓝牙控制器初始化失败", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "正在创建蓝牙房间，等待其他玩家加入...", Toast.LENGTH_SHORT).show();

        bluetoothActionHandler.createBluetoothRoom("P1");

        Intent intent = new Intent(RoomSettingsActivity.this, RoomLobbyActivity.class);
        intent.putExtra("is_host", true);
        intent.putExtra("local_player_id", "P1");
        startActivity(intent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (BluetoothPermissionHelper.hasHostBluetoothPermissions(this)) {
                startBluetoothRoomFlow();
            } else {
                Toast.makeText(this, "缺少蓝牙权限，无法创建房间", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode,
                                    @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (BluetoothPermissionHelper.isBluetoothEnabled()) {
                startBluetoothRoomFlow();
            } else {
                Toast.makeText(this, "蓝牙未开启，无法创建房间", Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (requestCode == REQUEST_DISCOVERABLE) {
            waitingDiscoverableResult = false;

            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "未允许设备被发现，其他玩家可能搜不到房间", Toast.LENGTH_LONG).show();
                return;
            }

            createBluetoothRoomAndEnterLobby();
        }
    }

    private int getSelectedRounds() {
        int checkedId = rgRounds.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_8_rounds) return 8;
        if (checkedId == R.id.rb_32_rounds) return 32;
        return 16;
    }

    private String getSelectedRule() {
        int checkedId = rgRule.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_south_rule) return "南方规则";
        if (checkedId == R.id.rb_north_rule) return "北方规则";
        return "南方规则";
    }

    private String getSelectedPlayStyle() {
        int checkedId = rgPlayStyle.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_soft) return "软锄";
        if (checkedId == R.id.rb_hard) return "硬锄";
        return "软锄";
    }
}