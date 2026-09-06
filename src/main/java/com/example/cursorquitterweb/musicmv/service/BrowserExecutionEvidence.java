package com.example.cursorquitterweb.musicmv.service;

import java.io.IOException;
import java.util.*;

/** 校验浏览器输出流水线事件报告；来源为浏览器实报，不冒充服务端独立测量。 */
final class BrowserExecutionEvidence {
    static Map<String, Object> validate(Map<String, Object> input) throws IOException {
        if (!"browser-render-execution-v1".equals(input.get("schemaVersion"))
                || !"instrumented_browser_output_pipeline".equals(input.get("observationScope")))
            throw new IOException("浏览器执行证据版本或观测范围无效");
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", input.get("schemaVersion"));
        result.put("observationScope", input.get("observationScope"));
        for (String key : Arrays.asList("videoEncodeCount", "submittedVideoFrameCount", "encodedVideoPacketCount",
                "materializedIntermediateVideoCount", "writerSidecarCount", "finalVideoCount", "finalVideoBytes")) {
            Object raw = input.get(key);
            if (!(raw instanceof Number)) throw new IOException("执行证据缺少数值：" + key);
            double value = ((Number) raw).doubleValue();
            if (!Double.isFinite(value) || value < 0 || value > 9007199254740991d || value != Math.rint(value))
                throw new IOException("执行证据数值无效：" + key);
            result.put(key, Long.valueOf((long) value));
        }
        if (count(result,"videoEncodeCount") < 1 || count(result,"submittedVideoFrameCount") < 1
                || count(result,"encodedVideoPacketCount") < 1 || count(result,"finalVideoCount") != 1
                || count(result,"finalVideoBytes") < 1) throw new IOException("浏览器执行证据不完整");
        result.put("provenance", "browser_reported_runtime_events");
        return result;
    }
    static long count(Map<String,Object> data, String key) { return ((Number)data.get(key)).longValue(); }
}
