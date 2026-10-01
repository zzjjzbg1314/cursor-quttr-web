package com.example.cursorquitterweb.musicmv.aimusic;

import java.util.Arrays;
import com.example.cursorquitterweb.musicmv.support.ApiException;

final class SubmissionSafety {
    private SubmissionSafety() { }
    static boolean definitelyRejected(ApiException error) {
        return Arrays.asList("SUNOAPI_NOT_CONFIGURED", "SUNOAPI_CALLBACK_SECURITY_NOT_CONFIGURED",
                "KIE_NOT_CONFIGURED", "SUNOAPI_CREDITS_EXHAUSTED", "KIE_CREDITS_EXHAUSTED",
                "SUNOAPI_RATE_LIMITED", "KIE_RATE_LIMITED").contains(error.getCode());
    }
}
