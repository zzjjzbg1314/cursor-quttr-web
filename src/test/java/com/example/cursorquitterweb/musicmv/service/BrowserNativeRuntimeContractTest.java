package com.example.cursorquitterweb.musicmv.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;

class BrowserNativeRuntimeContractTest {
    static Map<String,Object> descriptor(){
        Map<String,Object> value=new LinkedHashMap<>(),assets=new LinkedHashMap<>(),binding=new LinkedHashMap<>();
        String root="/native-runtime/"+String.join("",Collections.nCopies(64,"a"))+"/";
        for(String[] pair:new String[][]{{"loaderUrl","loader.js"},{"mainWasmUrl","main.wasm"},{"mediaWasmUrl","media.wasm"},{"workerUrl","worker.mjs"},{"workletUrl","worklet.js"}})assets.put(pair[0],root+pair[1]);
        binding.put("resourceId","effect");binding.put("kind","filter");binding.put("path","effect/");
        value.put("schemaVersion","browser-native-photo-runtime-v1");value.put("layerMode",0);value.put("assets",assets);
        value.put("files",Collections.singletonList("effect/config.json"));value.put("bindings",Collections.singletonList(binding));return value;
    }
    @Test @SuppressWarnings("unchecked") void dynamicScriptTemplatesRequireExplicitPolicyAndSharedEntrypoints() {
        Map<String,Object> value=descriptor();value.put("schemaVersion","browser-native-scene-runtime-v4");
        value.put("videoAudioPolicy","external_music_only");value.put("scriptTemplatePolicy","dynamic_text_v1");
        ((List<Map<String,Object>>)value.get("bindings")).get(0).put("kind","script_template");
        value.put("files",Arrays.asList("effect/config.json","effect/js/main.js","effect/js/template/template.js",
                "effect/templates/parent/config.json","effect/templates/parent/content.json"));
        assertEquals(value,BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        value.remove("scriptTemplatePolicy");assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        value.put("scriptTemplatePolicy","dynamic_text_v1");value.put("files",Collections.singletonList("effect/config.json"));
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
    }
    @Test @SuppressWarnings("unchecked") void stickerPolicyRequiresV4AndOriginalEntrypoint() {
        Map<String,Object> value=descriptor();value.put("schemaVersion","browser-native-scene-runtime-v4");
        value.put("videoAudioPolicy","external_music_only");value.put("stickerPolicy","original_resources_v1");
        ((List<Map<String,Object>>)value.get("bindings")).get(0).put("kind","sticker");
        value.put("files",Arrays.asList("effect/config.json","effect/infoSticker.lua","effect/image.png"));
        assertEquals(value,BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        value.remove("stickerPolicy");assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        value.put("stickerPolicy","unknown");assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        value.put("stickerPolicy","original_resources_v1");value.put("files",Collections.singletonList("effect/config.json"));
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
    }
    @Test @SuppressWarnings("unchecked") void templatePolicyRequiresV4AndBothOriginalEntrypointFiles() {
        Map<String,Object> value=descriptor();value.put("schemaVersion","browser-native-scene-runtime-v4");
        value.put("videoAudioPolicy","external_music_only");value.put("textTemplatePolicy","original_attachments_v1");
        Map<String,Object> binding=((List<Map<String,Object>>)value.get("bindings")).get(0);binding.put("kind","text_template");
        value.put("files",Arrays.asList("effect/config.json","effect/content.json"));
        Map<String,Object> validated=BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect"));
        assertEquals(value,validated);assertNotSame(value,validated);
        value.remove("textTemplatePolicy");assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        value.put("textTemplatePolicy","unknown");assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        value.put("textTemplatePolicy","original_attachments_v1");value.put("schemaVersion","browser-native-scene-runtime-v2");
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        value.put("schemaVersion","browser-native-scene-runtime-v4");value.put("files",Collections.singletonList("effect/config.json"));
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
    }
    @Test @SuppressWarnings("unchecked") void blendRequiresDeliveredResourceAndSafePath() {
        Map<String,Object> value=descriptor();
        Map<String,Object> binding=((List<Map<String,Object>>)value.get("bindings")).get(0);
        binding.put("kind","blend");
        assertEquals(value,BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.emptySet()));
        binding.put("kind","unknown_blend");
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
    }
    @Test void acceptsDeliveredResourcesAndPreservesInput(){
        Map<String,Object> value=descriptor();assertEquals(value,BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
    }
    @Test @SuppressWarnings("unchecked") void acceptsSceneVersionWithoutWeakeningAnimationDependencyValidation(){
        Map<String,Object> value=descriptor();value.put("schemaVersion","browser-native-scene-runtime-v2");
        Map<String,Object> binding=((List<Map<String,Object>>)value.get("bindings")).get(0);
        binding.put("kind","animation");
        assertEquals(value,BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        assertEquals("browser-native-scene-runtime-v2",value.get("schemaVersion"));
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.emptySet()));
        binding.put("path","effect/../other/");
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
    }
    @Test void acceptsFixedImageSceneVersionWithoutAcceptingMissingDependencies(){
        Map<String,Object> value=descriptor();value.put("schemaVersion","browser-native-scene-runtime-v3");
        assertEquals(value,BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.emptySet()));
    }
    @Test void videoSceneRequiresExplicitExternalMusicPolicyAndPreservesIt() {
        Map<String,Object> value=descriptor();value.put("schemaVersion","browser-native-scene-runtime-v4");
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        value.put("videoAudioPolicy","source_audio");
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        value.put("videoAudioPolicy","external_music_only");
        assertEquals(value,BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.emptySet()));
    }
    @Test void rejectsUndeliveredDependencyAndUnknownVersion(){
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(descriptor(),Collections.emptySet()));
        Map<String,Object> value=descriptor();value.put("schemaVersion","future");assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
    }
    @Test @SuppressWarnings("unchecked") void rejectsAdminRemoteAndMixedSdkPaths(){
        for(String path:Arrays.asList("http://localhost:8082/worker.mjs","https://other.example/worker.mjs","/native-runtime/"+String.join("",Collections.nCopies(64,"b"))+"/worker.mjs")){
            Map<String,Object> value=descriptor();((Map<String,Object>)value.get("assets")).put("workerUrl",path);
            assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        }
    }
    @Test void rejectsTraversalAndDuplicateFiles(){
        for(List<String> files:Arrays.asList(Arrays.asList("effect/../config.json"),Arrays.asList("effect/config.json","effect/config.json"))){
            Map<String,Object> value=descriptor();value.put("files",files);
            assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        }
    }
    @Test @SuppressWarnings("unchecked") void preservesModelsAndRejectsUndeliveredOrNonModelFiles(){
        Map<String,Object> value=descriptor();
        value.put("files",Arrays.asList("effect/config.json","effect/models/face.model"));
        Map<String,Object> binding=((List<Map<String,Object>>)value.get("bindings")).get(0);
        binding.put("models",Collections.singletonMap("tt_face","effect/models/face.model"));
        assertEquals(value,BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        for(String file:Arrays.asList("other/face.model","effect/config.json","effect/../face.model")) {
            binding.put("models",Collections.singletonMap("tt_face",file));
            assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
        }
        binding.put("models",Collections.singletonMap("../face","effect/models/face.model"));
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
    }
}
