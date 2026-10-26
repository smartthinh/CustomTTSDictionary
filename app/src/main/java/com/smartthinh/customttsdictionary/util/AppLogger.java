package com.smartthinh.customttsdictionary.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class AppLogger {
    private static final int MAX_LOGS = 500;
    private static final LinkedList<String> logList = new LinkedList<>();
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private static LogListener listener = null;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static File persistentLogFile = null;
    private static boolean initialized = false;

    public interface LogListener {
        void onLogAdded(String logLine);
    }

    /**
     * Khoi tao bo ghi log voi bo nho rieng tu noi bo (Internal Storage).
     * Giup luu giu toan bo nhat ky hoat dong ngay ca khi thoat ung dung hoac tat may.
     */
    public static synchronized void init(Context context) {
        if (context == null) return;
        if (persistentLogFile == null) {
            File filesDir = context.getApplicationContext().getFilesDir();
            persistentLogFile = new File(filesDir, "app_runtime.log");
        }
        if (!initialized) {
            initialized = true;
            if (persistentLogFile.exists()) {
                LinkedList<String> previousLogs = new LinkedList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(persistentLogFile), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (previousLogs.size() >= MAX_LOGS) {
                            previousLogs.removeFirst();
                        }
                        previousLogs.addLast(line);
                    }
                } catch (Exception ignored) {
                }

                // Neu co log da ghi truoc khi goi init thi dua them vao
                for (String curr : logList) {
                    if (previousLogs.size() >= MAX_LOGS) {
                        previousLogs.removeFirst();
                    }
                    previousLogs.addLast(curr);
                }
                logList.clear();
                logList.addAll(previousLogs);
            }
            rewriteFile();
        }
    }

    public static synchronized void setListener(LogListener l) {
        listener = l;
    }

    public static synchronized void log(String tag, String message) {
        String timestamp = sdf.format(new Date());
        String logLine = "[" + timestamp + "][" + tag + "] " + message;

        android.util.Log.d(tag, message);

        if (logList.size() >= MAX_LOGS) {
            logList.removeFirst();
        }
        logList.addLast(logLine);

        // Ghi vao bo nho noi bo de giu nguyen khi thoat ung dung
        appendToFile(logLine);

        if (listener != null) {
            final String finalLine = logLine;
            mainHandler.post(() -> {
                synchronized (AppLogger.class) {
                    if (listener != null) {
                        listener.onLogAdded(finalLine);
                    }
                }
            });
        }
    }

    private static void appendToFile(String logLine) {
        if (persistentLogFile == null) return;
        try {
            // Neu tep vuot qua 1MB thi ghi lai giu lai cac dong moi nhat
            if (persistentLogFile.length() > 1024 * 1024) {
                rewriteFile();
                return;
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(persistentLogFile, true), StandardCharsets.UTF_8)) {
                writer.write(logLine + "\n");
                writer.flush();
            }
        } catch (Exception ignored) {
        }
    }

    private static void rewriteFile() {
        if (persistentLogFile == null) return;
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(persistentLogFile, false), StandardCharsets.UTF_8)) {
            for (String line : logList) {
                writer.write(line + "\n");
            }
            writer.flush();
        } catch (Exception ignored) {
        }
    }

    public static synchronized List<String> getLogs() {
        return new ArrayList<>(logList);
    }

    public static synchronized String getAllLogsText() {
        StringBuilder sb = new StringBuilder();
        for (String line : logList) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    public static synchronized void clearLogs() {
        logList.clear();
        if (persistentLogFile != null && persistentLogFile.exists()) {
            persistentLogFile.delete();
        }
    }
}
