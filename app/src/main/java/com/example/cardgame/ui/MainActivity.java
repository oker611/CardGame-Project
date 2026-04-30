package com.example.cardgame.ui;

import com.example.cardgame.R;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
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
        Button btnRules = findViewById(R.id.btn_rules);
        Button btnExit = findViewById(R.id.btn_exit);

        btnStart.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RoomSelectionActivity.class);
            startActivity(intent);
        });

        // 设置按钮：弹出对话框输入玩家昵称
        btnSettings.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("设置玩家昵称");

            SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
            String currentName = prefs.getString("player_name", "玩家");

            final EditText input = new EditText(MainActivity.this);
            input.setText(currentName);
            input.setSelectAllOnFocus(true);
            builder.setView(input);

            builder.setPositiveButton("保存", (dialog, which) -> {
                String newName = input.getText().toString().trim();
                if (newName.isEmpty()) {
                    Toast.makeText(MainActivity.this, "昵称不能为空", Toast.LENGTH_SHORT).show();
                } else {
                    prefs.edit().putString("player_name", newName).apply();
                    Toast.makeText(MainActivity.this, "昵称已保存: " + newName, Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("取消", null);
            builder.show();
        });

        // 查看规则按钮
        btnRules.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RulesActivity.class);
            startActivity(intent);
        });

        btnExit.setOnClickListener(v -> finish());
    }
}