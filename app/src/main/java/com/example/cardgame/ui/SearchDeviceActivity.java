package com.example.cardgame.ui;

import android.bluetooth.BluetoothAdapter;
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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cardgame.CardGameApplication;
import com.example.cardgame.R;
import com.example.cardgame.controller.BluetoothActionHandler;
import com.example.cardgame.dto.BluetoothDeviceViewData;
import com.example.cardgame.dto.BluetoothViewData;
import com.example.cardgame.model.DeviceInfo;
import com.example.cardgame.util.BluetoothPermissionHelper;

import java.util.ArrayList;
import java.util.List;

public class SearchDeviceActivity extends AppCompatActivity {

    private static final int REQUEST_BLUETOOTH_PERMISSION = 2101;
    private static final int REQUEST_ENABLE_BLUETOOTH = 2102;

    private RecyclerView rvDeviceList;
    private DeviceAdapter deviceAdapter;
    private final List<DeviceInfo> deviceList = new ArrayList<>();

    private BluetoothActionHandler bluetoothActionHandler;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean connecting = false;
    private boolean searching = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_device);

        bluetoothActionHandler = CardGameApplication.getBluetoothActionHandler(this);

        TextView tvTitle = findViewById(R.id.tv_title);
        Typeface typeface = Typeface.createFromAsset(getAssets(), "my_custom_font.ttf");
        tvTitle.setTypeface(typeface);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_help).setOnClickListener(v -> showHelpDialog());

        rvDeviceList = findViewById(R.id.rv_device_list);
        rvDeviceList.setLayoutManager(new LinearLayoutManager(this));

        deviceAdapter = new DeviceAdapter(deviceList, this::connectToDevice);
        rvDeviceList.setAdapter(deviceAdapter);

        startSearchFlow();
    }

    private void startSearchFlow() {
        if (!BluetoothPermissionHelper.isBluetoothAvailable()) {
            Toast.makeText(this, "当前设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!BluetoothPermissionHelper.hasClientBluetoothPermissions(this)) {
            ActivityCompat.requestPermissions(
                    this,
                    BluetoothPermissionHelper.getClientBluetoothPermissions(),
                    REQUEST_BLUETOOTH_PERMISSION
            );
            return;
        }

        if (!BluetoothPermissionHelper.isBluetoothEnabled()) {
            requestEnableBluetooth();
            return;
        }

        searchBluetoothDevices();
    }

    private void requestEnableBluetooth() {
        try {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开蓝牙，请到系统设置中手动开启", Toast.LENGTH_LONG).show();
        }
    }

    private void searchBluetoothDevices() {
        if (bluetoothActionHandler == null) {
            Toast.makeText(this, "蓝牙控制器初始化失败", Toast.LENGTH_SHORT).show();
            return;
        }

        searching = true;
        deviceList.clear();
        deviceAdapter.notifyDataSetChanged();

        Toast.makeText(this, "正在搜索可加入的手机或平板...", Toast.LENGTH_SHORT).show();

        bluetoothActionHandler.searchBluetoothDevices();

        handler.postDelayed(this::refreshDeviceListFromController, 1500);
        handler.postDelayed(this::refreshDeviceListFromController, 5000);
        handler.postDelayed(this::refreshDeviceListFromController, 9000);
        handler.postDelayed(this::finishSearchAndRefresh, 13500);
    }

    private void finishSearchAndRefresh() {
        searching = false;
        refreshDeviceListFromController();

        if (deviceList.isEmpty()) {
            Toast.makeText(
                    this,
                    "未发现可加入的手机/平板。请确认房主已创建房间，并允许设备被发现。",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void refreshDeviceListFromController() {
        if (bluetoothActionHandler == null) {
            return;
        }

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

        if (!searching && !deviceList.isEmpty()) {
            Toast.makeText(this, "搜索完成，共发现 " + deviceList.size() + " 个可加入设备", Toast.LENGTH_SHORT).show();
        }
    }

    private void connectToDevice(DeviceInfo device) {
        if (connecting) {
            Toast.makeText(this, "正在连接，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }

        if (device == null || device.getDeviceAddress() == null) {
            Toast.makeText(this, "设备地址无效", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!BluetoothPermissionHelper.hasClientBluetoothPermissions(this)) {
            ActivityCompat.requestPermissions(
                    this,
                    BluetoothPermissionHelper.getClientBluetoothPermissions(),
                    REQUEST_BLUETOOTH_PERMISSION
            );
            return;
        }

        if (!BluetoothPermissionHelper.isBluetoothEnabled()) {
            requestEnableBluetooth();
            return;
        }

        if (bluetoothActionHandler == null) {
            Toast.makeText(this, "蓝牙控制器初始化失败", Toast.LENGTH_SHORT).show();
            return;
        }

        connecting = true;

        Toast.makeText(this, "正在连接：" + device.getDeviceName(), Toast.LENGTH_SHORT).show();

        bluetoothActionHandler.connectToDevice("P2", device.getDeviceAddress());

        handler.postDelayed(this::checkConnectionResultAndEnterLobby, 2000);
        handler.postDelayed(this::checkConnectionResultAndEnterLobby, 4500);
    }

    private void checkConnectionResultAndEnterLobby() {
        if (!connecting || bluetoothActionHandler == null) {
            return;
        }

        BluetoothViewData viewData = bluetoothActionHandler.getBluetoothViewData();

        if (viewData != null && viewData.isConnected()) {
            connecting = false;

            Toast.makeText(this, "蓝牙连接成功", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(SearchDeviceActivity.this, RoomLobbyActivity.class);
            intent.putExtra("is_host", false);
            intent.putExtra("local_player_id", "P2");
            startActivity(intent);
            finish();
            return;
        }

        String errorMessage = viewData != null ? viewData.getErrorMessage() : null;
        if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            connecting = false;
            Toast.makeText(this, "连接失败：" + errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (BluetoothPermissionHelper.hasClientBluetoothPermissions(this)) {
                startSearchFlow();
            } else {
                Toast.makeText(this, "缺少蓝牙权限，无法搜索或连接设备", Toast.LENGTH_LONG).show();
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
                startSearchFlow();
            } else {
                Toast.makeText(this, "蓝牙未开启，无法搜索设备", Toast.LENGTH_LONG).show();
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