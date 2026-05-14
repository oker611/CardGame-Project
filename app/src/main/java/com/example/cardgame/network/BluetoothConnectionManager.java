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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BluetoothConnectionManager {

    private static final String SERVICE_NAME = "CardGameBluetoothService";
    public static final UUID SERVICE_UUID =
            UUID.fromString("a5c93a6e-6c0f-4a21-8e6d-9dd3b8a3d7c1");

    private static final long DISCOVERY_TIMEOUT_SECONDS = 13L;

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;

    private BluetoothServerSocket serverSocket;

    /** 正在阻塞 accept() 的线程，用于 close() 时中断 */
    private volatile Thread acceptThread;

    /** 服务端是否仍在接受连接 */
    private volatile boolean accepting = false;

    // ——— 多路连接支持 ———
    /** deviceAddress → ClientConnection */
    private final Map<String, ClientConnection> clientConnections = new ConcurrentHashMap<>();

    // ——— 单连接兼容（旧代码可继续使用，指向最近连接的设备） ———
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private String connectedDeviceName;
    private String connectedDeviceAddress;
    private boolean connected;

    public BluetoothConnectionManager(Context context) {
        this.context = context.getApplicationContext();
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    // ========================================================================
    //  设备发现（无变化）
    // ========================================================================

    public boolean isBluetoothAvailable() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

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

    // ========================================================================
    //  Server 端：多客户端连接
    // ========================================================================

    @SuppressLint("MissingPermission")
    public void startServer() throws IOException {
        if (bluetoothAdapter == null) {
            throw new IOException("Bluetooth is not available");
        }

        accepting = true;
        serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                SERVICE_NAME,
                SERVICE_UUID
        );

        Log.i("CardGame", "[INFO] [蓝牙] 服务端 Socket 已创建，等待客户端连接...");
    }

    /**
     * 阻塞等待下一个客户端连接，返回该连接的设备地址。
     * 调用前必须先调用 {@link #startServer()}。
     * 可重复调用以接受多个客户端（每次 accept 后 serverSocket 保持打开）。
     */
    @SuppressLint("MissingPermission")
    public String waitForNextClient() throws IOException {
        if (serverSocket == null) {
            throw new IOException("Server socket not started. Call startServer() first.");
        }

        acceptThread = Thread.currentThread();
        BluetoothSocket socket;
        try {
            socket = serverSocket.accept();
        } catch (IOException e) {
            // 被 close() 中断时正常退出
            if (!accepting) {
                throw new IOException("Server stopped while waiting for client", e);
            }
            throw e;
        } finally {
            acceptThread = null;
        }

        BluetoothDevice remoteDevice = socket.getRemoteDevice();
        String deviceAddress = remoteDevice.getAddress();
        String deviceName = safeDeviceName(remoteDevice);

        ClientConnection conn = new ClientConnection(
                deviceAddress,
                deviceName,
                socket,
                socket.getInputStream(),
                socket.getOutputStream()
        );

        clientConnections.put(deviceAddress, conn);

        // 同步单连接兼容字段
        syncLegacyFields(deviceAddress);

        Log.i("CardGame", "[INFO] [蓝牙] 客户端已连接 | name=" + deviceName
                + ", address=" + deviceAddress
                + ", totalConnections=" + clientConnections.size());

        return deviceAddress;
    }

    /**
     * 阻塞等待多个客户端连接（最多 maxClients 个）。
     * 返回所有已连接设备的地址列表。
     */
    @SuppressLint("MissingPermission")
    public List<String> waitForAllClients(int maxClients) throws IOException {
        List<String> addresses = new ArrayList<>();

        for (int i = 0; i < maxClients; i++) {
            try {
                String address = waitForNextClient();
                addresses.add(address);
            } catch (IOException e) {
                Log.e("CardGame", "[ERROR] [蓝牙] 等待客户端" + (i + 1) + "连接失败", e);
                // 清理已建立的连接，避免半初始化状态
                for (String addr : addresses) {
                    closeConnection(addr);
                }
                throw e;
            }
        }

        return addresses;
    }

    // ========================================================================
    //  兼容旧的单连接 API（用于 CLIENT 端 和 HOST 端旧代码）
    // ========================================================================

    /**
     * 仅创建蓝牙服务端 Socket 并开始监听（非阻塞）。
     * 兼容旧 API，等同于 {@link #startServer()}。
     */
    @SuppressLint("MissingPermission")
    @Deprecated
    public void startServerLegacy() throws IOException {
        startServer();
    }

    /**
     * 阻塞等待客户端连接，连上后建立输入输出流。
     * 兼容旧 API。
     */
    @SuppressLint("MissingPermission")
    @Deprecated
    public void waitForClient() throws IOException {
        waitForNextClient();
    }

    @SuppressLint("MissingPermission")
    @Deprecated
    public void acceptConnectionAsServer() throws IOException {
        startServer();
        waitForNextClient();
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

        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
        socket.connect();

        ClientConnection conn = new ClientConnection(
                deviceAddress,
                safeDeviceName(device),
                socket,
                socket.getInputStream(),
                socket.getOutputStream()
        );

        clientConnections.put(deviceAddress, conn);

        syncLegacyFields(deviceAddress);

        Log.i("CardGame", "[INFO] [蓝牙] 客户端连接成功 | device=" + deviceAddress);
    }

    // ========================================================================
    //  多路连接查询
    // ========================================================================

    public int getConnectionCount() {
        return clientConnections.size();
    }

    public ClientConnection getConnection(String deviceAddress) {
        return clientConnections.get(deviceAddress);
    }

    public Map<String, ClientConnection> getAllConnections() {
        return Collections.unmodifiableMap(clientConnections);
    }

    public List<String> getAllDeviceAddresses() {
        return new ArrayList<>(clientConnections.keySet());
    }

    // ========================================================================
    //  单连接兼容方法（返回最近操作的连接的信息）
    // ========================================================================

    private void syncLegacyFields(String deviceAddress) {
        ClientConnection conn = clientConnections.get(deviceAddress);
        if (conn != null) {
            this.bluetoothSocket = conn.socket;
            this.inputStream = conn.inputStream;
            this.outputStream = conn.outputStream;
            this.connectedDeviceName = conn.deviceName;
            this.connectedDeviceAddress = conn.deviceAddress;
            this.connected = true;
        }
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
        return !clientConnections.isEmpty();
    }

    // ========================================================================
    //  断开连接
    // ========================================================================

    /**
     * 断开指定设备的连接。
     */
    public void closeConnection(String deviceAddress) {
        ClientConnection conn = clientConnections.remove(deviceAddress);
        if (conn != null) {
            conn.close();
        }

        // 刷新兼容字段（指向剩余第一个连接或清空）
        if (!clientConnections.isEmpty()) {
            String firstKey = clientConnections.keySet().iterator().next();
            syncLegacyFields(firstKey);
        } else {
            bluetoothSocket = null;
            inputStream = null;
            outputStream = null;
            connectedDeviceName = null;
            connectedDeviceAddress = null;
            connected = false;
        }
    }

    /**
     * 关闭所有连接（包括 serverSocket）。
     */
    public void close() {
        connected = false;
        accepting = false;

        // 中断可能正在阻塞 accept() 的线程
        Thread t = acceptThread;
        if (t != null) {
            t.interrupt();
        }
        acceptThread = null;

        // 防御性复制，避免 closeConnection() 并发修改
        List<ClientConnection> connections = new ArrayList<>(clientConnections.values());
        clientConnections.clear();
        for (ClientConnection conn : connections) {
            conn.close();
        }

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        bluetoothSocket = null;
        inputStream = null;
        outputStream = null;
        connectedDeviceName = null;
        connectedDeviceAddress = null;
        serverSocket = null;
    }

    /**
     * 获取本机蓝牙设备名称，用于 JOIN 请求中标识自己。
     */
    @SuppressLint("MissingPermission")
    public String getLocalDeviceName() {
        if (bluetoothAdapter == null) {
            return "Player";
        }
        String name = bluetoothAdapter.getName();
        return (name != null && !name.trim().isEmpty()) ? name : "Player";
    }

    // ========================================================================
    //  工具方法
    // ========================================================================

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

    // ========================================================================
    //  内部类：单路客户端连接
    // ========================================================================

    public static class ClientConnection {

        public final String deviceAddress;
        public final String deviceName;
        public final BluetoothSocket socket;
        public final InputStream inputStream;
        public final OutputStream outputStream;

        private ClientConnection(String deviceAddress,
                                  String deviceName,
                                  BluetoothSocket socket,
                                  InputStream inputStream,
                                  OutputStream outputStream) {
            this.deviceAddress = deviceAddress;
            this.deviceName = deviceName;
            this.socket = socket;
            this.inputStream = inputStream;
            this.outputStream = outputStream;
        }

        public void close() {
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
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}
