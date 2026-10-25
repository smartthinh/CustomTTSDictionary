package com.smartthinh.customttsdictionary.service;

import android.media.AudioFormat;
import android.speech.tts.SynthesisCallback;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class WavParser {

    public static class ParsedWav {
        public final int sampleRate;
        public final int audioFormat;
        public final int channels;
        public final byte[] pcmData;

        public ParsedWav(int sampleRate, int audioFormat, int channels, byte[] pcmData) {
            this.sampleRate = sampleRate;
            this.audioFormat = audioFormat;
            this.channels = channels;
            this.pcmData = pcmData;
        }
    }

    public static ParsedWav parseWavFile(File wavFile) {
        if (wavFile == null || !wavFile.exists() || wavFile.length() < 44) {
            return null;
        }

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(wavFile);
            byte[] header = new byte[12];
            int read = fis.read(header);
            if (read < 12) return null;

            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F' ||
                header[8] != 'W' || header[9] != 'A' || header[10] != 'V' || header[11] != 'E') {
                return null;
            }

            int channels = 1;
            int sampleRate = 22050;
            int bitsPerSample = 16;
            int dataSize = 0;
            boolean foundData = false;

            byte[] chunkHeader = new byte[8];
            while (fis.read(chunkHeader) == 8) {
                String chunkId = new String(chunkHeader, 0, 4);
                int chunkSize = (chunkHeader[4] & 0xFF) |
                               ((chunkHeader[5] & 0xFF) << 8) |
                               ((chunkHeader[6] & 0xFF) << 16) |
                               ((chunkHeader[7] & 0xFF) << 24);

                if ("fmt ".equals(chunkId)) {
                    byte[] fmtData = new byte[chunkSize];
                    int fmtRead = fis.read(fmtData);
                    if (fmtRead >= 16) {
                        channels = (fmtData[2] & 0xFF) | ((fmtData[3] & 0xFF) << 8);
                        sampleRate = (fmtData[4] & 0xFF) |
                                    ((fmtData[5] & 0xFF) << 8) |
                                    ((fmtData[6] & 0xFF) << 16) |
                                    ((fmtData[7] & 0xFF) << 24);
                        bitsPerSample = (fmtData[14] & 0xFF) | ((fmtData[15] & 0xFF) << 8);
                    }
                } else if ("data".equals(chunkId)) {
                    dataSize = chunkSize;
                    foundData = true;
                    break;
                } else {
                    long skipped = fis.skip(chunkSize);
                    if (skipped < chunkSize) break;
                }
            }

            if (!foundData || sampleRate <= 0) return null;

            int audioFormat = (bitsPerSample == 8) ? AudioFormat.ENCODING_PCM_8BIT : AudioFormat.ENCODING_PCM_16BIT;

            int sizeToRead = dataSize > 0 ? dataSize : (int)(wavFile.length() - 44);
            if (sizeToRead <= 0) return null;

            byte[] pcm = new byte[sizeToRead];
            int totalRead = 0;
            while (totalRead < sizeToRead) {
                int r = fis.read(pcm, totalRead, sizeToRead - totalRead);
                if (r <= 0) break;
                totalRead += r;
            }

            return new ParsedWav(sampleRate, audioFormat, channels, pcm);
        } catch (Exception e) {
            return null;
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (IOException ignored) {}
            }
        }
    }

    public static boolean streamWavToCallback(File wavFile, SynthesisCallback callback) {
        ParsedWav wav = parseWavFile(wavFile);
        if (wav == null || wav.pcmData == null || wav.pcmData.length == 0) {
            return false;
        }

        int maxBufferSize = callback.getMaxBufferSize();
        if (maxBufferSize <= 0) maxBufferSize = 8192;

        callback.start(wav.sampleRate, wav.audioFormat, wav.channels);

        int offset = 0;
        int total = wav.pcmData.length;
        while (offset < total) {
            int toSend = Math.min(maxBufferSize, total - offset);
            callback.audioAvailable(wav.pcmData, offset, toSend);
            offset += toSend;
        }

        callback.done();
        return true;
    }
}
