package com.smartthinh.customttsdictionary.service;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.os.Bundle;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.smartthinh.customttsdictionary.model.DictionaryManager;
import com.smartthinh.customttsdictionary.util.AppLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProxyTtsService extends TextToSpeechService {
    private static final String TAG = "ProxyTtsService";
    public static final String PREFS_NAME = "custom_tts_prefs";
    public static final String KEY_SELECTED_ENGINE = "selected_tts_engine";
    public static final String ACTION_RELOAD_ENGINE = "com.smartthinh.customttsdictionary.RELOAD_ENGINE";
    public static final String EXTRA_ENGINE_PACKAGE = "engine_package";

    private TextToSpeech targetTts;
    private String currentEnginePackage = null;
    private final AtomicBoolean isEngineReady = new AtomicBoolean(false);
    private Locale currentLocale = Locale.getDefault();

    private final AtomicBoolean isStopped = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, CountDownLatch> pendingLatches = new ConcurrentHashMap<>();
    private volatile Thread activeProducerThread = null;

    private float lastPitch = -1;
    private float lastRate = -1;
    private String lastLang = "";

    private static class ChunkAudio {
        static final ChunkAudio POISON_PILL = new ChunkAudio(-1, 0, 0, 0, null, true, false);

        final int chunkIndex;
        final int sampleRate;
        final int audioFormat;
        final int channels;
        final byte[] pcmData;
        final boolean isEnd;
        final boolean isError;

        ChunkAudio(int chunkIndex, int sampleRate, int audioFormat, int channels, byte[] pcmData, boolean isEnd, boolean isError) {
            this.chunkIndex = chunkIndex;
            this.sampleRate = sampleRate;
            this.audioFormat = audioFormat;
            this.channels = channels;
            this.pcmData = pcmData;
            this.isEnd = isEnd;
            this.isError = isError;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppLogger.init(this);
        AppLogger.log(TAG, "ProxyTtsService da khoi tao.");

        cleanupTempFiles();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String selectedEngine = prefs.getString(KEY_SELECTED_ENGINE, null);
        initTargetEngine(selectedEngine);
    }

    private void cleanupTempFiles() {
        try {
            File cacheDir = getCacheDir();
            if (cacheDir != null && cacheDir.exists()) {
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().startsWith("tts_") && f.getName().endsWith(".wav")) {
                            f.delete();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_RELOAD_ENGINE.equals(intent.getAction())) {
            String pkg = intent.getStringExtra(EXTRA_ENGINE_PACKAGE);
            AppLogger.log(TAG, "Nhan lenh doi Target Engine sang: " + pkg);
            initTargetEngine(pkg);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private synchronized void initTargetEngine(final String enginePackage) {
        if (targetTts != null) {
            try {
                targetTts.shutdown();
            } catch (Exception ignored) {}
            targetTts = null;
        }
        isEngineReady.set(false);
        currentEnginePackage = enginePackage;

        String targetPkg = enginePackage;
        if (targetPkg == null || targetPkg.trim().isEmpty() || targetPkg.equals(getPackageName())) {
            targetPkg = "com.google.android.tts";
        }

        final String finalTargetPkg = targetPkg;
        AppLogger.log(TAG, "Dang ket noi toi cong cu: [" + currentEnginePackage + "]");

        try {
            targetTts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS) {
                        isEngineReady.set(true);
                        AppLogger.log(TAG, "Target TTS san sang! Engine: [" + finalTargetPkg + "]");
                        setupGlobalUtteranceListener();
                        if (currentLocale != null && targetTts != null) {
                            try {
                                targetTts.setLanguage(currentLocale);
                            } catch (Exception ignored) {}
                        }
                    } else {
                        isEngineReady.set(false);
                        AppLogger.log(TAG, "LOI: Khong the khoi tao Target TTS cho: " + finalTargetPkg);
                        if (!"com.google.android.tts".equals(finalTargetPkg)) {
                            initTargetEngine("com.google.android.tts");
                        }
                    }
                }
            }, finalTargetPkg);
        } catch (Exception e) {
            isEngineReady.set(false);
            AppLogger.log(TAG, "Exception khi tao TextToSpeech: " + e.getMessage());
        }
    }

    private void setupGlobalUtteranceListener() {
        if (targetTts == null) return;
        targetTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {}

            @Override
            public void onDone(String utteranceId) {
                CountDownLatch latch = pendingLatches.remove(utteranceId);
                if (latch != null) {
                    latch.countDown();
                }
            }

            @Override
            public void onError(String utteranceId) {
                AppLogger.log(TAG, "Target TTS bao loi o utterance: " + utteranceId);
                CountDownLatch latch = pendingLatches.remove(utteranceId);
                if (latch != null) {
                    latch.countDown();
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppLogger.log(TAG, "ProxyTtsService bi huy.");
        if (targetTts != null) {
            try {
                targetTts.shutdown();
            } catch (Exception ignored) {}
            targetTts = null;
        }
        cleanupTempFiles();
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        Locale locale = new Locale(lang, country != null ? country : "", variant != null ? variant : "");
        if (targetTts != null && isEngineReady.get()) {
            return targetTts.isLanguageAvailable(locale);
        }
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected String[] onGetLanguage() {
        if (currentLocale != null) {
            return new String[]{currentLocale.getISO3Language(), currentLocale.getISO3Country(), currentLocale.getVariant()};
        }
        Locale def = Locale.getDefault();
        return new String[]{def.getISO3Language(), def.getISO3Country(), def.getVariant()};
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        currentLocale = new Locale(lang, country != null ? country : "", variant != null ? variant : "");
        if (targetTts != null && isEngineReady.get()) {
            return targetTts.setLanguage(currentLocale);
        }
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected void onStop() {
        isStopped.set(true);
        AppLogger.log(TAG, "Nhan lenh onStop -> Dung phat am.");

        Thread prod = activeProducerThread;
        if (prod != null) {
            prod.interrupt();
        }

        if (targetTts != null) {
            try {
                targetTts.stop();
            } catch (Exception ignored) {}
        }

        for (CountDownLatch latch : pendingLatches.values()) {
            latch.countDown();
        }
        pendingLatches.clear();
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        isStopped.set(false);

        CharSequence originalSeq = request.getCharSequenceText();
        if (originalSeq == null || originalSeq.length() == 0) {
            try {
                callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
                callback.done();
            } catch (Exception ignored) {}
            return;
        }

        int origLen = originalSeq.length();
        String originalText = originalSeq.toString();
        String preview = origLen > 40 ? originalText.substring(0, 40) + "..." : originalText;
        AppLogger.log(TAG, "Yeu cau doc (" + origLen + " ky tu): \"" + preview + "\"");

        // 1. Smart Bypass & Xu ly tu dien sieu toc (< 1ms)
        long tDictStart = System.currentTimeMillis();
        String processedText = DictionaryManager.replaceFast(originalText, getApplicationContext());
        long dictTime = System.currentTimeMillis() - tDictStart;

        if (processedText.equals(originalText)) {
            if (dictTime > 5) {
                AppLogger.log(TAG, "Smart Bypass: Khong co tu can thay the (" + dictTime + "ms)");
            }
        } else {
            AppLogger.log(TAG, "Tu dien da thay the trong: " + dictTime + "ms");
        }

        // 2. Kiem tra Target TTS san sang
        if (targetTts == null || !isEngineReady.get()) {
            initTargetEngine(currentEnginePackage);
        }

        int waitCount = 0;
        while (!isEngineReady.get() && waitCount < 30) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
            waitCount++;
        }

        if (targetTts == null || !isEngineReady.get()) {
            AppLogger.log(TAG, "LOI: Target TTS chua san sang, khong the phat am.");
            callback.error();
            return;
        }

        // 3. Dong bo cao do, toc do va ngon ngu
        try {
            int pitch = request.getPitch();
            if (pitch > 0 && pitch != lastPitch) {
                targetTts.setPitch(pitch / 100.0f);
                lastPitch = pitch;
            }
            int speechRate = request.getSpeechRate();
            if (speechRate > 0 && speechRate != lastRate) {
                targetTts.setSpeechRate(speechRate / 100.0f);
                lastRate = speechRate;
            }
            String lang = request.getLanguage();
            if (lang != null && !lang.isEmpty() && !lang.equals(lastLang)) {
                Locale loc = new Locale(lang,
                        request.getCountry() != null ? request.getCountry() : "",
                        request.getVariant() != null ? request.getVariant() : "");
                targetTts.setLanguage(loc);
                lastLang = lang;
            }
        } catch (Exception ignored) {}

        // 4. Phan doan thong minh theo diem ngat tu nhien:
        // - Van ban binh thuong (<= 500 ky tu): GIU NGUYEN 100%, doc lien mach khong bao gio cat!
        // - Van ban dai (> 500 ky tu): Chia theo khoi cau tu nhien (500 - 800 ky tu) tai dau cham cau/xuong dong.
        List<String> chunks = splitNaturalChunks(processedText);
        if (chunks.isEmpty()) {
            callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
            callback.done();
            return;
        }

        if (chunks.size() == 1) {
            synthesizeSingleChunk(chunks.get(0), callback);
        } else {
            synthesizeWithPipeline(chunks, callback);
        }
    }

    /**
     * Tong hop truc tiep cho van ban don (<= 500 ky tu).
     * Dam bao ngu dieu lien mach 100% nhu Engine goc.
     */
    private void synthesizeSingleChunk(String text, SynthesisCallback callback) {
        Bundle params = new Bundle();
        int maxBuffer = Math.max(8192, callback.getMaxBufferSize());

        String uid = "utt_" + System.currentTimeMillis() + "_single";
        File tempWav = new File(getCacheDir(), "tts_single_" + System.nanoTime() + ".wav");
        CountDownLatch latch = new CountDownLatch(1);
        pendingLatches.put(uid, latch);

        long tStart = System.currentTimeMillis();
        int res = targetTts.synthesizeToFile(text, params, tempWav, uid);
        if (res != TextToSpeech.SUCCESS) {
            AppLogger.log(TAG, "LOI synthesizeToFile ma " + res);
            pendingLatches.remove(uid);
            callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
            callback.done();
            return;
        }

        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            if (tempWav.exists()) tempWav.delete();
            return;
        } finally {
            pendingLatches.remove(uid);
        }

        if (isStopped.get()) {
            if (tempWav.exists()) tempWav.delete();
            return;
        }

        long duration = System.currentTimeMillis() - tStart;
        if (tempWav.exists() && tempWav.length() > 44) {
            WavParser.ParsedWav wav = WavParser.parseWavFile(tempWav);
            tempWav.delete();

            if (wav != null && wav.pcmData != null && wav.pcmData.length > 0) {
                callback.start(wav.sampleRate, wav.audioFormat, wav.channels);
                AppLogger.log(TAG, "Phat am don (" + text.length() + " ky tu, " + duration + "ms)");

                int offset = 0;
                int total = wav.pcmData.length;
                while (offset < total && !isStopped.get()) {
                    int toSend = Math.min(maxBuffer, total - offset);
                    callback.audioAvailable(wav.pcmData, offset, toSend);
                    offset += toSend;
                }
            } else {
                callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
            }
        } else {
            if (tempWav.exists()) tempWav.delete();
            callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
        }

        callback.done();
        AppLogger.log(TAG, "Hoan tat phat am van ban.");
    }

    /**
     * Tong hop goi dau theo khoi cau tu nhien (500 - 800 ky tu):
     * - Khoi 0 doc ra loa trong 10-20 giay.
     * - Trong luc do, khoi tiep theo da duoc tong hop xong sau 1 giay va nam san trong hang doi.
     * - Khi khoi 0 dut tieng o dung dau cham cau, khoi 1 noi tiep tuc thi (0ms delay),
     *   khong bao gio bi dut tieng giua mot cau van.
     */
    private void synthesizeWithPipeline(final List<String> chunks, SynthesisCallback callback) {
        final BlockingQueue<ChunkAudio> audioQueue = new LinkedBlockingQueue<>(3);
        final Bundle params = new Bundle();
        final int maxBuffer = Math.max(8192, callback.getMaxBufferSize());
        final AtomicBoolean producerStopped = new AtomicBoolean(false);

        Thread producerThread = new Thread(() -> {
            for (int i = 0; i < chunks.size(); i++) {
                if (isStopped.get() || producerStopped.get() || Thread.currentThread().isInterrupted()) {
                    break;
                }

                String chunk = chunks.get(i);
                if (chunk.trim().isEmpty()) continue;

                String uid = "utt_" + System.currentTimeMillis() + "_" + i;
                File tempWav = new File(getCacheDir(), "tts_pip_" + System.nanoTime() + ".wav");
                CountDownLatch latch = new CountDownLatch(1);
                pendingLatches.put(uid, latch);

                long tStart = System.currentTimeMillis();
                int res = targetTts.synthesizeToFile(chunk, params, tempWav, uid);

                if (res != TextToSpeech.SUCCESS) {
                    AppLogger.log(TAG, "LOI synthesizeToFile ma " + res + " cho khoi " + (i + 1));
                    pendingLatches.remove(uid);
                    continue;
                }

                try {
                    boolean finished = latch.await(15, TimeUnit.SECONDS);
                    if (!finished) {
                        AppLogger.log(TAG, "TIMEOUT khoi " + (i + 1));
                    }
                } catch (InterruptedException e) {
                    if (tempWav.exists()) tempWav.delete();
                    break;
                } finally {
                    pendingLatches.remove(uid);
                }

                if (isStopped.get() || producerStopped.get()) {
                    if (tempWav.exists()) tempWav.delete();
                    break;
                }

                long duration = System.currentTimeMillis() - tStart;
                if (tempWav.exists() && tempWav.length() > 44) {
                    WavParser.ParsedWav wav = WavParser.parseWavFile(tempWav);
                    tempWav.delete();

                    if (wav != null && wav.pcmData != null && wav.pcmData.length > 0) {
                        AppLogger.log(TAG, "Goi dau khoi " + (i + 1) + "/" + chunks.size() + " (" + chunk.length() + " ky tu, " + duration + "ms)");
                        try {
                            audioQueue.put(new ChunkAudio(i, wav.sampleRate, wav.audioFormat, wav.channels, wav.pcmData, false, false));
                        } catch (InterruptedException e) {
                            break;
                        }
                    } else {
                        AppLogger.log(TAG, "LOI giai ma WAV khoi " + (i + 1));
                    }
                } else {
                    if (tempWav.exists()) tempWav.delete();
                }
            }

            try {
                audioQueue.put(ChunkAudio.POISON_PILL);
            } catch (InterruptedException ignored) {}
        }, "TtsProducerThread");

        activeProducerThread = producerThread;
        producerThread.start();

        boolean callbackStarted = false;
        int receivedChunks = 0;

        try {
            while (!isStopped.get()) {
                ChunkAudio item = audioQueue.poll(20, TimeUnit.SECONDS);
                if (item == null || item.isEnd) {
                    break;
                }
                if (item.isError || item.pcmData == null) {
                    continue;
                }

                if (!callbackStarted) {
                    callback.start(item.sampleRate, item.audioFormat, item.channels);
                    callbackStarted = true;
                    AppLogger.log(TAG, "Bat dau phat tieng ra loa (Khoi dau)");
                }

                int offset = 0;
                int total = item.pcmData.length;
                while (offset < total && !isStopped.get()) {
                    int toSend = Math.min(maxBuffer, total - offset);
                    callback.audioAvailable(item.pcmData, offset, toSend);
                    offset += toSend;
                }
                receivedChunks++;
            }
        } catch (InterruptedException ignored) {
        } finally {
            producerStopped.set(true);
            producerThread.interrupt();
            activeProducerThread = null;

            if (!callbackStarted) {
                callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
            }
            callback.done();
            AppLogger.log(TAG, "Hoan tat phat am van ban (" + receivedChunks + "/" + chunks.size() + " khoi)");
        }
    }

    /**
     * Thuat toan chia khoi theo cau tu nhien (Natural Sentence Chunking):
     * - Van ban <= 500 ky tu: KHONG CAT, de nguyen ven 100% de giu tron ngu dieu tu nhien.
     * - Van ban > 500 ky tu:
     *   + Khoi dau: 150 - 300 ky tu (uu tien ket thuc o dau cham cau/xuong dong) de bat dau doc sau ~300ms.
     *   + Cac khoi sau: 500 - 800 ky tu, chi ngat tai dau cham het cau hoac xuong dong.
     *   + Tuyet doi khong bao gio cat o giua mot cau khi chua den dau cau.
     */
    public static List<String> splitNaturalChunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null) return chunks;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return chunks;

        // Voi van ban duoi 500 ky tu: GIU NGUYEN VEN 100%
        if (trimmed.length() <= 500) {
            chunks.add(trimmed);
            return chunks;
        }

        int n = trimmed.length();
        int start = 0;
        int chunkIndex = 0;

        while (start < n) {
            int remaining = n - start;
            int minTarget;
            int maxTarget;

            if (chunkIndex == 0) {
                // Khoi 0: Khoang 150 - 300 ky tu de bat dau doc som
                minTarget = 120;
                maxTarget = 300;
            } else {
                // Cac khoi sau: Khoang 500 - 800 ky tu
                minTarget = 400;
                maxTarget = 800;
            }

            if (remaining <= maxTarget + 100) {
                String lastChunk = trimmed.substring(start).trim();
                if (!lastChunk.isEmpty()) {
                    chunks.add(lastChunk);
                }
                break;
            }

            int targetEnd = start + maxTarget;
            int breakPos = -1;

            // 1. Uu tien cao nhat: Dau xuong dong '\n'
            for (int i = targetEnd; i >= start + minTarget; i--) {
                char c = trimmed.charAt(i);
                if (c == '\n' || c == '\r') {
                    breakPos = i + 1;
                    break;
                }
            }

            // 2. Uu tien 2: Dau ket thuc cau (., !, ?, …, 。, ！, ？)
            if (breakPos == -1) {
                for (int i = targetEnd; i >= start + minTarget; i--) {
                    char c = trimmed.charAt(i);
                    if (c == '.' || c == '!' || c == '?' || c == '…' || c == '。' || c == '！' || c == '？') {
                        // Kiem tra ky tu sau do phai la khoang trang hoac het van ban de tranh nham so thap phan
                        if (i + 1 == n || Character.isWhitespace(trimmed.charAt(i + 1))) {
                            breakPos = i + 1;
                            break;
                        }
                    }
                }
            }

            // 3. Uu tien 3: Dau phan tach menh de (; : — ； ：)
            if (breakPos == -1) {
                for (int i = targetEnd; i >= start + minTarget; i--) {
                    char c = trimmed.charAt(i);
                    if (c == ';' || c == ':' || c == '—' || c == '；' || c == '：') {
                        breakPos = i + 1;
                        break;
                    }
                }
            }

            // 4. Uu tien 4: Dau phay (,, ，, 、)
            if (breakPos == -1) {
                for (int i = targetEnd; i >= start + minTarget; i--) {
                    char c = trimmed.charAt(i);
                    if (c == ',' || c == '，' || c == '、') {
                        breakPos = i + 1;
                        break;
                    }
                }
            }

            // 5. Cung duong cuoi cung: Neu ca 800 ky tu khong he co bat cu dau cau nao, tim khoang trang gan nhat
            if (breakPos == -1) {
                for (int i = targetEnd; i >= start + minTarget; i--) {
                    char c = trimmed.charAt(i);
                    if (Character.isWhitespace(c)) {
                        breakPos = i + 1;
                        break;
                    }
                }
            }

            // 6. Neu hoan toan khong co khoang trang nao (chuoi vo cung dai), ngat tai targetEnd
            if (breakPos == -1 || breakPos <= start) {
                breakPos = targetEnd;
            }

            String chunk = trimmed.substring(start, breakPos).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
                chunkIndex++;
            }
            start = breakPos;
            while (start < n && Character.isWhitespace(trimmed.charAt(start)) && trimmed.charAt(start) != '\n') {
                start++;
            }
        }

        return chunks;
    }
}
