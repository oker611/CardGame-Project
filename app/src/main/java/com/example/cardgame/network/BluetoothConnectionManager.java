package com.example.cardgame.network;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BluetoothConnectionManager {

    private static final String SERVICE_NAME = "CardGameBluetoothService";
    private static final UUID SERVICE_UUID =
            UUID.fromString("a5c93a6e-6c0f-4a21-8e6d-9dd3b8a3d7c1");

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
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public boolean isBluetoothAvailable() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
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

    @SuppressLint("MissingPermission")
    public void acceptConnectionAsServer() throws IOException {
        if (bluetoothAdapter == null) {
            throw new IOException("Bluetooth is not available");
        }

        serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                SERVICE_NAME,
                SERVICE_UUID
        );

        bluetoothSocket = serverSocket.accept();

        setupStreams();
    }

    @SuppressLint("MissingPermission")
    public void connectToDevice(String deviceAddress) throws IOException {
        if (bluetoothAdapter == null) {
            throw new IOException("Bluetooth is not available");
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
    }
}