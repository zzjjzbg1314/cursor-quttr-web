package com.example.cursorquitterweb.musicmv.support;

import java.util.UUID;

public final class IdUtils {
    private IdUtils() {
    }

    public static String token(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static String safeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "photo.jpg";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
