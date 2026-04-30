package com.example.cardgame.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.cardgame.R;
import com.example.cardgame.model.DeviceInfo;
import java.util.ArrayList;
import java.util.List;

public class SearchDeviceActivity extends AppCompatActivity {

    private RecyclerView rvDeviceList;
    private DeviceAdapter deviceAdapter;
    private List<DeviceInfo> deviceList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_device);

        // 标题字体
        TextView tvTitle = findViewById(R.id.tv_title);
        Typeface typeface = Typeface.createFromAsset(getAssets(), "my_custom_font.ttf");
        tvTitle.setTypeface(typeface);

        // 返回按钮
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 帮助按钮
        findViewById(R.id.btn_help).setOnClickListener(v -> showHelpDialog());

        // 设备列表
        rvDeviceList = findViewById(R.id.rv_device_list);
        rvDeviceList.setLayoutManager(new LinearLayoutManager(this));
        deviceAdapter = new DeviceAdapter(deviceList, device -> {
            // 点击连接后跳转到房间大厅（成员模式）
            Intent intent = new Intent(SearchDeviceActivity.this, RoomLobbyActivity.class);
            intent.putExtra("is_host", false);
            startActivity(intent);
            finish();
        });
        rvDeviceList.setAdapter(deviceAdapter);
    }

    private void showHelpDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_help, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_help_title);
        TextView tvMessage = dialogView.findViewById(R.id.tv_help_message);

        Typeface typeface = Typeface.createFromAsset(getAssets(), "my_custom_font.ttf");
        tvTitle.setTypeface(typeface);
        tvMessage.setText(getString(R.string.bluetooth_help_message));

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("知道了", null)
                .show();
    }
}