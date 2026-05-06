package com.example.cardgame.util;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

public class BluetoothPermissionHelper {

    private BluetoothPermissionHelper() {
    }

    public static boolean isBluetoothAvailable() {
        return BluetoothAdapter.getDefaultAdapter() != null;
    }

    public static boolean isBluetoothEnabled() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled();
    }

    /**
     * 创建房间 / 作为 Host：
     * Android 12+ 需要 CONNECT + ADVERTISE。
     */
    public static String[] getHostBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
            };
        }

        return new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION
        };
    }

    /**
     * 加入房间 / 搜索和连接：
     * Android 12+ 需要 SCAN + CONNECT。
     */
    public static String[] getClientBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        }

        return new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION
        };
    }

    /**
     * 兼容旧调用：默认按 Client 搜索连接权限处理。
     */
    public static String[] getRequiredBluetoothPermissions() {
        return getClientBluetoothPermissions();
    }

    public static boolean hasBluetoothPermissions(Context context, String[] permissions) {
        if (context == null || permissions == null) {
            return false;
        }

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    public static boolean hasHostBluetoothPermissions(Context context) {
        return hasBluetoothPermissions(context, getHostBluetoothPermissions());
    }

    public static boolean hasClientBluetoothPermissions(Context context) {
        return hasBluetoothPermissions(context, getClientBluetoothPermissions());
    }

    public static boolean hasBluetoothPermissions(Context context) {
        return hasClientBluetoothPermissions(context);
    }
}