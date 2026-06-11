package com.example.cardgame.ui;

import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
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

        bluetoothActionHandler = ((CardGameApplication) getApplication()).getBluetoothActionHandler(this);

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
            Toast.makeText(this, R.string.bt_not_supported, Toast.LENGTH_SHORT).show();
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

        // 第一步：立即显示已配对设备（毫秒级）
        if (bluetoothActionHandler != null) {
            bluetoothActionHandler.loadBondedDevices();
            refreshDeviceListFromController();
        }

        // 第二步：启动后台搜索（最多等待6秒）
        searchBluetoothDevices();
    }

    private void requestEnableBluetooth() {
        try {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.bt_cannot_enable, Toast.LENGTH_LONG).show();
        }
    }

    private void searchBluetoothDevices() {
        if (bluetoothActionHandler == null) {
            Toast.makeText(this, R.string.bt_init_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        searching = true;
        deviceList.clear();
        deviceAdapter.notifyDataSetChanged();

        Toast.makeText(this, R.string.bt_searching, Toast.LENGTH_SHORT).show();

        bluetoothActionHandler.searchBluetoothDevices();

        handler.postDelayed(this::refreshDeviceListFromController, 1000);
        handler.postDelayed(this::refreshDeviceListFromController, 3000);
        handler.postDelayed(this::finishSearchAndRefresh, 6500);
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
            Toast.makeText(this, R.string.bt_not_initialized, Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, getString(R.string.bt_search_done, deviceList.size()), Toast.LENGTH_SHORT).show();
        }
    }

    private void connectToDevice(DeviceInfo device) {
        if (connecting) {
            Toast.makeText(this, R.string.bt_connecting, Toast.LENGTH_SHORT).show();
            return;
        }

        if (device == null || device.getDeviceAddress() == null) {
            Toast.makeText(this, R.string.bt_invalid_address, Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, R.string.bt_init_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        connecting = true;

        Toast.makeText(this, getString(R.string.bt_connecting_to, device.getDeviceName()), Toast.LENGTH_SHORT).show();

        bluetoothActionHandler.connectToDevice("CLIENT", device.getDeviceAddress());

        handler.postDelayed(this::checkConnectionResultAndEnterLobby, 2000);
        handler.postDelayed(this::checkConnectionResultAndEnterLobby, 5000);
        handler.postDelayed(this::checkConnectionResultAndEnterLobby, 8000);
    }

    private void checkConnectionResultAndEnterLobby() {
        if (!connecting || bluetoothActionHandler == null) {
            return;
        }

        BluetoothViewData viewData = bluetoothActionHandler.getBluetoothViewData();

        if (viewData != null && viewData.isConnected()) {
            connecting = false;

            Toast.makeText(this, R.string.bt_connected_success, Toast.LENGTH_SHORT).show();

            String assignedId = viewData.getAssignedPlayerId();
            String localId = (assignedId != null && !assignedId.isEmpty()) ? assignedId : "CLIENT";

            Intent intent = new Intent(SearchDeviceActivity.this, RoomLobbyActivity.class);
            intent.putExtra("is_host", false);
            intent.putExtra("local_player_id", localId);
            startActivity(intent);
            finish();
            return;
        }

        String errorMessage = viewData != null ? viewData.getErrorMessage() : null;
        if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            connecting = false;
            Toast.makeText(this, getString(R.string.bt_connect_failed, errorMessage), Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, R.string.bt_no_permission, Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, R.string.bt_not_enabled, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
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
