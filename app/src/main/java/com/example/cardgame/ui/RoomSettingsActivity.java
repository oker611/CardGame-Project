package com.example.cardgame.ui;

import android.Manifest;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.cardgame.CardGameApplication;
import com.example.cardgame.R;
import com.example.cardgame.controller.BluetoothActionHandler;

public class RoomSettingsActivity extends AppCompatActivity {

    private static final int REQUEST_BLUETOOTH_PERMISSION = 2001;

    private RadioGroup rgRounds, rgRule, rgPlayStyle;
    private CheckBox cbCardTracker, cbAntiCheat, cbTimeoutDismiss, cbSwapCards;
    private Button btnBack, btnStartBluetooth;

    private BluetoothActionHandler bluetoothActionHandler;

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

        btnStartBluetooth.setOnClickListener(v -> startBluetoothRoom());
    }

    private void startBluetoothRoom() {
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions();
            return;
        }

        Toast.makeText(this, "正在创建蓝牙房间...", Toast.LENGTH_SHORT).show();

        bluetoothActionHandler.createBluetoothRoom("P1");

        Intent intent = new Intent(RoomSettingsActivity.this, RoomLobbyActivity.class);
        intent.putExtra("is_host", true);
        intent.putExtra("local_player_id", "P1");
        startActivity(intent);
        finish();
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
        }

        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                    },
                    REQUEST_BLUETOOTH_PERMISSION
            );
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_BLUETOOTH_PERMISSION
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (hasBluetoothPermissions()) {
                startBluetoothRoom();
            } else {
                Toast.makeText(this, "缺少蓝牙权限，无法创建房间", Toast.LENGTH_SHORT).show();
            }
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