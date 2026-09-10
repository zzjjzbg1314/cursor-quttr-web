package com.example.cursorquitterweb.musicmv.dto;
import org.junit.jupiter.api.Test;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import static org.junit.jupiter.api.Assertions.*;
class BrowserRenderTelemetryTest {
    private BrowserRenderTelemetry valid() {
        BrowserRenderTelemetry t=new BrowserRenderTelemetry();t.setSemanticIntegrity("exact");t.setVideoEncodeCount(1);
        t.setMaterializedIntermediateVideoCount(0);t.setCompletedFrameCount(428);t.setElapsedSeconds(11.8);return t;
    }
    @Test void acceptsOptionalPhasesAndRejectsInvalidCountersAndDurations() {
        try(ValidatorFactory f=Validation.buildDefaultValidatorFactory()) {
            BrowserRenderTelemetry t=valid();assertTrue(f.getValidator().validate(t).isEmpty());
            t.setVideoEncodeCount(2);assertFalse(f.getValidator().validate(t).isEmpty());t.setVideoEncodeCount(1);
            t.setElapsedSeconds(Double.NaN);assertFalse(f.getValidator().validate(t).isEmpty());t.setElapsedSeconds(-1.0);assertFalse(f.getValidator().validate(t).isEmpty());
        }
    }
    @Test void validatesNestedPhasesAndPreservesOldRequests() {
        try(ValidatorFactory f=Validation.buildDefaultValidatorFactory()) {
            BrowserRenderOutputRequest r=new BrowserRenderOutputRequest();r.setAttemptId("a");r.setLeaseToken("b");r.setSha256(new String(new char[64]).replace('\0','a'));r.setSizeBytes(1L);r.setContentType("video/mp4");r.setDurationSeconds(1.0);
            assertTrue(f.getValidator().validate(r).isEmpty());
            BrowserRenderTelemetry t=valid();t.setPhaseSeconds(new BrowserRenderTelemetry.PhaseSeconds());r.setRenderValidation(t);
            assertFalse(f.getValidator().validate(r).isEmpty());
        }
    }
}
