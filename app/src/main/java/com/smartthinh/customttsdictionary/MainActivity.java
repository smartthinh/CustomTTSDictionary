package com.smartthinh.customttsdictionary;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.smartthinh.customttsdictionary.model.DictionaryItem;
import com.smartthinh.customttsdictionary.model.DictionaryManager;
import com.smartthinh.customttsdictionary.service.ProxyTtsService;
import com.smartthinh.customttsdictionary.ui.DictionaryAdapter;
import com.smartthinh.customttsdictionary.ui.WordAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT_DIC = 2001;
    private static final int REQ_EXPORT_DIC = 2002;
    private static final int REQ_STORAGE_PERMISSION = 1001;

    private ListView lvDictionaries;
    private TextView tvEmptyList;
    private TextView tvCurrentEngine;
    private TextView tvActiveDictStatus;
    private DictionaryAdapter adapter;
    private List<String> dictionaryList = new ArrayList<>();
    private SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String currentExportDictName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(ProxyTtsService.PREFS_NAME, MODE_PRIVATE);

        lvDictionaries = findViewById(R.id.lvDictionaries);
        tvEmptyList = findViewById(R.id.tvEmptyList);
        tvCurrentEngine = findViewById(R.id.tvCurrentEngine);
        tvActiveDictStatus = findViewById(R.id.tvActiveDictStatus);

        Button btnMainAddDictionary = findViewById(R.id.btnMainAddDictionary);
        Button btnMainImportDictionary = findViewById(R.id.btnMainImportDictionary);
        Button btnMainSelectEngine = findViewById(R.id.btnMainSelectEngine);
        Button btnOpenSystemSettings = findViewById(R.id.btnOpenSystemSettings);
        Button btnOpenWebsite = findViewById(R.id.btnOpenWebsite);

        btnMainAddDictionary.setOnClickListener(v -> showAddDictionaryStep1());
        btnMainImportDictionary.setOnClickListener(v -> launchImportFilePicker());
        btnMainSelectEngine.setOnClickListener(v -> showSelectEngineDialog());
        btnOpenSystemSettings.setOnClickListener(v -> openSystemTtsSettings());
        btnOpenWebsite.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://smartthinh.com"));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Không thể mở trình duyệt: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // 1. Chạm vào bộ từ điển:
        // - Nếu chưa được chọn: Chọn bộ này để sử dụng.
        // - Nếu đã được chọn: Mở danh sách từ.
        lvDictionaries.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String dictName = dictionaryList.get(position);
                String activeDict = DictionaryManager.getActiveDictionary(MainActivity.this);

                if (dictName.equals(activeDict)) {
                    loadAndShowWordListDialog(dictName);
                } else {
                    DictionaryManager.setActiveDictionary(MainActivity.this, dictName);
                    Toast.makeText(MainActivity.this, "Đã chọn sử dụng: " + dictName, Toast.LENGTH_SHORT).show();
                    refreshDictionaryList();
                    updateEngineStatusText();
                }
            }
        });

        // Nhấn giữ để xóa bộ từ điển
        lvDictionaries.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                String dictName = dictionaryList.get(position);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Xóa bộ từ điển")
                        .setMessage("Bạn có chắc chắn muốn xóa bộ từ điển \"" + dictName + "\" không?")
                        .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                            DictionaryManager.deleteDictionary(MainActivity.this, dictName);
                            refreshDictionaryList();
                            updateEngineStatusText();
                            Toast.makeText(MainActivity.this, "Đã xóa: " + dictName, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .show();
                return true;
            }
        });

        // Yêu cầu quyền trực tiếp nếu cần (Android 10 trở xuống)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, REQ_STORAGE_PERMISSION);
            }
        }

        updateEngineStatusText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDictionaryList();
        updateEngineStatusText();
    }

    /**
     * Mở trình chọn tệp chuẩn của hệ thống để nhập tệp .dic
     */
    private void launchImportFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, REQ_IMPORT_DIC);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                fallback.setType("*/*");
                startActivityForResult(fallback, REQ_IMPORT_DIC);
            } catch (Exception ex) {
                Toast.makeText(this, "Không thể mở trình chọn tệp.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Xử lý nhập tệp từ điển (.dic)
        if (requestCode == REQ_IMPORT_DIC && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String fileName = getFileNameFromUri(uri);

            if (fileName == null || !fileName.toLowerCase().endsWith(".dic")) {
                Toast.makeText(this, "Chỉ chấp nhận tệp có đuôi là .dic! Vui lòng chọn đúng tệp từ điển.", Toast.LENGTH_LONG).show();
                return;
            }

            String dictName = fileName.substring(0, fileName.length() - 4);
            importDictionaryFromUri(uri, dictName);
        }

        // Xử lý xuất tệp từ điển ra vị trí người dùng tự chọn
        if (requestCode == REQ_EXPORT_DIC && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (currentExportDictName != null) {
                exportDictionaryToUri(currentExportDictName, uri);
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String name = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        name = cursor.getString(index);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (name == null) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    name = path.substring(cut + 1);
                }
            }
        }
        return name;
    }

    /**
     * Nhập tệp .dic vào vùng nhớ riêng tư nội bộ trong luồng ngầm (không lag điện thoại)
     */
    private void importDictionaryFromUri(final Uri uri, final String dictName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Đang nhập từ điển: " + dictName);
        builder.setMessage("Đang sao chép vào bộ nhớ riêng tư...");

        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setPadding(30, 30, 30, 30);
        builder.setView(progressBar);
        builder.setCancelable(false);

        final AlertDialog progressDialog = builder.create();
        progressDialog.show();

        new Thread(() -> {
            boolean success = false;
            try {
                File targetDir = DictionaryManager.getDictionaryDirectory(MainActivity.this);
                File targetFile = new File(targetDir, dictName + ".dic");

                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[32768]; // 32KB buffer sao chép cực nhanh
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                    success = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            final boolean isOk = success;
            mainHandler.post(() -> {
                try { progressDialog.dismiss(); } catch (Exception ignored) {}

                if (isOk) {
                    DictionaryManager.setActiveDictionary(MainActivity.this, dictName);
                    refreshDictionaryList();
                    updateEngineStatusText();
                    Toast.makeText(MainActivity.this, "Đã nhập thành công bộ từ điển: " + dictName, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "Lỗi khi nhập tệp từ điển!", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void exportDictionaryToUri(final String dictName, final Uri destUri) {
        new Thread(() -> {
            boolean success = false;
            try {
                File dir = DictionaryManager.getDictionaryDirectory(MainActivity.this);
                File src = new File(dir, dictName + ".dic");
                if (!src.exists()) src = new File(dir, dictName + ".txt");

                if (src.exists()) {
                    try (InputStream in = new FileInputStream(src);
                         OutputStream out = getContentResolver().openOutputStream(destUri)) {
                        byte[] buffer = new byte[32768];
                        int read;
                        while ((read = in.read(buffer)) > 0) {
                            out.write(buffer, 0, read);
                        }
                        out.flush();
                        success = true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            final boolean isOk = success;
            mainHandler.post(() -> {
                if (isOk) {
                    Toast.makeText(MainActivity.this, "Đã xuất tệp từ điển thành công!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "Không thể xuất tệp từ điển!", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void showAddDictionaryStep1() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_add_dict_name);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_dict_name, null);
        final EditText etDictName = view.findViewById(R.id.etDictName);
        builder.setView(view);

        builder.setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
            String dictName = etDictName.getText().toString().trim();
            if (TextUtils.isEmpty(dictName)) {
                Toast.makeText(MainActivity.this, "Vui lòng nhập tên bộ từ điển!", Toast.LENGTH_SHORT).show();
                return;
            }
            showWordInputDialog("Thêm từ điển: " + dictName, "", "", (orig, repl) -> {
                boolean success = DictionaryManager.createDictionaryWithFirstWord(
                        MainActivity.this, dictName, orig, repl);
                if (success) {
                    Toast.makeText(MainActivity.this, "Đã tạo: " + dictName, Toast.LENGTH_SHORT).show();
                    refreshDictionaryList();
                    updateEngineStatusText();
                } else {
                    Toast.makeText(MainActivity.this, "Không thể lưu tệp từ điển!", Toast.LENGTH_SHORT).show();
                }
            });
        });

        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    interface OnWordInputListener {
        void onConfirmed(String originalWord, String replacementWord);
    }

    private void showWordInputDialog(String title, String defaultOrig, String defaultRepl, OnWordInputListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_word_pair, null);
        final EditText etOrig = view.findViewById(R.id.etOriginalWord);
        final EditText etRepl = view.findViewById(R.id.etReplacementWord);

        if (defaultOrig != null) etOrig.setText(defaultOrig);
        if (defaultRepl != null) etRepl.setText(defaultRepl);
        builder.setView(view);

        builder.setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
            String orig = etOrig.getText().toString().trim();
            String repl = etRepl.getText().toString().trim();

            if (TextUtils.isEmpty(orig) || TextUtils.isEmpty(repl)) {
                Toast.makeText(MainActivity.this, "Vui lòng nhập đầy đủ từ gốc và từ thay thế!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (listener != null) {
                listener.onConfirmed(orig, repl);
            }
        });

        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void loadAndShowWordListDialog(final String dictName) {
        final AtomicBoolean isCancelled = new AtomicBoolean(false);

        AlertDialog.Builder progressBuilder = new AlertDialog.Builder(this);
        progressBuilder.setTitle("Đang tải bộ từ điển...");
        progressBuilder.setMessage("Vui lòng đợi giây lát...");

        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setPadding(30, 30, 30, 30);
        progressBuilder.setView(progressBar);
        progressBuilder.setCancelable(false);

        progressBuilder.setNegativeButton("Hủy", (dialog, which) -> {
            isCancelled.set(true);
            dialog.dismiss();
        });

        final AlertDialog progressDialog = progressBuilder.create();
        progressDialog.show();

        new Thread(() -> {
            final List<DictionaryItem> items = DictionaryManager.loadDictionary(MainActivity.this, dictName);

            mainHandler.post(() -> {
                if (isCancelled.get()) return;
                try {
                    progressDialog.dismiss();
                } catch (Exception ignored) {}

                showWordListDialog(dictName, items);
            });
        }).start();
    }

    /**
     * Hộp thoại hiển thị danh sách từ:
     * - Trên cùng: Ô tìm kiếm thời gian thực
     * - Ở giữa: Danh sách từ chiếm phần lớn diện tích
     * - Dưới cùng: Nút "Thêm từ", "Xóa tất cả từ", "Xuất từ điển"
     */
    private void showWordListDialog(final String dictName, final List<DictionaryItem> items) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Bộ từ điển: " + dictName);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_word_list, null);
        builder.setView(view);

        final EditText etSearch = view.findViewById(R.id.etSearchWord);
        final TextView tvCount = view.findViewById(R.id.tvDialogWordCount);
        final ListView lvWords = view.findViewById(R.id.lvWords);
        final TextView tvEmpty = view.findViewById(R.id.tvEmptyWords);
        final Button btnAdd = view.findViewById(R.id.btnAddWordInList);
        final Button btnClearAll = view.findViewById(R.id.btnClearAllWords);
        final Button btnExport = view.findViewById(R.id.btnExportDictionary);

        final WordAdapter wordAdapter = new WordAdapter(this, items);
        lvWords.setAdapter(wordAdapter);

        final Runnable updateCountAndVisibility = () -> {
            int displayedCount = wordAdapter.getCount();
            int totalCount = items.size();
            if (displayedCount == totalCount) {
                tvCount.setText("Tổng số: " + totalCount + " từ (Chạm để sửa, nhấn giữ để xóa)");
            } else {
                tvCount.setText("Tìm thấy: " + displayedCount + " / " + totalCount + " từ");
            }

            if (displayedCount == 0) {
                tvEmpty.setVisibility(View.VISIBLE);
                lvWords.setVisibility(View.GONE);
            } else {
                tvEmpty.setVisibility(View.GONE);
                lvWords.setVisibility(View.VISIBLE);
            }
        };
        updateCountAndVisibility.run();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                wordAdapter.filter(s != null ? s.toString() : "");
                updateCountAndVisibility.run();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        final AlertDialog dialog = builder.create();

        // 1. Chạm vào một từ để sửa
        lvWords.setOnItemClickListener((parent, v, position, id) -> {
            DictionaryItem item = wordAdapter.getItem(position);
            showWordInputDialog("Chỉnh sửa từ", item.getOriginalWord(), item.getReplacementWord(), (orig, repl) -> {
                item.setOriginalWord(orig);
                item.setReplacementWord(repl);
                DictionaryManager.saveDictionary(MainActivity.this, dictName, items);
                wordAdapter.refresh();
                updateCountAndVisibility.run();
                Toast.makeText(MainActivity.this, "Đã lưu chỉnh sửa!", Toast.LENGTH_SHORT).show();
            });
        });

        // 2. Nhấn giữ vào một từ để xóa
        lvWords.setOnItemLongClickListener((parent, v, position, id) -> {
            DictionaryItem item = wordAdapter.getItem(position);
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Xóa từ này?")
                    .setMessage("Bạn có chắc chắn muốn xóa từ: \"" + item.getOriginalWord() + "\" không?")
                    .setPositiveButton(R.string.dialog_ok, (d, w) -> {
                        items.remove(item);
                        DictionaryManager.saveDictionary(MainActivity.this, dictName, items);
                        wordAdapter.refresh();
                        updateCountAndVisibility.run();
                        Toast.makeText(MainActivity.this, "Đã xóa từ!", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();
            return true;
        });

        // 3. Nút thêm từ mới
        btnAdd.setOnClickListener(v -> {
            showWordInputDialog("Thêm từ mới vào: " + dictName, "", "", (orig, repl) -> {
                items.add(new DictionaryItem(orig, repl));
                DictionaryManager.saveDictionary(MainActivity.this, dictName, items);
                wordAdapter.refresh();
                updateCountAndVisibility.run();
                Toast.makeText(MainActivity.this, "Đã thêm từ mới!", Toast.LENGTH_SHORT).show();
            });
        });

        // 4. Nút xóa tất cả các từ
        btnClearAll.setOnClickListener(v -> {
            if (items.isEmpty()) {
                Toast.makeText(MainActivity.this, "Bộ từ điển này đang trống!", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Xóa tất cả các từ")
                    .setMessage("Bạn có chắc chắn muốn xóa TOÀN BỘ từ trong bộ \"" + dictName + "\" không?")
                    .setPositiveButton(R.string.dialog_ok, (d, w) -> {
                        items.clear();
                        DictionaryManager.saveDictionary(MainActivity.this, dictName, items);
                        wordAdapter.refresh();
                        updateCountAndVisibility.run();
                        Toast.makeText(MainActivity.this, "Đã xóa toàn bộ từ!", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();
        });

        // 5. Nút xuất từ điển ra bộ nhớ điện thoại
        btnExport.setOnClickListener(v -> {
            File exported = DictionaryManager.exportToDownloads(MainActivity.this, dictName);
            if (exported != null) {
                Toast.makeText(MainActivity.this, "Đã xuất ra: Download/" + dictName + ".dic", Toast.LENGTH_LONG).show();
            } else {
                // Neu khong ghi truc tiep duoc thi mo trinh chon thu muc luu
                currentExportDictName = dictName;
                try {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TITLE, dictName + ".dic");
                    startActivityForResult(intent, REQ_EXPORT_DIC);
                } catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "Không thể xuất tệp.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        dialog.show();

        // Mở rộng kích thước hộp thoại để danh sách từ hiển thị thoải mái nhất
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showSelectEngineDialog() {
        TextToSpeech tempTts = new TextToSpeech(this, null);
        List<TextToSpeech.EngineInfo> engines = tempTts.getEngines();

        final List<String> packageNames = new ArrayList<>();
        final List<String> displayNames = new ArrayList<>();

        String myPackage = getPackageName();
        int selectedIndex = -1;
        String currentSavedPackage = prefs.getString(ProxyTtsService.KEY_SELECTED_ENGINE, "");

        for (TextToSpeech.EngineInfo info : engines) {
            if (myPackage.equals(info.name)) continue;

            packageNames.add(info.name);
            displayNames.add(info.label + " (" + info.name + ")");

            if (info.name.equals(currentSavedPackage)) {
                selectedIndex = packageNames.size() - 1;
            }
        }

        try {
            tempTts.shutdown();
        } catch (Exception ignored) {}

        if (displayNames.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy công cụ TTS nào trên thiết bị!", Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.btn_select_engine);

        CharSequence[] items = displayNames.toArray(new CharSequence[0]);
        builder.setSingleChoiceItems(items, selectedIndex, (dialog, which) -> {
            String chosenPackage = packageNames.get(which);
            String chosenLabel = displayNames.get(which);

            prefs.edit()
                    .putString(ProxyTtsService.KEY_SELECTED_ENGINE, chosenPackage)
                    .putString("selected_tts_label", chosenLabel)
                    .commit();

            try {
                Intent reloadIntent = new Intent(MainActivity.this, ProxyTtsService.class);
                reloadIntent.setAction(ProxyTtsService.ACTION_RELOAD_ENGINE);
                reloadIntent.putExtra(ProxyTtsService.EXTRA_ENGINE_PACKAGE, chosenPackage);
                startService(reloadIntent);
            } catch (Exception e) {
                e.printStackTrace();
            }

            Toast.makeText(MainActivity.this, "Đã chọn: " + chosenLabel, Toast.LENGTH_SHORT).show();
            updateEngineStatusText();
            dialog.dismiss();
        });

        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void openSystemTtsSettings() {
        try {
            Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                fallback.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
            } catch (Exception ex) {
                Toast.makeText(this, "Không thể mở cài đặt TTS hệ thống.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void refreshDictionaryList() {
        dictionaryList.clear();
        dictionaryList.addAll(DictionaryManager.getDictionaryNames(this));

        String activeDict = DictionaryManager.getActiveDictionary(this);

        if (adapter == null) {
            adapter = new DictionaryAdapter(this, dictionaryList, activeDict);
            lvDictionaries.setAdapter(adapter);
        } else {
            adapter.setActiveDictionary(activeDict);
            adapter.notifyDataSetChanged();
        }

        if (dictionaryList.isEmpty()) {
            tvEmptyList.setVisibility(View.VISIBLE);
            lvDictionaries.setVisibility(View.GONE);
        } else {
            tvEmptyList.setVisibility(View.GONE);
            lvDictionaries.setVisibility(View.VISIBLE);
        }
    }

    private void updateEngineStatusText() {
        String activeDict = DictionaryManager.getActiveDictionary(this);
        if (activeDict != null) {
            tvActiveDictStatus.setText("Từ điển đang dùng: " + activeDict);
        } else {
            tvActiveDictStatus.setText("Từ điển đang dùng: Chưa chọn (Chạm bộ từ điển để chọn)");
        }

        String savedPackage = prefs.getString(ProxyTtsService.KEY_SELECTED_ENGINE, null);
        String savedLabel = prefs.getString("selected_tts_label", null);
        if (savedLabel != null) {
            tvCurrentEngine.setText("Công cụ TTS đang chọn: " + savedLabel);
        } else if (savedPackage != null) {
            tvCurrentEngine.setText("Công cụ TTS đang chọn: " + savedPackage);
        } else {
            tvCurrentEngine.setText("Công cụ TTS đang chọn: Mặc định hệ thống (Chưa chọn)");
        }
    }
}
