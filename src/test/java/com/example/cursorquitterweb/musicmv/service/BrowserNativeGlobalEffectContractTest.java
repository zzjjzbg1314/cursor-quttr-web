package com.example.cursorquitterweb.musicmv.service;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BrowserNativeGlobalEffectContractTest {
    Map<String,Object> descriptor(){Map<String,Object> d=BrowserNativeRuntimeContractTest.descriptor();d.put("schemaVersion","browser-native-scene-runtime-v4");d.put("videoAudioPolicy","external_music_only");d.put("globalEffectPolicy","original_tracks_v1");return d;}
    Map<String,Object> effect()throws Exception{return new ObjectMapper().readValue("{\"effectId\":\"s\",\"resourceId\":\"effect\",\"targetStartSeconds\":1,\"targetDurationSeconds\":2,\"nativeGlobalSource\":{\"schemaVersion\":\"native-global-effect-source-v1\",\"segmentId\":\"s\",\"materialId\":\"m\",\"type\":\"filter\",\"targetStartRaw\":\"1000000\",\"targetDurationRaw\":\"2000000\",\"sourceStartRaw\":\"0\",\"sourceDurationRaw\":\"0\",\"offsetRaw\":\"0\",\"renderIndex\":10000,\"trackRenderIndex\":7,\"visible\":true,\"value\":0,\"adjustParams\":[],\"commonKeyframes\":[]}}",Map.class);}
    @Test void preservesPolicyAndChecksEverySource()throws Exception {
        Map<String,Object> d=descriptor(),validated=BrowserNativeRuntimeContract.validate(d,Collections.singleton("effect"));assertEquals(d,validated);
        assertEquals(Collections.singleton("s"),BrowserNativeGlobalEffectContract.validate(Collections.singletonMap("postEffects",Collections.singletonList(effect())),validated));
        d.put("globalEffectPolicy","unknown");assertThrows(RuntimeException.class,()->BrowserNativeRuntimeContract.validate(d,Collections.singleton("effect")));
    }
    @Test void missingSourceWrongClockAndUnknownKeyframesStayBlocked()throws Exception {
        Map<String,Object> e=effect(),scene=Collections.singletonMap("postEffects",Collections.singletonList(e));
        e.put("targetStartSeconds",1.1);assertThrows(RuntimeException.class,()->BrowserNativeGlobalEffectContract.validate(scene,descriptor()));
        e.put("targetStartSeconds",1);Map<String,Object> source=(Map<String,Object>)e.get("nativeGlobalSource");
        source.put("commonKeyframes",Collections.singletonList("unknown"));assertThrows(RuntimeException.class,()->BrowserNativeGlobalEffectContract.validate(scene,descriptor()));
        e.remove("nativeGlobalSource");assertThrows(RuntimeException.class,()->BrowserNativeGlobalEffectContract.validate(scene,descriptor()));
    }
    @Test void duplicateEffectsAndMissingResourceAreNotClaimed()throws Exception {
        Map<String,Object> e=effect();assertThrows(RuntimeException.class,()->BrowserNativeGlobalEffectContract.validate(Collections.singletonMap("postEffects",Arrays.asList(e,e)),descriptor()));
        e.put("resourceId","missing");assertThrows(RuntimeException.class,()->BrowserNativeGlobalEffectContract.validate(Collections.singletonMap("postEffects",Collections.singletonList(e)),descriptor()));
    }
}
