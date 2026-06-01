package com.example.cardgame.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cardgame.R;

public class RulesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rules);

        // 设置标题自定义字体
        TextView tvTitle = findViewById(R.id.tv_rules_title);
        Typeface typeface = Typeface.createFromAsset(getAssets(), "my_custom_font.ttf");
        tvTitle.setTypeface(typeface);

        // 设置规则内容
        TextView tvContent = findViewById(R.id.tv_rules_content);
        String htmlRules = getString(R.string.rules_text);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            tvContent.setText(android.text.Html.fromHtml(htmlRules, android.text.Html.FROM_HTML_MODE_LEGACY));
        } else {
            tvContent.setText(android.text.Html.fromHtml(htmlRules));
        }

        // 返回按钮
        ImageButton btnBack = findViewById(R.id.btn_back_home);
        btnBack.setOnClickListener(v -> finish());
    }
}