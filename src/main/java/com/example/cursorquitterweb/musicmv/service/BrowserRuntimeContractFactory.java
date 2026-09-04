package com.example.cursorquitterweb.musicmv.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BrowserRuntimeContractFactory {
    private BrowserRuntimeContractFactory() {}

    static Map<String, Object> create(
            Map<String, Object> scene,
            Object sceneManifestSha256,
            Map<String, Object> textOverrides,
            List<Map<String, Object>> slotBindings,
            List<Map<String, Object>> resources,
            Map<String, Object> runtimePackage,
            Map<String, Object> outputVideo
    ) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("scene", scene);
        result.put("sceneManifestSha256", sceneManifestSha256);
        result.put("textOverrides", textOverrides == null
                ? Collections.<String, String>emptyMap() : textOverrides);
        result.put("slotBindings", slotBindings);
        result.put("resources", resources);
        if (runtimePackage != null && !runtimePackage.isEmpty()) {
            result.put("runtimePackage", runtimePackage);
        }
        result.put("outputVideo", outputVideo);
        return result;
    }
}
