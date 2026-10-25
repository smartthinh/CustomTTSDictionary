package com.smartthinh.customttsdictionary.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DictionaryManager {
    private static final String TAG = "DictionaryManager";
    public static final String DIRECTORY_NAME = "Dictionaries";
    public static final String FILE_EXTENSION = ".dic";
    public static final String PREFS_NAME = "custom_tts_prefs";
    public static final String KEY_ACTIVE_DICTIONARY = "active_dictionary_name";

    private static volatile Map<String, String> cachedRules = null;
    private static volatile int cachedMaxKeyLength = 0;
    private static volatile boolean isCacheValid = false;
    private static volatile String cachedDictionaryName = null;

    /**
     * Lay thu muc luu tru trong VUNG RIENG TU NOI BO cua ung dung:
     * /data/user/0/com.smartthinh.customttsdictionary/files/Dictionaries/
     */
    public static File getDictionaryDirectory(Context context) {
        File dir = new File(context.getFilesDir(), DIRECTORY_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Tu dong di chuyen cac bo tu dien cu (neu co) sang vung nho moi de khong mat du lieu
        migrateOldDictionariesIfAny(context, dir);

        return dir;
    }

    private static void migrateOldDictionariesIfAny(Context context, File targetDir) {
        try {
            // Kiem tra thu muc cu trong Smart Thinh
            File externalDir = new File(Environment.getExternalStorageDirectory(), "Smart Thinh/Dictionaries");
            if (externalDir.exists() && externalDir.isDirectory()) {
                File[] oldFiles = externalDir.listFiles();
                if (oldFiles != null) {
                    for (File f : oldFiles) {
                        if (f.isFile() && f.getName().endsWith(FILE_EXTENSION)) {
                            File dest = new File(targetDir, f.getName());
                            if (!dest.exists()) {
                                copyFile(f, dest);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static void copyFile(File src, File dst) {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (Exception ignored) {}
    }

    public static String getActiveDictionary(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_ACTIVE_DICTIONARY, null);
    }

    public static void setActiveDictionary(Context context, String dictName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ACTIVE_DICTIONARY, dictName).commit();
        invalidateCache();
    }

    public static synchronized void invalidateCache() {
        isCacheValid = false;
        cachedRules = null;
        cachedMaxKeyLength = 0;
        cachedDictionaryName = null;
    }

    public static List<String> getDictionaryNames(Context context) {
        List<String> list = new ArrayList<>();
        File dir = getDictionaryDirectory(context);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String name = file.getName();
                    if (name.endsWith(FILE_EXTENSION)) {
                        list.add(name.substring(0, name.length() - FILE_EXTENSION.length()));
                    } else if (name.endsWith(".txt") || name.endsWith(".json")) {
                        int dotIndex = name.lastIndexOf('.');
                        if (dotIndex > 0) {
                            list.add(name.substring(0, dotIndex));
                        }
                    }
                }
            }
        }
        Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    public static List<DictionaryItem> loadDictionary(Context context, String dictName) {
        List<DictionaryItem> items = new ArrayList<>();
        File dir = getDictionaryDirectory(context);
        File file = new File(dir, dictName + FILE_EXTENSION);

        if (!file.exists()) file = new File(dir, dictName + ".txt");
        if (!file.exists()) file = new File(dir, dictName + ".json");
        if (!file.exists()) return items;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                DictionaryItem item = DictionaryItem.fromDicLine(line);
                if (item != null) {
                    items.add(item);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Loi khi doc tep tu dien: " + file.getName(), e);
        }
        return items;
    }

    public static boolean saveDictionary(Context context, String dictName, List<DictionaryItem> items) {
        File dir = getDictionaryDirectory(context);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, dictName + FILE_EXTENSION);

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write("# Dictionary: " + dictName + "\n");
            for (DictionaryItem item : items) {
                writer.write(item.toDicLine() + "\n");
            }
            writer.flush();
            invalidateCache();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Loi khi luu tep tu dien: " + dictName, e);
            return false;
        }
    }

    public static boolean createDictionaryWithFirstWord(Context context, String dictName, String originalWord, String replacementWord) {
        List<DictionaryItem> items = new ArrayList<>();
        items.add(new DictionaryItem(originalWord, replacementWord));
        boolean success = saveDictionary(context, dictName, items);
        if (success) {
            if (getActiveDictionary(context) == null) {
                setActiveDictionary(context, dictName);
            }
        }
        return success;
    }

    public static boolean deleteDictionary(Context context, String dictName) {
        File dir = getDictionaryDirectory(context);
        File file = new File(dir, dictName + FILE_EXTENSION);
        boolean deleted = file.exists() && file.delete();

        File txt = new File(dir, dictName + ".txt");
        if (txt.exists()) txt.delete();
        File json = new File(dir, dictName + ".json");
        if (json.exists()) json.delete();

        String active = getActiveDictionary(context);
        if (dictName.equals(active)) {
            setActiveDictionary(context, null);
        } else {
            invalidateCache();
        }
        return deleted;
    }

    /**
     * Xuat bo tu dien ra thu muc Download trong bo nho dien thoai
     */
    public static File exportToDownloads(Context context, String dictName) {
        try {
            File dir = getDictionaryDirectory(context);
            File srcFile = new File(dir, dictName + FILE_EXTENSION);
            if (!srcFile.exists()) {
                srcFile = new File(dir, dictName + ".txt");
            }
            if (!srcFile.exists()) return null;

            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }

            File destFile = new File(downloadDir, dictName + FILE_EXTENSION);
            copyFile(srcFile, destFile);
            return destFile;
        } catch (Exception e) {
            Log.e(TAG, "Loi khi xuat tu dien sang Download", e);
            return null;
        }
    }

    private static void ensureCacheLoaded(Context context) {
        String activeDict = getActiveDictionary(context);
        if (isCacheValid && cachedRules != null) {
            if (activeDict == null && cachedDictionaryName == null) return;
            if (activeDict != null && activeDict.equals(cachedDictionaryName)) return;
        }

        synchronized (DictionaryManager.class) {
            if (isCacheValid && cachedRules != null) {
                if (activeDict == null && cachedDictionaryName == null) return;
                if (activeDict != null && activeDict.equals(cachedDictionaryName)) return;
            }

            Map<String, String> rules = new HashMap<>();
            int maxLen = 0;

            if (activeDict != null && !activeDict.isEmpty()) {
                List<DictionaryItem> items = loadDictionary(context, activeDict);
                for (DictionaryItem item : items) {
                    String orig = item.getOriginalWord();
                    if (!orig.isEmpty()) {
                        String key = orig.toLowerCase(Locale.ROOT);
                        rules.put(key, item.getReplacementWord());
                        if (orig.length() > maxLen) {
                            maxLen = orig.length();
                        }
                    }
                }
            }

            cachedRules = rules;
            cachedMaxKeyLength = Math.min(maxLen, 60);
            cachedDictionaryName = activeDict;
            isCacheValid = true;
            Log.d(TAG, "Da nap RAM bo tu dien dang dung [" + activeDict + "]: " + rules.size() + " tu");
        }
    }

    public static String replaceFast(String text, Context context) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        ensureCacheLoaded(context);
        Map<String, String> rules = cachedRules;
        if (rules == null || rules.isEmpty()) {
            return text;
        }

        int textLen = text.length();
        int maxLen = cachedMaxKeyLength > 0 ? cachedMaxKeyLength : 30;
        StringBuilder result = new StringBuilder(textLen + 32);

        int i = 0;
        while (i < textLen) {
            int maxMatch = Math.min(maxLen, textLen - i);
            boolean matched = false;

            for (int len = maxMatch; len > 0; len--) {
                String sub = text.substring(i, i + len);
                String lowerSub = sub.toLowerCase(Locale.ROOT);

                String replacement = rules.get(lowerSub);
                if (replacement != null) {
                    boolean isLatinWord = isLatinWordOnly(lowerSub);
                    boolean boundaryOk = true;

                    if (isLatinWord) {
                        if (i > 0 && Character.isLetterOrDigit(text.charAt(i - 1))) {
                            boundaryOk = false;
                        }
                        if (i + len < textLen && Character.isLetterOrDigit(text.charAt(i + len))) {
                            boundaryOk = false;
                        }
                    }

                    if (boundaryOk) {
                        result.append(replacement);
                        i += len;
                        matched = true;
                        break;
                    }
                }
            }

            if (!matched) {
                result.append(text.charAt(i));
                i++;
            }
        }

        return result.toString();
    }

    private static boolean isLatinWordOnly(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 128) {
                if (!Character.isLetterOrDigit(c)) return false;
            } else {
                Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
                if (block == Character.UnicodeBlock.BASIC_LATIN ||
                    block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT ||
                    block == Character.UnicodeBlock.LATIN_EXTENDED_A ||
                    block == Character.UnicodeBlock.LATIN_EXTENDED_B ||
                    block == Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL) {
                    if (!Character.isLetterOrDigit(c)) return false;
                } else {
                    return false;
                }
            }
        }
        return true;
    }
}
