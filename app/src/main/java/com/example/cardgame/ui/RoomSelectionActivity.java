package com.example.cardgame.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

public class RoomSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_selection);

        Button btnPractice = findViewById(R.id.btn_practice);
        Button btnCreateRoom = findViewById(R.id.btn_create_room);
        Button btnJoinRoom = findViewById(R.id.btn_join_room);
        ImageButton btnBackHome = findViewById(R.id.btn_back_home);

        // 设置自定义字体（与主菜单相同）
        Typeface typeface = Typeface.createFromAsset(getAssets(), "my_custom_font.ttf");
        btnPractice.setTypeface(typeface);
        btnCreateRoom.setTypeface(typeface);
        btnJoinRoom.setTypeface(typeface);

        // 三个按钮都跳转到游戏界面
        btnPractice.setOnClickListener(v -> startGame());
        btnCreateRoom.setOnClickListener(v -> startGame());
        btnJoinRoom.setOnClickListener(v -> startGame());

        // 返回主菜单
        btnBackHome.setOnClickListener(v -> {
            Intent intent = new Intent(RoomSelectionActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void startGame() {
        Intent intent = new Intent(RoomSelectionActivity.this, GameActivity.class);
        startActivity(intent);
    }
}