package com.bunny.rtt_api.service.realtime;

import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Service
public class VadService {

    // Default RMS threshold for speech activity detection (below this is silence/ambient noise)
    public static final double DEFAULT_RMS_THRESHOLD = 250.0;

    /**
     * Calculates the Root Mean Square (RMS) energy of 16-bit PCM audio samples.
     *
     * @param pcm16 byte buffer containing PCM16 audio
     * @return RMS value
     */
    public double calculateRms(ByteBuffer pcm16) {
        if (pcm16 == null || pcm16.remaining() < 2) {
            return 0.0;
        }

        ByteBuffer readOnly = pcm16.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        long sumSquares = 0;
        int sampleCount = readOnly.remaining() / 2;

        while (readOnly.remaining() >= 2) {
            short sample = readOnly.getShort();
            sumSquares += (long) sample * sample;
        }

        if (sampleCount == 0) return 0.0;
        return Math.sqrt((double) sumSquares / sampleCount);
    }

    /**
     * Checks if the PCM16 audio frame contains speech above the RMS threshold.
     */
    public boolean isSpeech(ByteBuffer pcm16, double thresholdRms) {
        return calculateRms(pcm16) >= thresholdRms;
    }

    public boolean isSpeech(ByteBuffer pcm16) {
        return isSpeech(pcm16, DEFAULT_RMS_THRESHOLD);
    }
}
