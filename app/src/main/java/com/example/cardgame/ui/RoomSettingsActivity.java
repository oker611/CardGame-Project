package com.example.cardgame.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.cardgame.R;

public class RoomSettingsActivity extends AppCompatActivity {

    private RadioGroup rgRounds, rgRule, rgPlayStyle;
    private CheckBox cbCardTracker, cbAntiCheat, cbTimeoutDismiss, cbSwapCards;
    private Button btnBack, btnStartBluetooth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_settings);

        // 标题字体
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

        btnStartBluetooth.setOnClickListener(v -> {
            // 跳转到房间大厅（房主模式）
            Intent intent = new Intent(RoomSettingsActivity.this, RoomLobbyActivity.class);
            intent.putExtra("is_host", true);
            startActivity(intent);
            finish();
        });
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