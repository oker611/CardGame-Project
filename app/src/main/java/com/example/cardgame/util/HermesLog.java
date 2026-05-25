package com.example.cardgame.util;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 诊断日志工具：写入设备文件，无需 adb。
 * 文件路径：/sdcard/Download/hermes_debug_<role>.txt
 */
public class HermesLog {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static File logFile;
    private static String role = "unknown";

    public static void init(String deviceRole) {
        role = deviceRole;
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        logFile = new File(dir, "hermes_debug_" + role + ".txt");
        log("=== Hermes Debug Log Start | role=" + role + " ===");
    }

    public static synchronized void log(String msg) {
        String line = SDF.format(new Date()) + " " + msg;
        Log.i("HermesLog", line);
        System.out.println(line);

        if (logFile == null) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
            pw.println(line);
        } catch (IOException ignored) {
        }
    }

    public static String getFilePath() {
        return logFile != null ? logFile.getAbsolutePath() : "not initialized";
    }
}
