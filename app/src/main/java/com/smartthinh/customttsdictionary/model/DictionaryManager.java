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
    private static volatile boolean isChineseDictionary = false;

    /**
     * Bang anh xa dau cau tieng Trung sang Latin + khoang trang theo chuan zhcnViDict
     */
    public static final Map<String, String> PUNCT_MAP = new HashMap<>();
    static {
        PUNCT_MAP.put("。", ". ");
        PUNCT_MAP.put("，", ", ");
        PUNCT_MAP.put("、", ", ");
        PUNCT_MAP.put("；", "; ");
        PUNCT_MAP.put("：", ": ");
        PUNCT_MAP.put("！", "! ");
        PUNCT_MAP.put("？", "? ");
        PUNCT_MAP.put("“", "\"");
        PUNCT_MAP.put("”", "\"");
        PUNCT_MAP.put("‘", "'");
        PUNCT_MAP.put("’", "'");
        PUNCT_MAP.put("（", " (");
        PUNCT_MAP.put("）", ") ");
        PUNCT_MAP.put("【", " [");
        PUNCT_MAP.put("】", "] ");
        PUNCT_MAP.put("《", " <");
        PUNCT_MAP.put("》", "> ");
        PUNCT_MAP.put("…", "... ");
        PUNCT_MAP.put("—", "- ");
    }

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
        isChineseDictionary = false;
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
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8), 32768)) {
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
            boolean detectedChinese = false;

            if (activeDict != null && !activeDict.isEmpty()) {
                File dir = getDictionaryDirectory(context);
                File file = new File(dir, activeDict + FILE_EXTENSION);
                if (!file.exists()) file = new File(dir, activeDict + ".txt");
                if (!file.exists()) file = new File(dir, activeDict + ".json");

                if (file.exists()) {
                    long startTime = System.currentTimeMillis();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8), 32768)) {
                        String line;
                        int count = 0;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty() || line.startsWith("#")) {
                                continue;
                            }

                            String[] parts = line.split("\t");
                            if (parts.length >= 2) {
                                String orig = parts[0].trim();
                                if (orig.startsWith("\uFEFF")) {
                                    orig = orig.substring(1).trim();
                                }
                                String repl = parts[1].trim();

                                if (orig.isEmpty()) {
                                    continue;
                                }

                                boolean isEntryChinese = containsChinese(orig);
                                if (isEntryChinese) {
                                    detectedChinese = true;
                                }

                                // 1. FILTER: Neu la tu tieng Trung va la dau cau toan giac -> bo qua de PUNCT_MAP xu ly
                                if (isEntryChinese && PUNCT_MAP.containsKey(orig)) {
                                    continue;
                                }

                                // 2. CLEANUP: Chi ap dung xu ly '的' va '了' neu tu goc chua chu Han
                                if (isEntryChinese) {
                                    if (orig.equals("的")) {
                                        repl = "";
                                    } else if (orig.endsWith("的")) {
                                        if (repl.endsWith(" đích")) {
                                            repl = repl.substring(0, repl.length() - 5);
                                        } else if (repl.endsWith("đích")) {
                                            repl = repl.substring(0, repl.length() - 4);
                                        }
                                    }

                                    if (orig.equals("了")) {
                                        repl = ""; // Mute '了'
                                    } else if (orig.endsWith("了")) {
                                        if (repl.endsWith(" liễu")) {
                                            repl = repl.substring(0, repl.length() - 5);
                                        } else if (repl.endsWith("liễu")) {
                                            repl = repl.substring(0, repl.length() - 4);
                                        }
                                    }
                                }

                                // 3. FIRST ENTRY WINS: Giu nguyen nghia dau tien (nghia chuan nhat)
                                if (!rules.containsKey(orig)) {
                                    rules.put(orig, repl);
                                    if (orig.length() > maxLen) {
                                        maxLen = orig.length();
                                    }
                                }

                                // Ho tro tim kiem khong phan biet hoa thuong cho tu Latinh
                                String lower = orig.toLowerCase(Locale.ROOT);
                                if (!lower.equals(orig) && !rules.containsKey(lower)) {
                                    rules.put(lower, repl);
                                }

                                count++;
                            }
                        }
                        long elapsed = System.currentTimeMillis() - startTime;
                        Log.d(TAG, "Da nap tu dien [" + activeDict + "]: " + rules.size() + " tu hop le (Trung-Viet: " + detectedChinese + ", mat " + elapsed + "ms)");
                    } catch (Exception e) {
                        Log.e(TAG, "Loi khi nap tu dien tu file: " + file.getName(), e);
                    }
                }
            }

            cachedRules = rules;
            cachedMaxKeyLength = Math.min(maxLen > 0 ? maxLen : 50, 50);
            cachedDictionaryName = activeDict;
            isChineseDictionary = detectedChinese;
            isCacheValid = true;
        }
    }

    /**
     * Thuat toan dich thuat va thay the tu dien ke thua hoan toan tu zhcnViDict:
     * - Longest match first (toi da 50 ky tu)
     * - Chuan hoa dau cau tieng Trung toan giac sang Latin + khoang cach (PUNCT_MAP)
     * - Tu dong chen khoang trang thong minh (Smart Spacing) ngan chan dinh chu
     * - Tu dong loai bo tro tu gay nhieu (nhu liễu, đích)
     */
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
        int maxLimit = cachedMaxKeyLength > 0 ? cachedMaxKeyLength : 50;
        if (maxLimit > 50) maxLimit = 50;

        StringBuilder result = new StringBuilder(textLen * 2);

        int i = 0;
        boolean lastWasReplacement = false;

        while (i < textLen) {
            int maxMatchLen = Math.min(maxLimit, textLen - i);
            boolean matched = false;

            // Thu tim tu dai nhat truoc (Longest match first)
            for (int len = maxMatchLen; len > 0; len--) {
                String substring = text.substring(i, i + len);
                String vietnamese = rules.get(substring);

                if (vietnamese == null) {
                    vietnamese = rules.get(substring.toLowerCase(Locale.ROOT));
                }

                if (vietnamese != null) {
                    // Kiem tra boundary cho tu Latinh de khong thay the nua chung tu khac
                    if (isLatinWordOnly(substring)) {
                        boolean boundaryOk = true;
                        if (i > 0 && Character.isLetterOrDigit(text.charAt(i - 1))) {
                            boundaryOk = false;
                        }
                        if (i + len < textLen && Character.isLetterOrDigit(text.charAt(i + len))) {
                            boundaryOk = false;
                        }
                        if (!boundaryOk) {
                            continue;
                        }
                    }

                    // Add space BEFORE replacement if previous char was not space/punct
                    if (!vietnamese.isEmpty()) {
                        if (result.length() > 0) {
                            char lastChar = result.charAt(result.length() - 1);
                            if (" \t\n\r\"'([<{".indexOf(lastChar) == -1) {
                                result.append(' ');
                            }
                        }
                        result.append(vietnamese);
                        lastWasReplacement = true;
                    }

                    i += len;
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                // Ky tu khong khop trong tu dien
                String currentCharStr = text.substring(i, i + 1);

                // Chuan hoa dau cau tieng Trung (chi ap dung khi tu dien co chua chu Han)
                String mappedPunct = isChineseDictionary ? PUNCT_MAP.get(currentCharStr) : null;
                String charToAppend = mappedPunct != null ? mappedPunct : currentCharStr;

                // Add space BEFORE original char IF previous was a replacement
                // BUT NOT if current char is punctuation (comma, dot, etc)
                if (lastWasReplacement) {
                    char firstChar = charToAppend.charAt(0);
                    if (" \t\n\r,.;:!?)[]>}'\"-_".indexOf(firstChar) == -1) {
                        result.append(' ');
                    }
                }

                result.append(charToAppend);
                lastWasReplacement = false;
                i++;
            }
        }

        return result.toString();
    }

    public static boolean containsChinese(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION) {
                return true;
            }
        }
        return false;
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
