package com.aperturemapslib.core;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class CTMPatternDebug {

    private static final Set<String> LOGGED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Map<String, AtomicInteger> COUNTS = new ConcurrentHashMap<String, AtomicInteger>();
    private static final int MAX_SAMPLES_PER_SPRITE = 40;

    private CTMPatternDebug() {
    }

    public static void logPatternSample(String spriteName, float f6, float f7, float f8, float f9) {
        if (!StitcherHolderTransformer.isDebugPatternSamplesEnabled()) {
            return;
        }

        if (spriteName == null) {
            return;
        }

        AtomicInteger counter = COUNTS.computeIfAbsent(spriteName, k -> new AtomicInteger(0));
        int idx = counter.getAndIncrement();
        if (idx >= MAX_SAMPLES_PER_SPRITE) {
            return;
        }

        String key = spriteName + "|" + round3(f6) + "|" + round3(f7) + "|" + round3(f8) + "|" + round3(f9);
        if (!LOGGED.add(key)) {
            return;
        }

        System.out.println("[ApertureMapsLib] Pattern sample "
                + spriteName
                + " #" + idx
                + " -> stepU=" + f6
                + ", stepV=" + f7
                + ", offU=" + f8
                + ", offV=" + f9);
    }

    private static float round3(float value) {
        return Math.round(value * 1000.0f) / 1000.0f;
    }
}
