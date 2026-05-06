package com.example.cardgame.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cardgame.CardGameApplication;
import com.example.cardgame.R;
import com.example.cardgame.controller.BluetoothActionHandler;
import com.example.cardgame.dto.BluetoothDeviceViewData;
import com.example.cardgame.dto.BluetoothViewData;
import com.example.cardgame.model.DeviceInfo;

import java.util.ArrayList;
import java.util.List;

public class SearchDeviceActivity extends AppCompatActivity {

    private static final int REQUEST_BLUETOOTH_PERMISSION = 2002;

    private RecyclerView rvDeviceList;
    private DeviceAdapter deviceAdapter;
    private List<DeviceInfo> deviceList = new ArrayList<>();

    private BluetoothActionHandler bluetoothActionHandler;
    private Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_device);

        bluetoothActionHandler = CardGameApplication.getBluetoothActionHandler(this);

        tvTitle = findViewById(R.id.tv_title);
        Typeface typeface = Typeface.createFromAsset(getAssets(), "my_custom_font.ttf");
        tvTitle.setTypeface(typeface);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_help).setOnClickListener(v -> showHelpDialog());

        rvDeviceList = findViewById(R.id.rv_device_list);
        rvDeviceList.setLayoutManager(new LinearLayoutManager(this));

        deviceAdapter = new DeviceAdapter(deviceList, device -> connectToDevice(device));
        rvDeviceList.setAdapter(deviceAdapter);

        startSearch();
    }

    private void startSearch() {
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions();
            return;
        }

        Toast.makeText(this, "正在搜索蓝牙设备...", Toast.LENGTH_SHORT).show();

        bluetoothActionHandler.searchBluetoothDevices();

        handler.postDelayed(this::refreshDeviceListFromController, 1200);
    }

    private void refreshDeviceListFromController() {
        BluetoothViewData viewData = bluetoothActionHandler.getBluetoothViewData();

        if (viewData == null) {
            Toast.makeText(this, "蓝牙状态未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        deviceList.clear();

        List<BluetoothDeviceViewData> bluetoothDevices = viewData.getDevices();
        if (bluetoothDevices != null) {
            for (BluetoothDeviceViewData item : bluetoothDevices) {
                deviceList.add(new DeviceInfo(
                        item.getDeviceName(),
                        item.getDeviceAddress(),
                        0,
                        item.isBonded(),
                        1
                ));
            }
        }

        deviceAdapter.notifyDataSetChanged();

        if (deviceList.isEmpty()) {
            Toast.makeText(this, "未发现已配对设备，请先在系统蓝牙中配对", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "搜索完成，共发现 " + deviceList.size() + " 个设备", Toast.LENGTH_SHORT).show();
        }
    }

    private void connectToDevice(DeviceInfo device) {
        if (device == null || device.getDeviceAddress() == null) {
            Toast.makeText(this, "设备地址无效", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions();
            return;
        }

        Toast.makeText(this, "正在连接：" + device.getDeviceName(), Toast.LENGTH_SHORT).show();

        bluetoothActionHandler.connectToDevice("P2", device.getDeviceAddress());

        handler.postDelayed(() -> {
            BluetoothViewData viewData = bluetoothActionHandler.getBluetoothViewData();

            if (viewData != null && viewData.isConnected()) {
                Intent intent = new Intent(SearchDeviceActivity.this, RoomLobbyActivity.class);
                intent.putExtra("is_host", false);
                intent.putExtra("local_player_id", "P2");
                startActivity(intent);
                finish();
                return;
            }

            String errorMessage = viewData != null ? viewData.getErrorMessage() : null;
            if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                Toast.makeText(this, "连接失败：" + errorMessage, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "连接中，请稍候...", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(SearchDeviceActivity.this, RoomLobbyActivity.class);
                intent.putExtra("is_host", false);
                intent.putExtra("local_player_id", "P2");
                startActivity(intent);
                finish();
            }
        }, 1500);
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
                startSearch();
            } else {
                Toast.makeText(this, "缺少蓝牙权限，无法搜索设备", Toast.LENGTH_SHORT).show();
            }
        }
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