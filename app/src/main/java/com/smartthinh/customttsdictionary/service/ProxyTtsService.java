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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
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

    private volatile CountDownLatch currentLatch = null;
    private final AtomicBoolean isStopped = new AtomicBoolean(false);

    private float lastPitch = -1;
    private float lastRate = -1;
    private String lastLang = "";

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String selectedEngine = prefs.getString(KEY_SELECTED_ENGINE, null);
        initTargetEngine(selectedEngine);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_RELOAD_ENGINE.equals(intent.getAction())) {
            String pkg = intent.getStringExtra(EXTRA_ENGINE_PACKAGE);
            Log.d(TAG, "Nhan lenh doi engine sang: " + pkg);
            initTargetEngine(pkg);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private synchronized void initTargetEngine(String targetPackage) {
        if (targetPackage == null) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            targetPackage = prefs.getString(KEY_SELECTED_ENGINE, null);
        }

        if (targetTts != null && isEngineReady.get()) {
            if (targetPackage == null && currentEnginePackage == null) {
                return;
            }
            if (targetPackage != null && targetPackage.equals(currentEnginePackage)) {
                return;
            }
        }

        if (targetTts != null) {
            try {
                targetTts.shutdown();
            } catch (Exception ignored) {}
            targetTts = null;
        }
        isEngineReady.set(false);
        currentEnginePackage = targetPackage;
        lastPitch = -1;
        lastRate = -1;
        lastLang = "";

        Log.d(TAG, "Ket noi toi cong cu: [" + currentEnginePackage + "]");

        final String targetPkg = currentEnginePackage;
        TextToSpeech.OnInitListener listener = status -> {
            if (status == TextToSpeech.SUCCESS) {
                isEngineReady.set(true);
                Log.d(TAG, "Target TTS san sang! Goi dang chon: [" + targetPkg + "]");
                if (targetTts != null && currentLocale != null) {
                    try {
                        targetTts.setLanguage(currentLocale);
                    } catch (Exception ignored) {}
                }
            } else {
                isEngineReady.set(false);
                Log.e(TAG, "Loi khoi tao Target TTS cho: " + targetPkg);
            }
        };

        try {
            if (currentEnginePackage != null && !currentEnginePackage.isEmpty()) {
                targetTts = new TextToSpeech(getApplicationContext(), listener, currentEnginePackage);
            } else {
                targetTts = new TextToSpeech(getApplicationContext(), listener);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception khi tao TextToSpeech", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (targetTts != null) {
            try {
                targetTts.shutdown();
            } catch (Exception ignored) {}
            targetTts = null;
        }
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
        if (targetTts != null) {
            try {
                targetTts.stop();
            } catch (Exception ignored) {}
        }
        CountDownLatch latch = currentLatch;
        if (latch != null) {
            latch.countDown();
        }
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

        // 1. Xu ly tu dien dang chon trong RAM (< 0.1ms)
        String processedText = DictionaryManager.replaceFast(originalSeq.toString(), getApplicationContext());

        if (targetTts == null || !isEngineReady.get()) {
            initTargetEngine(null);
        }

        int waitCount = 0;
        while (!isEngineReady.get() && waitCount < 30) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
            waitCount++;
        }

        if (targetTts == null || !isEngineReady.get()) {
            Log.e(TAG, "Target TTS chua san sang.");
            callback.error();
            return;
        }

        // 2. Dong bo cao do, toc do va ngon ngu
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

        // 3. Pipelined Streaming: Phat am cau 1 trong 30ms, cac cau sau tong hop ngam
        List<String> chunks = splitIntoChunks(processedText);

        boolean callbackStarted = false;
        int maxBuffer = callback.getMaxBufferSize();
        if (maxBuffer <= 0) maxBuffer = 8192;
        Bundle params = new Bundle();

        for (int i = 0; i < chunks.size(); i++) {
            if (isStopped.get()) break;

            String chunk = chunks.get(i);
            if (chunk.trim().isEmpty()) continue;

            File chunkFile = new File(getCacheDir(), "stream_buf_" + (i % 2) + ".wav");
            final CountDownLatch latch = new CountDownLatch(1);
            currentLatch = latch;
            final String uid = "chk_" + i + "_" + System.nanoTime();
            final AtomicBoolean synthSuccess = new AtomicBoolean(false);

            targetTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {}

                @Override
                public void onDone(String utteranceId) {
                    if (uid.equals(utteranceId)) {
                        synthSuccess.set(true);
                        latch.countDown();
                    }
                }

                @Override
                public void onError(String utteranceId) {
                    if (uid.equals(utteranceId)) {
                        synthSuccess.set(false);
                        latch.countDown();
                    }
                }
            });

            int res = targetTts.synthesizeToFile(chunk, params, chunkFile, uid);
            if (res == TextToSpeech.SUCCESS) {
                try {
                    latch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    targetTts.stop();
                    break;
                }
            }

            if (isStopped.get()) break;

            if (synthSuccess.get() && chunkFile.exists() && chunkFile.length() > 44) {
                WavParser.ParsedWav wav = WavParser.parseWavFile(chunkFile);
                if (wav != null && wav.pcmData != null && wav.pcmData.length > 0) {
                    if (!callbackStarted) {
                        callback.start(wav.sampleRate, wav.audioFormat, wav.channels);
                        callbackStarted = true;
                    }

                    int offset = 0;
                    int totalBytes = wav.pcmData.length;
                    while (offset < totalBytes && !isStopped.get()) {
                        int toSend = Math.min(maxBuffer, totalBytes - offset);
                        callback.audioAvailable(wav.pcmData, offset, toSend);
                        offset += toSend;
                    }
                }
            }
        }

        if (!callbackStarted) {
            callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
        }
        callback.done();
    }

    public static List<String> splitIntoChunks(String text) {
        List<String> list = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return list;
        }

        String[] sentences = text.split("(?<=[.!?;\n\r。！？])(?![0-9])\\s+");
        for (String s : sentences) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.length() > 100) {
                String[] clauses = trimmed.split("(?<=[,，、—:])\\s+");
                for (String c : clauses) {
                    String clauseTrimmed = c.trim();
                    if (clauseTrimmed.isEmpty()) continue;

                    if (clauseTrimmed.length() > 120) {
                        int start = 0;
                        while (start < clauseTrimmed.length()) {
                            int end = Math.min(start + 80, clauseTrimmed.length());
                            if (end < clauseTrimmed.length()) {
                                int space = clauseTrimmed.lastIndexOf(' ', end);
                                if (space > start + 30) {
                                    end = space;
                                }
                            }
                            String piece = clauseTrimmed.substring(start, end).trim();
                            if (!piece.isEmpty()) list.add(piece);
                            start = end;
                            while (start < clauseTrimmed.length() && clauseTrimmed.charAt(start) == ' ') start++;
                        }
                    } else {
                        list.add(clauseTrimmed);
                    }
                }
            } else {
                list.add(trimmed);
            }
        }

        if (list.isEmpty()) {
            list.add(text);
        }
        return list;
    }
}
