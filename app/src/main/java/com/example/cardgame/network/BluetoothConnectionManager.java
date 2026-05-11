package com.example.cardgame.network;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BluetoothConnectionManager {

    private static final String SERVICE_NAME = "CardGameBluetoothService";
    public static final UUID SERVICE_UUID =
            UUID.fromString("a5c93a6e-6c0f-4a21-8e6d-9dd3b8a3d7c1");

    private static final long DISCOVERY_TIMEOUT_SECONDS = 13L;

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;

    private BluetoothSocket bluetoothSocket;
    private BluetoothServerSocket serverSocket;

    private InputStream inputStream;
    private OutputStream outputStream;

    private String connectedDeviceName;
    private String connectedDeviceAddress;
    private boolean connected;

    public BluetoothConnectionManager(Context context) {
        this.context = context.getApplicationContext();
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public boolean isBluetoothAvailable() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    /**
     * 搜索可加入游戏的候选设备。
     *
     * 策略：
     * 1. 查询已配对设备，但只保留手机 / 平板候选。
     * 2. 执行真实 discovery，发现未配对但可被发现的手机 / 平板。
     * 3. 过滤耳机、音箱、车机、电视等明显不能运行本游戏客户端的设备。
     *
     * 注意：
     * 这一步只能筛选“可能可加入”的设备；是否真的运行了 CardGame Host，
     * 需要点击连接并成功建立 RFCOMM Socket 后才能确认。
     */
    @SuppressLint("MissingPermission")
    public List<BluetoothDeviceInfo> discoverJoinableMobileDevices() {
        Map<String, BluetoothDeviceInfo> resultMap = new LinkedHashMap<>();

        if (bluetoothAdapter == null) {
            return new ArrayList<>();
        }

        addBondedMobileDevices(resultMap);

        if (!bluetoothAdapter.isEnabled()) {
            return new ArrayList<>(resultMap.values());
        }

        final CountDownLatch discoveryFinishedLatch = new CountDownLatch(1);

        BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                String action = intent.getAction();

                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    BluetoothClass bluetoothClass = intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS);
                    short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                    BluetoothDeviceInfo info = toJoinableDeviceInfo(device, bluetoothClass, rssi);
                    if (info != null && info.getDeviceAddress() != null) {
                        resultMap.put(info.getDeviceAddress(), info);

                        Log.d("CardGame", "[DEBUG] [蓝牙] 发现候选设备 | name="
                                + info.getDeviceName()
                                + ", address="
                                + info.getDeviceAddress());
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    discoveryFinishedLatch.countDown();
                    Log.i("CardGame", "[INFO] [蓝牙] 设备搜索完成");
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        try {
            ContextCompat.registerReceiver(
                    context,
                    discoveryReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
            );

            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }

            boolean started = bluetoothAdapter.startDiscovery();
            Log.i("CardGame", "[INFO] [蓝牙] startDiscovery result=" + started);

            if (started) {
                discoveryFinishedLatch.await(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            Log.e("CardGame", "[ERROR] [蓝牙] 搜索设备失败", e);
        } finally {
            try {
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
            } catch (Exception ignored) {
            }

            try {
                context.unregisterReceiver(discoveryReceiver);
            } catch (Exception ignored) {
            }
        }

        return new ArrayList<>(resultMap.values());
    }

    @SuppressLint("MissingPermission")
    private void addBondedMobileDevices(Map<String, BluetoothDeviceInfo> resultMap) {
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

        if (bondedDevices == null) {
            return;
        }

        for (BluetoothDevice device : bondedDevices) {
            BluetoothDeviceInfo info = toJoinableDeviceInfo(
                    device,
                    device.getBluetoothClass(),
                    Short.MIN_VALUE
            );

            if (info != null && info.getDeviceAddress() != null) {
                resultMap.put(info.getDeviceAddress(), info);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private BluetoothDeviceInfo toJoinableDeviceInfo(BluetoothDevice device,
                                                     BluetoothClass bluetoothClass,
                                                     short rssi) {
        if (device == null) {
            return null;
        }

        String address = device.getAddress();
        String name = safeDeviceName(device);
        boolean bonded = device.getBondState() == BluetoothDevice.BOND_BONDED;

        if (!isLikelyPhoneOrTablet(name, bluetoothClass)) {
            Log.d("CardGame", "[DEBUG] [蓝牙] 过滤非手机/平板设备 | name=" + name);
            return null;
        }

        int signalStrength = mapSignalStrength(rssi);

        return new BluetoothDeviceInfo(
                name,
                address,
                bonded,
                0,
                signalStrength,
                true
        );
    }

    private boolean isLikelyPhoneOrTablet(String name, BluetoothClass bluetoothClass) {
        if (bluetoothClass != null) {
            int majorDeviceClass = bluetoothClass.getMajorDeviceClass();

            if (majorDeviceClass == BluetoothClass.Device.Major.PHONE
                    || majorDeviceClass == BluetoothClass.Device.Major.COMPUTER) {
                return true;
            }

            if (majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
                    || majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL
                    || majorDeviceClass == BluetoothClass.Device.Major.TOY
                    || majorDeviceClass == BluetoothClass.Device.Major.HEALTH
                    || majorDeviceClass == BluetoothClass.Device.Major.IMAGING
                    || majorDeviceClass == BluetoothClass.Device.Major.WEARABLE) {
                return false;
            }
        }

        String lowerName = name == null ? "" : name.toLowerCase();

        if (lowerName.trim().isEmpty() || "unknown device".equals(lowerName)) {
            return false;
        }

        if (lowerName.contains("headset")
                || lowerName.contains("buds")
                || lowerName.contains("earbuds")
                || lowerName.contains("earphone")
                || lowerName.contains("speaker")
                || lowerName.contains("audio")
                || lowerName.contains("watch")
                || lowerName.contains("band")
                || lowerName.contains("car")
                || lowerName.contains("tv")
                || lowerName.contains("mouse")
                || lowerName.contains("keyboard")
                || lowerName.contains("printer")) {
            return false;
        }

        return lowerName.contains("android")
                || lowerName.contains("phone")
                || lowerName.contains("mobile")
                || lowerName.contains("tablet")
                || lowerName.contains("pad")
                || lowerName.contains("pixel")
                || lowerName.contains("xiaomi")
                || lowerName.contains("redmi")
                || lowerName.contains("huawei")
                || lowerName.contains("honor")
                || lowerName.contains("oppo")
                || lowerName.contains("vivo")
                || lowerName.contains("oneplus")
                || lowerName.contains("samsung")
                || lowerName.contains("galaxy")
                || lowerName.contains("sony")
                || lowerName.contains("motorola")
                || lowerName.contains("lenovo")
                || lowerName.contains("meizu")
                || lowerName.contains("realme");
    }

    private int mapSignalStrength(short rssi) {
        if (rssi == Short.MIN_VALUE) {
            return 1;
        }

        if (rssi >= -60) {
            return 2;
        }

        if (rssi >= -80) {
            return 1;
        }

        return 0;
    }

    @SuppressLint("MissingPermission")
    public List<BluetoothDeviceInfo> getBondedDevices() {
        List<BluetoothDeviceInfo> result = new ArrayList<>();

        if (bluetoothAdapter == null) {
            return result;
        }

        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

        if (bondedDevices == null) {
            return result;
        }

        for (BluetoothDevice device : bondedDevices) {
            result.add(new BluetoothDeviceInfo(
                    safeDeviceName(device),
                    device.getAddress(),
                    true
            ));
        }

        return result;
    }

    @SuppressLint("MissingPermission")
    public boolean startDiscovery() {
        if (bluetoothAdapter == null) {
            return false;
        }

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        return bluetoothAdapter.startDiscovery();
    }

    @SuppressLint("MissingPermission")
    public void stopDiscovery() {
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    /**
     * 仅创建蓝牙服务端 Socket 并开始监听（非阻塞）。房间此时已可被发现。
     * 调用此方法后应调用 {@link #waitForClient()} 等待客户端连接。
     */
    @SuppressLint("MissingPermission")
    public void startServer() throws IOException {
        if (bluetoothAdapter == null) {
            throw new IOException("Bluetooth is not available");
        }

        serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                SERVICE_NAME,
                SERVICE_UUID
        );

        Log.i("CardGame", "[INFO] [蓝牙] 服务端 Socket 已创建，等待客户端连接...");
    }

    /**
     * 阻塞等待客户端连接，连上后建立输入输出流。
     * 调用前必须先调用 {@link #startServer()}。
     */
    @SuppressLint("MissingPermission")
    public void waitForClient() throws IOException {
        if (serverSocket == null) {
            throw new IOException("Server socket not started. Call startServer() first.");
        }

        bluetoothSocket = serverSocket.accept();

        Log.i("CardGame", "[INFO] [蓝牙] 客户端已连接");
        setupStreams();
    }

    @SuppressLint("MissingPermission")
    public void acceptConnectionAsServer() throws IOException {
        startServer();
        waitForClient();
    }

    @SuppressLint("MissingPermission")
    public void connectToDevice(String deviceAddress) throws IOException {
        if (bluetoothAdapter == null) {
            throw new IOException("Bluetooth is not available");
        }

        if (deviceAddress == null || deviceAddress.trim().isEmpty()) {
            throw new IOException("Bluetooth device address is empty");
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);

        stopDiscovery();

        bluetoothSocket = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
        bluetoothSocket.connect();

        setupStreams();
    }

    @SuppressLint("MissingPermission")
    private void setupStreams() throws IOException {
        if (bluetoothSocket == null) {
            throw new IOException("Bluetooth socket is null");
        }

        inputStream = bluetoothSocket.getInputStream();
        outputStream = bluetoothSocket.getOutputStream();

        BluetoothDevice remoteDevice = bluetoothSocket.getRemoteDevice();
        connectedDeviceName = safeDeviceName(remoteDevice);
        connectedDeviceAddress = remoteDevice.getAddress();
        connected = true;
    }

    @SuppressLint("MissingPermission")
    private String safeDeviceName(BluetoothDevice device) {
        if (device == null) {
            return "Unknown Device";
        }

        String name = device.getName();

        if (name == null || name.trim().isEmpty()) {
            return "Unknown Device";
        }

        return name;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public String getConnectedDeviceName() {
        return connectedDeviceName;
    }

    public String getConnectedDeviceAddress() {
        return connectedDeviceAddress;
    }

    public boolean isConnected() {
        return connected;
    }

    public void close() {
        connected = false;

        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException ignored) {
        }

        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException ignored) {
        }

        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException ignored) {
        }

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        inputStream = null;
        outputStream = null;
        bluetoothSocket = null;
        serverSocket = null;
        connectedDeviceName = null;
        connectedDeviceAddress = null;
    }
}