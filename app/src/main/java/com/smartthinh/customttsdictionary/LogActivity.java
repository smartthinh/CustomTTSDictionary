package com.smartthinh.customttsdictionary;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.smartthinh.customttsdictionary.util.AppLogger;
import com.smartthinh.customttsdictionary.util.LogFileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogActivity extends Activity {
    private ScrollView scrollLog;
    private TextView tvLogContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Nhật ký hoạt động (Logs)");
        setContentView(R.layout.activity_log);

        // Khoi tao bo luu tru log noi bo neu chua khoi tao
        AppLogger.init(this);

        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        scrollLog = findViewById(R.id.scrollLog);
        tvLogContent = findViewById(R.id.tvLogContent);
        Button btnSaveLog = findViewById(R.id.btnSaveLog);
        Button btnCopyLog = findViewById(R.id.btnCopyLog);
        Button btnClearLog = findViewById(R.id.btnClearLog);

        refreshLogs();

        // Lang nghe log moi real-time khi man hinh dang mo
        AppLogger.setListener(line -> refreshLogs());

        // Nut 1: Luu thanh tep .log o bo nho dung chung va mo giao dien chia se
        btnSaveLog.setOnClickListener(v -> saveAndShareLog());

        // Nut 2: Sao chep log vao Clipboard
        btnCopyLog.setOnClickListener(v -> copyLogToClipboard());

        // Nut 3: Xoa nhat ky
        btnClearLog.setOnClickListener(v -> {
            AppLogger.clearLogs();
            tvLogContent.setText("Chưa có nhật ký hoạt động nào...");
            Toast.makeText(LogActivity.this, "Đã xóa toàn bộ nhật ký!", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppLogger.setListener(null);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshLogs() {
        String allLogs = AppLogger.getAllLogsText();
        if (TextUtils.isEmpty(allLogs)) {
            tvLogContent.setText("Chưa có nhật ký hoạt động nào...");
        } else {
            tvLogContent.setText(allLogs);
            if (scrollLog != null) {
                scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
            }
        }
    }

    /**
     * Tao tep .log trong bo nho noi bo va bo nho dung chung (Downloads),
     * sau do hien thi hop thoai chia se voi cac ung dung khac (Zalo, Gmail, Drive, Telegram...).
     */
    private void saveAndShareLog() {
        String allLogs = AppLogger.getAllLogsText();
        if (TextUtils.isEmpty(allLogs)) {
            Toast.makeText(this, "Không có nhật ký nào để lưu!", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = "tts_log_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".log";

        // 1. Luu tep .log vao thu muc rieng tu de san sang chia se qua FileProvider
        File shareDir = new File(getFilesDir(), "shared_logs");
        if (!shareDir.exists()) {
            shareDir.mkdirs();
        }
        File logFile = new File(shareDir, fileName);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(logFile), StandardCharsets.UTF_8)) {
            writer.write(allLogs);
            writer.flush();
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi tạo tệp log: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Dong thoi luu tep .log vao bo nho dung chung (Thu muc Download)
        boolean savedToDownload = false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os != null) {
                            os.write(allLogs.getBytes(StandardCharsets.UTF_8));
                            os.flush();
                            savedToDownload = true;
                        }
                    }
                }
            }
            if (!savedToDownload) {
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs();
                }
                File publicLogFile = new File(downloadDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(publicLogFile)) {
                    fos.write(allLogs.getBytes(StandardCharsets.UTF_8));
                    fos.flush();
                    savedToDownload = true;
                }
            }
        } catch (Exception ignored) {
        }

        if (savedToDownload) {
            Toast.makeText(this, "Đã lưu " + fileName + " vào thư mục Download!", Toast.LENGTH_SHORT).show();
        }

        // 3. Hien thi giao dien cac phan mem de nguoi dung chia se tep .log
        try {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());

            Uri fileUri = Uri.parse("content://" + LogFileProvider.AUTHORITY + logFile.getAbsolutePath());

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, fileName);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Nhật ký hoạt động: " + fileName);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Chia sẻ tệp nhật ký (" + fileName + ") qua:"));
        } catch (Exception ex) {
            Toast.makeText(this, "Không thể mở trình chia sẻ: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyLogToClipboard() {
        String allLogs = AppLogger.getAllLogsText();
        if (TextUtils.isEmpty(allLogs)) {
            Toast.makeText(this, "Không có nhật ký nào để sao chép!", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("TTS_Logs", allLogs);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Đã sao chép nhật ký vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show();
        }
    }
}
