package com.example.cursorquitterweb.musicmv.service;

import java.util.*;

/** 发布验收必须绑定场景与引擎，并包含原片及换素材跨周期导出的成片证据。 */
final class BrowserExportAcceptance {
    static boolean passed(Map<String,Object> report, String templateId, String versionId,
                          String sceneHash, String parityOutputHash, double cycleSeconds) {
        try {
            Map<?,?> acceptance = map(report.get("browserExportAcceptance"));
            String fingerprint = hash(report.get("rendererFingerprint"));
            if (report.containsKey("rendererVersionWarning")
                    || !"browser-export-acceptance-v1".equals(acceptance.get("schemaVersion"))
                    || !templateId.equals(acceptance.get("templateId"))
                    || !versionId.equals(acceptance.get("versionId"))
                    || !hash(sceneHash).equals(acceptance.get("sceneManifestSha256"))
                    || !fingerprint.equals(acceptance.get("rendererFingerprint"))
                    || !Double.isFinite(cycleSeconds) || cycleSeconds <= 0) return false;
            Map<?,?> original = map(acceptance.get("original"));
            Map<?,?> replacement = map(acceptance.get("replacementLoop"));
            if (!run(original, cycleSeconds) || !run(replacement, cycleSeconds * 2)
                    || !hash(parityOutputHash).equals(original.get("outputSha256"))
                    || hash(original.get("outputSha256")).equals(hash(replacement.get("outputSha256")))
                    || hash(original.get("inputManifestSha256")).equals(hash(replacement.get("inputManifestSha256")))) return false;
            for (String check : Arrays.asList("photoReplacement", "selectedMusic", "selectedLyrics", "loopBoundary"))
                if (!"passed".equals(replacement.get(check))) return false;
            return true;
        } catch (RuntimeException invalid) { return false; }
    }
    private static boolean run(Map<?,?> value, double minimumDuration) {
        if (!"completed".equals(value.get("status")) || !"exact".equals(value.get("semanticIntegrity"))
                || !number(value.get("videoEncodeCount"), 1) || !number(value.get("materializedIntermediateVideoCount"), 0)
                || !Boolean.TRUE.equals(value.get("decoded"))
                || !hash(value.get("outputSha256")).equals(hash(value.get("downloadedSha256")))) return false;
        hash(value.get("inputManifestSha256"));
        double duration = finite(value.get("durationSeconds")), fps = finite(value.get("fps"));
        double frames = finite(value.get("completedFrameCount"));
        return fps > 0 && fps <= 120 && duration + 1 / fps >= minimumDuration
                && frames > 0 && frames == Math.rint(frames) && Math.abs(frames - duration * fps) <= 1;
    }
    private static boolean number(Object value, int expected) { return finite(value) == expected; }
    private static double finite(Object value) {
        if (!(value instanceof Number) || !Double.isFinite(((Number)value).doubleValue())) throw new IllegalArgumentException();
        return ((Number)value).doubleValue();
    }
    private static String hash(Object value) {
        if (!(value instanceof String) || !((String)value).matches("[a-f0-9]{64}")) throw new IllegalArgumentException();
        return (String)value;
    }
    private static Map<?,?> map(Object value) { if (!(value instanceof Map)) throw new IllegalArgumentException(); return (Map<?,?>)value; }
    private BrowserExportAcceptance() {}
}
