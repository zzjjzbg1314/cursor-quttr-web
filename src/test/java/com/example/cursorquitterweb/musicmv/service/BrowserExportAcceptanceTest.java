package com.example.cursorquitterweb.musicmv.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrowserExportAcceptanceTest {
    static String hash(char value) { return String.join("", Collections.nCopies(64, String.valueOf(value))); }
    static Map<String,Object> row(Object... entries) { Map<String,Object> out=new LinkedHashMap<>(); for(int i=0;i<entries.length;i+=2)out.put((String)entries[i],entries[i+1]);return out; }
    static Map<String,Object> run(boolean replacement) {
        return row("status","completed","semanticIntegrity","exact","videoEncodeCount",1,"materializedIntermediateVideoCount",0,
                "decoded",true,"outputSha256",hash(replacement?'b':'a'),"downloadedSha256",hash(replacement?'b':'a'),
                "inputManifestSha256",hash(replacement?'e':'f'),"durationSeconds",replacement?20d:10d,"fps",30,
                "completedFrameCount",replacement?600:300,"photoReplacement","passed","selectedMusic","passed",
                "selectedLyrics","passed","loopBoundary","passed");
    }
    static Map<String,Object> report() {
        return row("rendererFingerprint",hash('f'),"browserExportAcceptance",row("schemaVersion","browser-export-acceptance-v1",
                "templateId","tpl_1","versionId","tplver_1","sceneManifestSha256",hash('c'),"rendererFingerprint",hash('f'),
                "original",run(false),"replacementLoop",run(true)));
    }
    static boolean passed(Map<String,Object> report) { return BrowserExportAcceptance.passed(report,"tpl_1","tplver_1",hash('c'),hash('a'),10d); }
    @Test void acceptsCompleteBoundEvidenceWithoutMutatingIt() {
        Map<String,Object> report=report();String before=report.toString();assertTrue(passed(report));assertEquals(before,report.toString());
    }
    @Test @SuppressWarnings("unchecked") void rejectsStaleIdentityAndMissingEvidence() {
        assertFalse(passed(Collections.emptyMap()));
        for(String field:Arrays.asList("templateId","versionId","sceneManifestSha256","rendererFingerprint","schemaVersion","original","replacementLoop")) {
            Map<String,Object> report=report();((Map<String,Object>)report.get("browserExportAcceptance")).remove(field);assertFalse(passed(report),field);
        }
        Map<String,Object> report=report();report.put("rendererVersionWarning","stale");assertFalse(passed(report));
        report=report();report.put("rendererFingerprint",hash('e'));assertFalse(passed(report));
    }
    @Test @SuppressWarnings("unchecked") void rejectsIncompleteDownloadLoopReplacementAndEncoding() {
        Map<String,Object> mutations=row("status","failed","semanticIntegrity","approximate","videoEncodeCount",2,
                "materializedIntermediateVideoCount",1,"decoded",false,"downloadedSha256",hash('d'),"durationSeconds",10,
                "completedFrameCount",599-10,"photoReplacement","failed","selectedMusic","failed","selectedLyrics","failed",
                "loopBoundary","failed","inputManifestSha256",hash('f'),"outputSha256",hash('a'),"fps",Double.NaN);
        for(Map.Entry<String,Object> mutation:mutations.entrySet()) {
            Map<String,Object> report=report();Map<String,Object> acceptance=(Map<String,Object>)report.get("browserExportAcceptance");
            ((Map<String,Object>)acceptance.get("replacementLoop")).put(mutation.getKey(),mutation.getValue());
            assertFalse(passed(report),mutation.getKey());
        }
    }
}
