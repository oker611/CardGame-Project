package com.example.cardgame.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 设置自定义字体
        TextView title = findViewById(R.id.tv_title);
        Typeface typeface = Typeface.createFromAsset(getAssets(), "my_custom_font.ttf");
        title.setTypeface(typeface);

        Button btnStart = findViewById(R.id.btn_start);
        Button btnSettings = findViewById(R.id.btn_settings);
        Button btnExit = findViewById(R.id.btn_exit);

        btnStart.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this,  RoomSelectionActivity.class);
            startActivity(intent);
        });

        btnSettings.setOnClickListener(v -> {
            Toast.makeText(this, "设置功能开发中", Toast.LENGTH_SHORT).show();
        });

        btnExit.setOnClickListener(v -> finish());
    }
}