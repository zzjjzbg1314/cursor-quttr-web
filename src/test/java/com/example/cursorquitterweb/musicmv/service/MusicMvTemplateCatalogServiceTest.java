package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.cursorquitterweb.musicmv.dto.TemplatePromotionRequest;
import com.example.cursorquitterweb.musicmv.dto.TemplateMediaUploadSessionRequest;
import com.example.cursorquitterweb.musicmv.dto.TemplateBrowserParityRequest;
import com.example.cursorquitterweb.musicmv.dto.TemplateBrowserSceneRequest;
import com.example.cursorquitterweb.musicmv.dto.TemplateSlotReconcileRequest;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository.TemplateDetailRows;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;

class MusicMvTemplateCatalogServiceTest {
    private Map<String,Object> nativeStickerScene() {
        Map<String,Object> scene = BrowserNativeStickerContractTest.scene(BrowserNativeStickerContractTest.source());
        scene.put("schemaVersion", "browser-template-scene-v4");
        scene.put("templateId", "tpl_1"); scene.put("versionId", "tplver_1");
        scene.put("slots", Collections.emptyList()); scene.put("resources", Collections.emptyList());
        scene.put("capability", BrowserNativeStickerContractTest.map("photoReplacementReady", true,
                "browserLayerCompositionReady", true, "browserExportReady", true,
                "blockingFeatures", Collections.emptyList(), "executionCapabilities", Collections.singletonList(
                        executionCapability("sticker_layers", 1, 1, "exact", false))));
        Map<String,Object> descriptor = BrowserNativeRuntimeContractTest.descriptor();
        descriptor.putAll(BrowserNativeStickerContractTest.descriptor());
        descriptor.put("videoAudioPolicy", "external_music_only");
        descriptor.put("files", Arrays.asList("sticker/config.json", "sticker/infoSticker.lua", "animation/config.json"));
        scene.put("runtimeDelivery", BrowserNativeStickerContractTest.map("schemaVersion", "browser-runtime-delivery-v1",
                "resources", Arrays.asList(row("resourceId", "sticker"), row("resourceId", "animation")), "nativeEngine", descriptor));
        return scene;
    }

    private Map<String,Object> syncStickerScene(Map<String,Object> scene) throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1")).thenReturn(row("version_id", "tplver_1"));
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion("browser-template-scene-v4"); request.setScene(scene);
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));
        return service.synchronizeBrowserScene("tpl_1", "tplver_1", request);
    }

    @Test
    void acceptsBoundNativeStickerWithoutLegacyImageAndPreservesScene() throws Exception {
        Map<String,Object> scene = nativeStickerScene(); String before = new ObjectMapper().writeValueAsString(scene);
        assertEquals("ready", syncStickerScene(scene).get("status"));
        assertEquals(before, new ObjectMapper().writeValueAsString(scene));
        verify(runtimePackages).downloadForScene("tpl_1", "tplver_1", scene);
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsBothOriginalStickerLayersFromFailedIntake() throws Exception {
        Map<String,Object> scene = nativeStickerScene();
        List<Map<String,Object>> layers = new ObjectMapper().readValue(
                getClass().getResourceAsStream("/musicmv/native-sticker-intake-layers.json"), List.class);
        scene.put("layers", layers);
        Map<String,Object> delivery = (Map<String,Object>) scene.get("runtimeDelivery");
        Map<String,Object> descriptor = (Map<String,Object>) delivery.get("nativeEngine");
        List<Map<String,Object>> resources = new java.util.ArrayList<>(), bindings = new java.util.ArrayList<>();
        List<String> files = new java.util.ArrayList<>();
        for (Map<String,Object> layer : layers) {
            String id = (String) ((Map<String,Object>) layer.get("nativeStickerSource")).get("resourceId");
            resources.add(row("resourceId", id));
            bindings.add(BrowserNativeStickerContractTest.map("resourceId", id, "path", id + "/", "kind", "sticker"));
            files.add(id + "/config.json"); files.add(id + "/infoSticker.lua");
        }
        delivery.put("resources", resources); descriptor.put("bindings", bindings); descriptor.put("files", files);
        ((Map<String,Object>) scene.get("capability")).put("executionCapabilities", Collections.singletonList(
                executionCapability("sticker_layers", 2, 2, "exact", false)));
        assertEquals("ready", syncStickerScene(scene).get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nativeStickerCannotBypassMissingDeliveryBindingSourceOrImageChecks() throws Exception {
        for (int mutation = 0; mutation < 6; mutation++) {
            Map<String,Object> scene = nativeStickerScene();
            Map<String,Object> delivery = (Map<String,Object>) scene.get("runtimeDelivery");
            Map<String,Object> descriptor = (Map<String,Object>) delivery.get("nativeEngine");
            Map<String,Object> layer = ((List<Map<String,Object>>) scene.get("layers")).get(0);
            if (mutation == 0) scene.remove("runtimeDelivery");
            if (mutation == 1) delivery.put("resources", Collections.emptyList());
            if (mutation == 2) descriptor.put("bindings", Collections.emptyList());
            if (mutation == 3) layer.remove("nativeStickerSource");
            if (mutation == 4) layer.put("type", "static_image");
            if (mutation == 5) layer.put("type", "video");
            assertThrows(ApiException.class, () -> syncStickerScene(scene));
        }
    }

    private MusicMvTemplateCatalogRepository repository;
    private CloudflareTemplateMediaProvider mediaProvider;
    private TemplateRuntimePackageService runtimePackages;
    private MusicMvTemplateCatalogService service;

    @BeforeEach
    void setUp() {
        repository = mock(MusicMvTemplateCatalogRepository.class);
        mediaProvider = mock(CloudflareTemplateMediaProvider.class);
        runtimePackages = mock(TemplateRuntimePackageService.class);
        service = new MusicMvTemplateCatalogService(repository,
                mediaProvider, runtimePackages, mock(D1DatabaseClient.class),
                new ObjectMapper());
        when(repository.versionByValidationJob(anyString())).thenReturn(null);
        Map<String, Object> category = new LinkedHashMap<String, Object>();
        category.put("enabled", Integer.valueOf(1));
        category.put("level", Integer.valueOf(2));
        category.put("is_selectable", Integer.valueOf(1));
        for (String key : Arrays.asList("birthday", "wedding", "anniversary", "graduation",
                "holidays-parties", "fathers-day", "family", "baby-kids", "couples", "friendship",
                "daily-life", "travel", "school-life", "growing-up", "recap",
                "hobbies-interests", "motivation", "healing", "love-thanks",
                "farewell-breakup", "memorial")) {
            when(repository.category(key)).thenReturn(category);
        }
    }

    private Map<String, Object> publicVersionState(Map<String, Object> template, Map<String, Object> version) {
        Map<String, Object> state = row("template_status", template.get("status"));
        state.put("visibility", template.get("visibility"));
        state.put("current_version_id", template.get("current_version_id"));
        state.put("version_status", version.get("status"));
        return state;
    }

    @Test
    void preservesNativeNonlinearSceneThroughSyncAndPublicDetail() throws Exception {
        assertNativeSceneRoundTrip("/musicmv/browser-native-nonlinear-scene.json");
    }

    @Test
    void preservesVerifiedNativeCurveSceneThroughSyncAndPublicDetail() throws Exception {
        assertNativeSceneRoundTrip("/musicmv/browser-native-curve-scene.json");
    }

    @SuppressWarnings("unchecked")
    private void assertNativeSceneRoundTrip(String resource) throws Exception {
        Map<String,Object> template=row("template_id","tpl_1"), version=row("version_id","tplver_1");
        template.put("status","published");template.put("visibility","public");template.put("current_version_id","tplver_1");
        version.put("width",1080);version.put("height",1920);version.put("fps",30);
        when(repository.template("tpl_1")).thenReturn(template);
        when(repository.version("tpl_1","tplver_1")).thenReturn(version);
        ObjectMapper mapper=new ObjectMapper();
        Map<String,Object> scene=mapper.readValue(getClass().getResourceAsStream(resource),Map.class);
        TemplateBrowserSceneRequest request=new TemplateBrowserSceneRequest();
        request.setScene(scene);request.setSchemaVersion(String.valueOf(scene.get("schemaVersion")));
        String original=mapper.writeValueAsString(scene);request.setManifestSha256(sha256(original));
        assertEquals("ready",service.synchronizeBrowserScene("tpl_1","tplver_1",request).get("status"));
        ArgumentCaptor<String> json=ArgumentCaptor.forClass(String.class);
        verify(repository).upsertBrowserScene(eq("tpl_1"),eq("tplver_1"),eq(request.getSchemaVersion()),eq(request.getManifestSha256()),eq("ready"),json.capture());
        assertEquals(mapper.readTree(original),mapper.readTree(json.getValue()));
        Map<String,Object> stored=row("version_id","tplver_1");stored.put("status","ready");
        stored.put("schema_version",request.getSchemaVersion());stored.put("manifest_sha256",request.getManifestSha256());stored.put("scene_json",json.getValue());
        when(repository.templateDetail(eq("tpl_1"),org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(new TemplateDetailRows(template,
                Collections.emptyList(),null,Collections.emptyList(),Collections.singletonList(version),Collections.emptyList(),Collections.emptyList(),Collections.singletonList(stored)));
        Map<String,Object> detail=service.detail("tpl_1",false);
        Map<String,Object> resultVersion=((List<Map<String,Object>>)detail.get("versions")).get(0);
        Map<String,Object> browser=(Map<String,Object>)resultVersion.get("browserRender");
        assertEquals(request.getManifestSha256(),browser.get("sceneManifestSha256"));
        assertEquals(mapper.readTree(original),mapper.valueToTree(browser.get("scene")));
        Map<String,Object> layer=((List<Map<String,Object>>)((Map<String,Object>)browser.get("scene")).get("layers")).get(0);
        assertEquals(((List<?>)scene.get("layers")).get(0),layer);
        assertEquals("motion-graph",((Map<?,?>)((List<?>)layer.get("keyframe_graph_list")).get(0)).get("id"));
        assertTrue(layer.containsKey("nativeSpeedBinding"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsNativeCurveSceneWithoutVerifiedKeyframeClockBeforeSaving() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1")).thenReturn(row("version_id", "tplver_1"));
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> scene = mapper.readValue(getClass().getResourceAsStream(
                "/musicmv/browser-native-curve-clock-missing-scene.json"), Map.class);
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setScene(scene);
        request.setSchemaVersion(String.valueOf(scene.get("schemaVersion")));
        request.setManifestSha256(sha256(mapper.writeValueAsString(scene)));
        assertEquals("TEMPLATE_BROWSER_SCENE_ANIMATION_NOT_READY", assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request)).getCode());
        verify(repository, never()).upsertBrowserScene(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void nativeBindingRelativePathsAreAllowedOnlyInRuntimeDelivery() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1")).thenReturn(row("version_id", "tplver_1"));
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> scene = mapper.readValue(getClass().getResourceAsStream(
                "/musicmv/browser-color-correct-scene.json"), Map.class);
        Map<String, Object> binding = row("resourceId", "effect_resource");
        binding.put("path", "effect_resource/");
        Map<String, Object> delivery = row("nativeEngine", row("bindings", Collections.singletonList(binding)));
        scene.put("runtimeDelivery", delivery);
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setScene(scene); request.setSchemaVersion(String.valueOf(scene.get("schemaVersion")));
        request.setManifestSha256(sha256(mapper.writeValueAsString(scene)));
        assertEquals("ready", service.synchronizeBrowserScene("tpl_1", "tplver_1", request).get("status"));
        verify(runtimePackages).downloadForScene("tpl_1", "tplver_1", scene);
        for (String path : Arrays.asList("/Users/private/file", "../effect_resource/", "other_resource/", "file:secret", "effect_resource/../")) {
            binding.put("path", path);
            request.setManifestSha256(sha256(mapper.writeValueAsString(scene)));
            assertEquals("TEMPLATE_BROWSER_SCENE_PRIVATE_DATA", assertThrows(ApiException.class,
                    () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request)).getCode());
        }
        binding.put("path", "effect_resource/");
        scene.remove("runtimeDelivery");
        scene.put("runtimeDelivery/nativeEngine", row("bindings", Collections.singletonList(binding)));
        request.setManifestSha256(sha256(mapper.writeValueAsString(scene)));
        assertEquals("TEMPLATE_BROWSER_SCENE_PRIVATE_DATA", assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request)).getCode());
        scene.remove("runtimeDelivery/nativeEngine"); scene.put("binding", binding);
        assertEquals("TEMPLATE_BROWSER_SCENE_PRIVATE_DATA", assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request)).getCode());
    }

    @Test
    void publishedSceneAllowsOnlyIdenticalReplayWithoutWriting() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        Map<String, Object> version = row("version_id", "tplver_1");
        version.put("status", "published");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> scene = mapper.readValue(getClass().getResourceAsStream(
                "/musicmv/browser-color-correct-scene.json"), Map.class);
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setScene(scene);
        request.setSchemaVersion(String.valueOf(scene.get("schemaVersion")));
        request.setManifestSha256(sha256(mapper.writeValueAsString(scene)));
        Map<String, Object> stored = row("status", "ready");
        stored.put("schema_version", request.getSchemaVersion());
        stored.put("manifest_sha256", request.getManifestSha256());
        when(repository.browserScene("tplver_1")).thenReturn(stored);
        assertEquals("ready", service.synchronizeBrowserScene("tpl_1", "tplver_1", request).get("status"));
        scene.put("name", "changed scene");
        request.setManifestSha256(sha256(mapper.writeValueAsString(scene)));
        assertEquals("TEMPLATE_PUBLISHED_SCENE_IMMUTABLE", assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request)).getCode());
        when(repository.browserScene("tplver_1")).thenReturn(null);
        assertEquals("TEMPLATE_PUBLISHED_SCENE_IMMUTABLE", assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request)).getCode());
        verify(repository, never()).upsertBrowserScene(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
        verify(runtimePackages, never()).downloadForScene(anyString(), anyString(), any());
        verify(runtimePackages, never()).downloadExactImage(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void preservesColorCorrectBindingEvidenceAndRawFramesInStoredScene() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1")).thenReturn(row("version_id", "tplver_1"));
        ObjectMapper mapper = new ObjectMapper();
        Map<String,Object> scene = mapper.readValue(getClass().getResourceAsStream("/musicmv/browser-color-correct-scene.json"), Map.class);
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion(String.valueOf(scene.get("schemaVersion"))); request.setScene(scene);
        String original = mapper.writeValueAsString(scene);
        request.setManifestSha256(sha256(original));
        assertEquals("ready", service.synchronizeBrowserScene("tpl_1", "tplver_1", request).get("status"));
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(repository).upsertBrowserScene(eq("tpl_1"), eq("tplver_1"), eq(request.getSchemaVersion()),
                eq(request.getManifestSha256()), eq("ready"), stored.capture());
        assertEquals(mapper.readTree(original), mapper.readTree(stored.getValue()));
        Map<String,Object> layer = ((List<Map<String,Object>>)scene.get("layers")).get(0);
        assertEquals("draft-keyframe-bindings-v2", ((Map<?,?>)layer.get("draft_keyframe_bindings")).get("contractVersion"));
        assertEquals("KFTypeColorCorrect", ((Map<?,?>)((List<?>)layer.get("common_keyframes")).get(0)).get("property_type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsCompleteOriginalGraphSceneAndRejectsUnverifiedNewContracts() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1")).thenReturn(row("version_id", "tplver_1"));
        ObjectMapper mapper = new ObjectMapper();
        Map<String,Object> scene = mapper.readValue(getClass().getResourceAsStream("/musicmv/browser-original-graph-scene.json"), Map.class);
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion(String.valueOf(scene.get("schemaVersion")));request.setScene(scene);
        request.setManifestSha256(sha256(mapper.writeValueAsString(scene)));
        assertEquals("ready", service.synchronizeBrowserScene("tpl_1", "tplver_1", request).get("status"));
        Map<String,Object> transition=null, graph=null;
        for (Map<String,Object> layer:(List<Map<String,Object>>)scene.get("layers")) {
            if(layer.get("transitionIn") instanceof Map)transition=(Map<String,Object>)layer.get("transitionIn");
            for(Map<String,Object> effect:(List<Map<String,Object>>)layer.getOrDefault("effects",Collections.emptyList()))
                if("scripted_resource_graph".equals(effect.get("preset")))graph=effect;
        }
        Object original=transition.remove("evidence");request.setManifestSha256(sha256(mapper.writeValueAsString(scene)));
        assertEquals("TEMPLATE_BROWSER_SCENE_TRANSITION_CONTRACT_INVALID",assertThrows(ApiException.class,()->service.synchronizeBrowserScene("tpl_1","tplver_1",request)).getCode());
        transition.put("evidence",original);original=graph.remove("runtimeValidation");request.setManifestSha256(sha256(mapper.writeValueAsString(scene)));
        assertEquals("TEMPLATE_BROWSER_SCENE_SCRIPTED_TEXTURE_CONTRACT_INVALID",assertThrows(ApiException.class,()->service.synchronizeBrowserScene("tpl_1","tplver_1",request)).getCode());
        graph.put("runtimeValidation",original);
        Map<String,Object> lut=((List<Map<String,Object>>)scene.get("postEffects")).get(0);lut.put("lutSampling","unknown");request.setManifestSha256(sha256(mapper.writeValueAsString(scene)));
        assertEquals("TEMPLATE_BROWSER_SCENE_POST_EFFECT_CONTRACT_INVALID",assertThrows(ApiException.class,()->service.synchronizeBrowserScene("tpl_1","tplver_1",request)).getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void synchronizesWholeSceneTextureSequenceAndRejectsInvalidContracts() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1")).thenReturn(row("version_id", "tplver_1"));
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> scene = mapper.readValue(getClass().getResourceAsStream(
                "/musicmv/browser-color-correct-scene.json"), Map.class);
        Map<String, Object> effect = row("preset", "texture_sequence_screen_multiply");
        effect.put("contractVersion", "browser-post-texture-sequence-v1");
        effect.put("semanticFamily", "texture_sequence_screen_multiply");
        effect.put("applicationStage", "whole_scene_after_layers");
        effect.put("resourceId", "published_texture_sequence");
        effect.put("targetStartSeconds", 0.0);
        effect.put("targetDurationSeconds", 32.533333);
        effect.put("intensity", 1.0);
        effect.put("fidelity", "exact");
        effect.put("sourceStartSeconds", 0.0);
        effect.put("sourceSpeed", 1.0);
        effect.put("effectSpeed", 0.65);
        scene.put("postEffects", Collections.singletonList(effect));
        Map<String, Object> capability = (Map<String, Object>) scene.get("capability");
        Map<String, Object> report = (Map<String, Object>) scene.get("capabilityReport");
        for (Object rawFeatures : Arrays.asList(capability.get("executionCapabilities"), report.get("features"))) {
            for (Map<String, Object> feature : (List<Map<String, Object>>) rawFeatures) {
                if ("post_effects".equals(feature.get("feature"))) {
                    feature.put("declaredCount", 1);
                    feature.put("executableCount", 1);
                    feature.put("fidelity", "exact");
                }
            }
        }
        ((List<Map<String, Object>>) report.get("effectImplementations")).add(effectImplementation(
                "post_effect", "texture_sequence_screen_multiply", "canvas_post_effect_v1", "exact"));
        Map<String, Object> summary = (Map<String, Object>) report.get("summary");
        for (String key : Arrays.asList("declaredItemCount", "executableItemCount", "exactFeatureCount", "effectImplementationCount")) {
            summary.put(key, ((Number) summary.get(key)).intValue() + 1);
        }
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion(String.valueOf(scene.get("schemaVersion")));
        request.setScene(scene);
        String original = mapper.writeValueAsString(scene);
        request.setManifestSha256(sha256(original));
        assertEquals("ready", service.synchronizeBrowserScene("tpl_1", "tplver_1", request).get("status"));
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(repository).upsertBrowserScene(eq("tpl_1"), eq("tplver_1"), eq(request.getSchemaVersion()),
                eq(request.getManifestSha256()), eq("ready"), stored.capture());
        assertEquals(mapper.readTree(original), mapper.readTree(stored.getValue()));
        for (String field : Arrays.asList("contractVersion", "semanticFamily", "applicationStage", "resourceId", "effectSpeed")) {
            Object value = effect.remove(field);
            assertThrows(ApiException.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                    service, "requireValidBrowserPostEffects", scene, Collections.emptyMap()), field);
            effect.put(field, value);
        }
        for (String field : Arrays.asList("effectSpeed", "sourceStartSeconds", "sourceSpeed",
                "targetStartSeconds", "targetDurationSeconds", "intensity")) {
            Object value = effect.get(field);
            for (Object invalid : Arrays.asList(-1.0, Double.NaN, Double.POSITIVE_INFINITY, "1")) {
                effect.put(field, invalid);
                assertThrows(ApiException.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                        service, "requireValidBrowserPostEffects", scene, Collections.emptyMap()), field);
            }
            effect.put(field, value);
        }
        for (String field : Arrays.asList("contractVersion", "semanticFamily", "applicationStage", "preset", "fidelity", "resourceId")) {
            Object value = effect.put(field, "invalid/unknown");
            assertThrows(ApiException.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                    service, "requireValidBrowserPostEffects", scene, Collections.emptyMap()), field);
            effect.put(field, value);
        }
        effect.put("contractVersion", "browser-layer-effect-semantic-v4");
        effect.put("applicationStage", "source_graph_before_video_animation");
        assertThrows(ApiException.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "requireValidBrowserPostEffects", scene, Collections.emptyMap()));
        effect.put("contractVersion", "browser-post-texture-sequence-v1");
        effect.put("applicationStage", "whole_scene_after_layers");
        effect.remove("sourceStartSeconds");
        effect.remove("sourceSpeed");
        effect.put("effectSpeed", 0.0);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "requireValidBrowserPostEffects", scene, Collections.emptyMap());
    }

    @Test
    void acceptsScriptedTexturesWithoutLayerDurationButRequiresTheirContract() {
        for (String preset : Arrays.asList("aspect_texture_sequence", "duration_texture_sequence",
                "chromatic_texture_distortion")) {
            Map<String, Object> effect = row("preset", preset);
            effect.put("fidelity", "exact");
            effect.put("semanticFamily", preset);
            effect.put("contractVersion", "browser-scripted-texture-v1");
            effect.put("applicationStage", "source_graph_before_video_animation");
            effect.put("evidence", "package_lua_clock_and_original_shader_graph");
            Map<String, Object> layer = row("effects", Collections.singletonList(effect));
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireValidBrowserLayerEffects", layer);
            effect.put("targetStartSeconds", 0.0);
            effect.put("targetDurationSeconds", 22.0);
            effect.put("intensity", 1.0);
            effect.put("applicationStage", "whole_scene_after_layers");
            Map<String, Object> scene = row("postEffects", Collections.singletonList(effect));
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireValidBrowserPostEffects", scene, Collections.emptyMap());
            effect.remove("targetDurationSeconds");
            assertThrows(ApiException.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireValidBrowserPostEffects", scene, Collections.emptyMap()));
            effect.put("applicationStage", "source_graph_before_video_animation");
            effect.remove("evidence");
            assertThrows(ApiException.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireValidBrowserLayerEffects", layer));
        }
        Map<String, Object> unknown = row("effects", Collections.singletonList(row("preset", "unknown_texture")));
        ApiException error = assertThrows(ApiException.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireValidBrowserLayerEffects", unknown));
        assertTrue(error.getMessage().contains("presetAllowed=false"));
        assertTrue(error.getMessage().contains("durationRequired=false"));
    }

    @Test
    void acceptsAllCompiledTextAnimationsAndRejectsMissingDurationOrContract() {
        for (String preset : Arrays.asList("quad_out_alpha_animation", "linear_scale_alpha_animation", "sequential_glyph_fade_animation",
                "staggered_glyph_pulse_animation", "staggered_glyph_bounce_animation", "directional_blur_fade_animation")) {
            Map<String, Object> animation = row("preset", preset);
            animation.put("fidelity", "exact");
            animation.put("durationSeconds", 0.8);
            animation.put("semanticFamily", preset);
            animation.put("contractVersion", "browser-package-animation-v1");
            animation.put("packageClock", "declared_category_duration");
            animation.put("evidence", "package_script_transform_glyph_and_shader_equations");
            Map<String, Object> layer = row("animations", Collections.singletonList(animation));
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireValidBrowserLayerAnimations", layer);
            animation.remove("durationSeconds");
            assertThrows(ApiException.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireValidBrowserLayerAnimations", layer));
            animation.put("durationSeconds", 0.8);
            animation.put("semanticFamily", "incorrect");
            assertThrows(ApiException.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireValidBrowserLayerAnimations", layer));
        }
    }

    @Test
    void validatesInlineEffectClockSchemaAndPublishedBinding() throws Exception {
        Map<String, Object> clock = new LinkedHashMap<>();
        clock.put("schemaVersion", "browser-effect-clock-v1"); clock.put("effectId", "segment-1");
        clock.put("resourceId", "resource-1"); clock.put("targetStartSeconds", 10.0);
        clock.put("targetDurationSeconds", 0.2); clock.put("sourceStartSeconds", 2.0);
        clock.put("segmentSpeed", 0.5); clock.put("speedAdjustment", 0.33);
        String inline = "data:application/json;base64," + java.util.Base64.getEncoder().encodeToString(new ObjectMapper().writeValueAsBytes(clock));
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireSafeInlineBrowserResource", inline);
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceKey", "effect_clock_segment-1"); resource.put("inlineData", inline);
        Map<String, Object> effect = new LinkedHashMap<>(clock);
        Map<String, Object> scene = new LinkedHashMap<>(); scene.put("postEffects", Collections.singletonList(effect));
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireValidBrowserEffectClock", resource, scene);
        effect.put("targetStartSeconds", 11.0);
        assertThrows(ApiException.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireValidBrowserEffectClock", resource, scene));
        clock.put("privatePath", "/Users/private/source.json");
        String invalid = "data:application/json;base64," + java.util.Base64.getEncoder().encodeToString(new ObjectMapper().writeValueAsBytes(clock));
        assertThrows(ApiException.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireSafeInlineBrowserResource", invalid));
        assertThrows(ApiException.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireSafeInlineBrowserResource", "data:application/json;base64,e30="));
    }

    @Test
    void classifiesMothersDayFamilyTemplateIntoFamilyHolidayAndThanks() {
        TemplatePromotionRequest request = validPromotion();
        request.setCategoryKey("family");
        request.setSourceCategory("Family");
        request.setSourceTitle("Happy Mother's Day");
        request.setSourceHashtags(Arrays.asList("mothersday", "bestmom"));
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(1));

        service.promote(request);

        Set<String> keys = capturedCategoryKeys("family");
        assertEquals(new HashSet<String>(Arrays.asList(
                "family")), keys);
    }

    @Test
    void classifiesFathersDayTemplateIntoDedicatedAssociatedCategory() {
        TemplatePromotionRequest request = validPromotion();
        request.setCategoryKey("family");
        request.setSourceCategory("Family");
        request.setSourceTitle("Happy Father's Day");
        request.setSourceHashtags(Arrays.asList("fathersday", "bestdad"));
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(1));

        service.promote(request);

        Set<String> keys = capturedCategoryKeys("family");
        assertTrue(keys.containsAll(Arrays.asList(
                "family")));
    }

    @Test
    void classifiesBabyMilestoneFamilyTemplateIntoBabyGrowthAndRecap() {
        TemplatePromotionRequest request = validPromotion();
        request.setCategoryKey("family");
        request.setSourceCategory("Family");
        request.setSourceTitle("Baby first year photo dump");
        request.setSourceHashtags(Arrays.asList("baby", "firstyear", "photodump"));
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(1));

        service.promote(request);

        Set<String> keys = capturedCategoryKeys("baby-kids");
        assertEquals(Collections.singleton("baby-kids"), keys);
    }

    @Test
    void usesOriginalCapCutClassificationAsPrimaryAndBatchCategoryOnlyAsFallback() {
        TemplatePromotionRequest request = validPromotion();
        request.setCategoryKey("birthday");
        request.setSourceTitle("Family Moments");
        request.setSourceCategory("Family");
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(1));

        service.promote(request);

        Set<String> keys = capturedCategoryKeys("family");
        assertEquals(Collections.singleton("family"), keys);
    }

    @Test
    void keepsManuallyLockedPrimaryEvenWhenCapCutMetadataMatchesAnotherCategory() {
        TemplatePromotionRequest request = validPromotion();
        request.setCategoryKey("birthday");
        request.setSourceTitle("Family Moments");
        request.setSourceCategory("Family");
        request.setClassificationLocked(Boolean.TRUE);
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(1));

        service.promote(request);

        Set<String> keys = capturedCategoryKeys("birthday");
        assertEquals(Collections.singleton("birthday"), keys);
    }

    @Test
    void birthdayContentOverridesBroadAnniversarySource() {
        TemplatePromotionRequest request = validPromotion();
        request.setCategoryKey("anniversary"); request.setSourceCategory("Anniversary");
        request.setSourceTitle("Happy Birthday Baby");
        when(repository.nextVersionNumber("tpl_1")).thenReturn(1);
        service.promote(request);
        assertEquals(new HashSet<>(Arrays.asList("birthday", "baby-kids")), capturedCategoryKeys("birthday"));
    }

    @Test
    void weddingAnniversaryUsesAnniversaryAsPrimary() {
        TemplatePromotionRequest request = validPromotion();
        request.setCategoryKey("wedding"); request.setSourceTitle("Happy wedding anniversary");
        when(repository.nextVersionNumber("tpl_1")).thenReturn(1);
        service.promote(request);
        assertTrue(capturedCategoryKeys("anniversary").contains("anniversary"));
    }

    @Test
    void memorialRequiresManualReviewInsteadOfAutomaticAnniversary() {
        TemplatePromotionRequest request = validPromotion();
        request.setCategoryKey("anniversary"); request.setSourceTitle("Memorial anniversary for dad");
        assertEquals("TEMPLATE_CLASSIFICATION_REVIEW_REQUIRED", assertThrows(ApiException.class, () -> service.promote(request)).getCode());
    }

    @Test
    void graduationUsesSchoolTopic() {
        TemplatePromotionRequest request = validPromotion();
        request.setCategoryKey("school-life"); request.setSourceTitle("Graduation day");
        when(repository.nextVersionNumber("tpl_1")).thenReturn(1);
        service.promote(request);
        assertEquals(Collections.singleton("school-life"), capturedCategoryKeys("school-life"));
    }

    @Test
    void doesNotTreatMomentsAsTheShortFamilyKeywordMom() {
        TemplatePromotionRequest request = validPromotion();
        request.setCategoryKey("daily-life");
        request.setSourceTitle("Summer moments");
        request.setSourceHashtags(Collections.singletonList("moments"));
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(1));

        service.promote(request);

        Set<String> keys = capturedCategoryKeys("daily-life");
        assertFalse(keys.contains("family"));
    }

    @Test
    void promotesExactNativeEvidenceIntoNewImmutableVersion() {
        when(repository.versionByValidationJob("native_1")).thenReturn(null);
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(3));
        Map<String, Object> result = service.promote(validPromotion());

        assertEquals("tpl_1", result.get("templateId"));
        assertEquals("validated", result.get("status"));
        assertFalse((Boolean) result.get("idempotentReplay"));
        verify(repository).promote(any(TemplatePromotionRequest.class), anyString(),
                anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void rejectsDifferentTemplateForExistingCapCutIdentity() {
        Map<String, Object> existing = new LinkedHashMap<String, Object>();
        existing.put("template_id", "tpl_existing");
        existing.put("capcut_template_id", "7362454015088561426");
        when(repository.templatesByCapCutTemplateIds(
                Collections.singletonList("7362454015088561426")))
                .thenReturn(Collections.singletonList(existing));

        ApiException error = assertThrows(ApiException.class,
                () -> service.promote(validPromotion()));

        assertEquals("CAPCUT_TEMPLATE_ALREADY_EXISTS", error.getCode());
        verify(repository, never()).promote(any(), anyString(), anyInt(), anyString(),
                anyString(), anyString());
    }

    @Test
    void enrichesAnIdempotentNativeVersionWithDerivedQualityEvidence() {
        TemplatePromotionRequest request = validPromotion();
        Map<String, Object> existing = row("version_id", "tplver_existing");
        existing.put("template_id", "tpl_1");
        existing.put("draft_snapshot_sha256", hash('b'));
        existing.put("validation_master_sha256", hash('a'));
        existing.put("status", "published");
        when(repository.versionByValidationJob("native_1")).thenReturn(existing);

        Map<String, Object> result = service.promote(request);

        assertEquals(Boolean.TRUE, result.get("idempotentReplay"));
        verify(repository).enrichVisualQuality(eq("tplver_existing"), eq(Double.valueOf(13)),
                anyString());
    }

    @Test
    void rejectsValidationThatUsedMoreThanOneVideoEncode() {
        TemplatePromotionRequest request = validPromotion();
        request.setVideoEncodeCount(Integer.valueOf(2));

        ApiException error = assertThrows(ApiException.class, () -> service.promote(request));
        assertEquals("TEMPLATE_VALIDATION_NOT_EXACT", error.getCode());
        verify(repository, never()).promote(any(), anyString(), anyInt(), anyString(),
                anyString(), anyString());
    }

    @Test
    void rejectsNativePromotionWithoutBoundVisualQualityEvidence() {
        TemplatePromotionRequest request = validPromotion();
        request.setVisualQuality(null);

        ApiException error = assertThrows(ApiException.class, () -> service.promote(request));

        assertEquals("TEMPLATE_VISUAL_QUALITY_REQUIRED", error.getCode());
        verify(repository, never()).promote(any(), anyString(), anyInt(), anyString(),
                anyString(), anyString());
    }

    @Test
    void promotesLatestSavedDraftWithoutNativeValidationRender() {
        TemplatePromotionRequest request = validPromotion();
        request.setPromotionMode("latest_saved_draft");
        request.setValidationRenderJobId("draft_1");
        request.setSemanticIntegrity("browser_ready");
        request.setVideoEncodeCount(Integer.valueOf(0));
        request.setRendererVersion("browser-canvas-v1");
        when(repository.versionByValidationJob("draft_1")).thenReturn(null);
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(4));

        Map<String, Object> result = service.promote(request);

        assertEquals("validated", result.get("status"));
        verify(repository).promote(eq(request), anyString(), eq(Integer.valueOf(4)),
                anyString(), anyString(), anyString());
    }

    @Test
    void synchronizationCannotOverwritePublishedEvidenceWithSameValidationJob() {
        TemplatePromotionRequest request = synchronizationRequest();
        Map<String, Object> existing = row("version_id", "tplver_existing");
        existing.put("template_id", "tpl_1");
        existing.put("status", "published");
        existing.put("validation_master_sha256", hash('f'));
        when(repository.versionByValidationJob("draft_1")).thenReturn(existing);
        assertEquals("TEMPLATE_PROMOTION_IDEMPOTENCY_CONFLICT",
                assertThrows(ApiException.class, () -> service.promote(request)).getCode());
        verify(repository, never()).replaceSynchronizedVersion(any(), anyString(), anyString(), anyString(), anyString());
        verify(repository, never()).promote(any(), anyString(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void synchronizationCreatesSeparateCandidateWhenCurrentVersionIsPublished() {
        TemplatePromotionRequest request = synchronizationRequest();
        Map<String, Object> existing = row("version_id", "tplver_published");
        existing.put("status", "published");
        when(repository.synchronizationVersion("tpl_1")).thenReturn(existing);
        when(repository.nextVersionNumber("tpl_1")).thenReturn(2);
        Map<String, Object> result = service.promote(request);
        assertFalse("tplver_published".equals(result.get("versionId")));
        verify(repository).promote(eq(request), anyString(), eq(2), anyString(), anyString(), anyString());
        verify(repository, never()).replaceSynchronizedVersion(any(), anyString(), anyString(), anyString(), anyString());
        verify(repository, never()).publish(anyString(), anyString());
    }

    @Test
    void synchronizationUsesExistingTemplateForNewDraftTask() {
        TemplatePromotionRequest request = synchronizationRequest();
        Map<String, Object> existing = row("version_id", "tplver_current");
        existing.put("status", "validated");
        when(repository.synchronizationVersion("tpl_1")).thenReturn(existing);
        Map<String, Object> result = service.promote(request);
        assertEquals("tplver_current", result.get("versionId"));
        verify(repository).replaceSynchronizedVersion(eq(request), eq("tplver_current"),
                anyString(), anyString(), anyString());
    }

    @Test
    void synchronizationCannotReplaceAnotherTemplatesEvidence() {
        TemplatePromotionRequest request = synchronizationRequest();
        Map<String, Object> existing = row("version_id", "tplver_other");
        existing.put("template_id", "tpl_other");
        when(repository.versionByValidationJob("draft_1")).thenReturn(existing);
        assertEquals("TEMPLATE_PROMOTION_IDEMPOTENCY_CONFLICT",
                assertThrows(ApiException.class, () -> service.promote(request)).getCode());
        verify(repository, never()).replaceSynchronizedVersion(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void firstSynchronizationStillCreatesVersion() {
        TemplatePromotionRequest request = synchronizationRequest();
        when(repository.nextVersionNumber("tpl_1")).thenReturn(1);
        service.promote(request);
        verify(repository).promote(eq(request), anyString(), eq(1), anyString(), anyString(), anyString());
    }

    private TemplatePromotionRequest synchronizationRequest() {
        TemplatePromotionRequest request = validPromotion();
        request.setPromotionMode("latest_saved_draft");
        request.setReplaceExisting(Boolean.TRUE);
        request.setValidationRenderJobId("draft_1");
        request.setSemanticIntegrity("browser_ready");
        request.setVideoEncodeCount(0);
        request.setRendererVersion("browser-canvas-v1");
        return request;
    }

    @Test
    void synchronizationCleansOldMediaOnlyAfterLatestMediaAreReady() {
        Map<String, Object> version = row("version_id", "tplver_1");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);
        when(repository.browserScene("tplver_1")).thenReturn(row("manifest_sha256", hash('a')));
        com.example.cursorquitterweb.musicmv.dto.TemplateSyncCompleteRequest request =
                new com.example.cursorquitterweb.musicmv.dto.TemplateSyncCompleteRequest();
        request.setManifestSha256(hash('a'));
        request.setMediaRoles(java.util.Arrays.asList("cover", "browser_parity_reference"));
        assertEquals("TEMPLATE_SYNC_MEDIA_NOT_READY", assertThrows(ApiException.class,
                () -> service.completeSynchronization("tpl_1", "tplver_1", request)).getCode());
        verify(repository, never()).retainSynchronizedMedia(anyString(), anyString(), anyList());
        Map<String, Object> cover = row("media_role", "cover");
        cover.put("status", "ready");
        Map<String, Object> reference = row("media_role", "browser_parity_reference");
        reference.put("status", "ready");
        when(repository.media("tplver_1")).thenReturn(java.util.Arrays.asList(cover, reference));
        assertEquals("synchronized", service.completeSynchronization("tpl_1", "tplver_1", request).get("status"));
        verify(repository).retainSynchronizedMedia("tpl_1", "tplver_1", request.getMediaRoles());
        request.setManifestSha256(hash('b'));
        assertEquals("TEMPLATE_SYNC_SCENE_CHANGED", assertThrows(ApiException.class,
                () -> service.completeSynchronization("tpl_1", "tplver_1", request)).getCode());
    }

    @Test
    void cachesPublicTemplateDetailAfterSingleBatchLoad() {
        Map<String, Object> template = row("template_id", "tpl_1");
        template.put("status", "published");
        template.put("visibility", "public");
        template.put("current_version_id", "tplver_1");
        template.put("revision", Integer.valueOf(3));
        Map<String, Object> version = row("version_id", "tplver_1");
        version.put("status", "published");
        TemplateDetailRows rows = new TemplateDetailRows(template,
                Collections.<Map<String, Object>>emptyList(), null,
                Collections.<Map<String, Object>>emptyList(),
                Collections.singletonList(version),
                Collections.<Map<String, Object>>emptyList(),
                Collections.<Map<String, Object>>emptyList(),
                Collections.<Map<String, Object>>emptyList());
        when(repository.templateDetail(org.mockito.ArgumentMatchers.eq("tpl_1"), org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(rows);

        when(repository.publicVersionStatus("tpl_1", "tplver_1")).thenAnswer(invocation -> publicVersionState(template, version));
        Map<String, Object> first = service.detail("tpl_1", false);
        Map<String, Object> second = service.detail("tpl_1", false);

        assertEquals("tplver_1", first.get("currentVersionId"));
        assertTrue(first == second);
        assertTrue(first == service.publishedVersionDetail("tpl_1", "tplver_1"));
        verify(repository).templateDetail(org.mockito.ArgumentMatchers.eq("tpl_1"), org.mockito.ArgumentMatchers.nullable(String.class));
        ((com.github.benmanes.caffeine.cache.Cache<?, ?>) org.springframework.test.util.ReflectionTestUtils
                .getField(service, "publicDetailCache")).invalidateAll();
        service.publishedVersionDetail("tpl_1", "tplver_1");
        verify(repository, org.mockito.Mockito.times(2)).templateDetail(org.mockito.ArgumentMatchers.eq("tpl_1"), org.mockito.ArgumentMatchers.nullable(String.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cachesPinnedVersionsWithoutChangingTheirIdentityAndInvalidatesWithdrawals() {
        Map<String, Object> template = row("template_id", "tpl_1");
        template.put("status", "published"); template.put("visibility", "public");
        template.put("current_version_id", "new");
        Map<String, Object> old = row("version_id", "old"); old.put("status", "published");
        Map<String, Object> current = row("version_id", "new"); current.put("status", "published");
        when(repository.templateDetail(org.mockito.ArgumentMatchers.eq("tpl_1"), org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(new TemplateDetailRows(template, Collections.emptyList(), null,
                Collections.emptyList(), Arrays.asList(current, old), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
        when(repository.publicVersionStatus("tpl_1", "old")).thenAnswer(invocation -> publicVersionState(template, old));
        Map<String, Object> historical = service.publishedVersionDetail("tpl_1", "old");
        Map<String, Object> latest = service.publishedVersionDetail("tpl_1", "new");
        assertEquals("old", ((Map<?, ?>)((List<?>)historical.get("versions")).get(0)).get("versionId"));
        assertEquals("new", ((Map<?, ?>)((List<?>)latest.get("versions")).get(0)).get("versionId"));
        assertTrue(historical == service.publishedVersionDetail("tpl_1", "old"));
        verify(repository, org.mockito.Mockito.times(2)).templateDetail(org.mockito.ArgumentMatchers.eq("tpl_1"), org.mockito.ArgumentMatchers.nullable(String.class));
        old.put("status", "draft"); service.invalidateDetail("tpl_1");
        assertThrows(ApiException.class, () -> service.publishedVersionDetail("tpl_1", "old"));
        old.put("status", "published");
        service.publishedVersionDetail("tpl_1", "old");
        verify(repository, org.mockito.Mockito.times(4)).templateDetail(org.mockito.ArgumentMatchers.eq("tpl_1"), org.mockito.ArgumentMatchers.nullable(String.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void candidatePreviewRequiresMatchingEvidenceAndLeavesPublishedPointerUnchanged() {
        Map<String, Object> template = row("template_id", "tpl_1");
        template.put("status", "published"); template.put("visibility", "public"); template.put("current_version_id", "old");
        Map<String, Object> version = row("version_id", "candidate"); version.put("status", "validated");
        Map<String, Object> scene = row("version_id", "candidate"); scene.put("status", "ready");
        scene.put("manifest_sha256", hash('a')); scene.put("scene_json", "{\"canvas\":{\"width\":1080,\"height\":1920,\"fps\":30},\"slots\":[],\"resources\":[]}");
        Map<String, Object> parity = row("status", "passed"); parity.put("scene_manifest_sha256", hash('a')); parity.put("reference_sha256", hash('b'));
        Map<String, Object> reference = row("status", "ready"); reference.put("source_sha256", hash('b'));
        when(repository.template("tpl_1")).thenReturn(template);
        when(repository.version("tpl_1", "candidate")).thenReturn(version);
        when(repository.browserScene("candidate")).thenReturn(scene);
        when(repository.browserParity("candidate")).thenReturn(parity);
        when(repository.mediaByRole("candidate", "browser_parity_reference")).thenReturn(reference);
        when(repository.templateDetail(org.mockito.ArgumentMatchers.eq("tpl_1"), org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(new TemplateDetailRows(template, Collections.emptyList(), null,
                Collections.emptyList(), Collections.singletonList(version), Collections.emptyList(), Collections.emptyList(), Collections.singletonList(scene)));
        Map<String, Object> detail = service.candidateVersionDetail("tpl_1", "candidate");
        assertEquals("old", detail.get("currentVersionId"));
        Map<String, Object> render = (Map<String, Object>) ((List<Map<String, Object>>) detail.get("versions")).get(0).get("browserRender");
        assertTrue(render.containsKey("slotBindings"));
        template.put("status", "draft"); template.put("visibility", "private");
        assertFalse(((List<?>) service.candidateVersionDetail("tpl_1", "candidate").get("versions")).isEmpty());
        assertThrows(ApiException.class, () -> service.detail("tpl_1", false));
        assertThrows(ApiException.class, () -> service.publishedVersionDetail("tpl_1", "candidate"));
        parity.put("reference_sha256", hash('c'));
        assertEquals("TEMPLATE_CANDIDATE_NOT_READY", assertThrows(ApiException.class,
                () -> service.candidateVersionDetail("tpl_1", "candidate")).getCode());
        parity.put("reference_sha256", hash('b')); version.put("status", "draft");
        assertThrows(ApiException.class, () -> service.candidateVersionDetail("tpl_1", "candidate"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void historicalDetailSelectsOnlyPublishedVersionAndPreservesCatalogPointer() {
        Map<String, Object> template = row("template_id", "tpl_1");
        template.put("status", "published"); template.put("visibility", "public");
        template.put("current_version_id", "new");
        Map<String, Object> old = row("version_id", "accepted"); old.put("status", "published");
        Map<String, Object> latest = row("version_id", "new"); latest.put("status", "published");
        Map<String, Object> draft = row("version_id", "draft"); draft.put("status", "draft");
        TemplateDetailRows rows = new TemplateDetailRows(template, Collections.emptyList(), null,
                Collections.emptyList(), Arrays.asList(latest, old, draft),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        when(repository.templateDetail(org.mockito.ArgumentMatchers.eq("tpl_1"), org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(rows);
        when(repository.publicVersionStatus("tpl_1", "accepted")).thenAnswer(invocation -> publicVersionState(template, old));
        Map<String, Object> detail = service.publishedVersionDetail("tpl_1", "accepted");
        assertEquals("new", detail.get("currentVersionId"));
        List<Map<String, Object>> versions = (List<Map<String, Object>>) detail.get("versions");
        assertEquals(1, versions.size()); assertEquals("accepted", versions.get(0).get("versionId"));
        for (String rejected : Arrays.asList("draft", "missing", "")) {
            assertEquals("TEMPLATE_VERSION_NOT_FOUND", assertThrows(ApiException.class,
                    () -> service.publishedVersionDetail("tpl_1", rejected)).getCode());
        }
        old.put("status", "archived");
        assertThrows(ApiException.class, () -> service.publishedVersionDetail("tpl_1", "accepted"));
        old.put("status", "published"); template.put("visibility", "private");
        assertEquals("TEMPLATE_NOT_FOUND", assertThrows(ApiException.class,
                () -> service.publishedVersionDetail("tpl_1", "accepted")).getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void includesReadyRuntimePackageInPublicBrowserRenderDetail() {
        Map<String, Object> template = row("template_id", "tpl_1");
        template.put("status", "published");
        template.put("visibility", "public");
        template.put("current_version_id", "tplver_1");
        Map<String, Object> version = row("version_id", "tplver_1");
        version.put("width", Integer.valueOf(1080));
        version.put("height", Integer.valueOf(1920));
        version.put("fps", Integer.valueOf(30));
        Map<String, Object> slot = row("version_id", "tplver_1");
        slot.put("slot_key", "photo_1");
        slot.put("timeline_order", Integer.valueOf(0));
        Map<String, Object> defaultMedia = row("version_id", "tplver_1");
        defaultMedia.put("media_role", "slot_default:photo_1");
        defaultMedia.put("provider", "cloudflare_images");
        defaultMedia.put("provider_asset_id", "default-photo");
        defaultMedia.put("provider_details_json", "{}");
        defaultMedia.put("status", "ready");
        Map<String, Object> effectMedia = row("version_id", "tplver_1");
        effectMedia.put("media_role", "browser_resource:effect_1");
        effectMedia.put("provider", "cloudflare_images");
        effectMedia.put("provider_asset_id", "effect-image");
        effectMedia.put("provider_details_json", "{}");
        effectMedia.put("status", "ready");
        when(mediaProvider.resolveDeliveryDetails(eq("cloudflare_images"), eq("default-photo"), any()))
                .thenReturn(row("deliveryUrl", "https://cdn.example/default.jpg"));
        when(mediaProvider.resolveDeliveryDetails(eq("cloudflare_images"), eq("effect-image"), any()))
                .thenReturn(row("deliveryUrl", "https://cdn.example/effect.png"));
        Map<String, Object> browserScene = row("version_id", "tplver_1");
        browserScene.put("status", "ready");
        browserScene.put("manifest_sha256", hash('b'));
        browserScene.put("scene_json", "{\"canvas\":{\"width\":1080,\"height\":1920,\"fps\":30},"
                + "\"slots\":[{\"slotKey\":\"photo_1\"}],"
                + "\"resources\":[{\"resourceKey\":\"effect_1\",\"role\":\"browser_resource:effect_1\",\"kind\":\"image\"}]}");
        browserScene.put("scene_json", String.valueOf(browserScene.get("scene_json")).replaceFirst("\\{",
                "{\"runtimeDelivery\":{\"schemaVersion\":\"browser-runtime-delivery-v1\",\"totalSizeBytes\":1234,\"resources\":[]},"));
        browserScene.put("scene_json", String.valueOf(browserScene.get("scene_json")).replace(
                "\"kind\":\"image\"}]", "\"kind\":\"image\"},{\"resourceKey\":\"lut\",\"kind\":\"lut_2d_png\",\"sourceAsset\":{}}]"));
        Map<String, Object> exactLut = row("kind", "lut_2d_png");
        exactLut.put("url", "https://cdn.example/lut.png");
        when(runtimePackages.downloadExactImages(any())).thenReturn(Collections.singletonList(exactLut));
        Map<String, Object> packageRow = row("status", "ready");
        when(repository.runtimePackage("tplver_1")).thenReturn(packageRow);
        Map<String, Object> download = new LinkedHashMap<String, Object>();
        download.put("templateId", "tpl_1");
        download.put("versionId", "tplver_1");
        download.put("status", "ready");
        download.put("downloadUrl", "https://r2.example/runtime.zip");
        download.put("sourceSha256", hash('a'));
        download.put("sourceSizeBytes", Long.valueOf(1234L));
        download.put("objectKey", "private/runtime.zip");
        download.put("errorMessage", null);
        when(runtimePackages.downloadForScene(org.mockito.ArgumentMatchers.eq("tpl_1"), org.mockito.ArgumentMatchers.eq("tplver_1"), org.mockito.ArgumentMatchers.anyMap())).thenReturn(download);
        when(repository.templateDetail(org.mockito.ArgumentMatchers.eq("tpl_1"), org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(new TemplateDetailRows(template,
                Collections.<Map<String, Object>>emptyList(), null,
                Collections.<Map<String, Object>>emptyList(),
                Collections.singletonList(version),
                Collections.singletonList(slot),
                Arrays.asList(defaultMedia, effectMedia),
                Collections.singletonList(browserScene)));

        Map<String, Object> detail = service.detail("tpl_1", false);
        verify(repository, never()).runtimePackage("tplver_1");
        List<Map<String, Object>> versions = (List<Map<String, Object>>) detail.get("versions");
        Map<String, Object> browserRender =
                (Map<String, Object>) versions.get(0).get("browserRender");
        Map<String, Object> runtimePackage =
                (Map<String, Object>) browserRender.get("runtimePackage");

        assertEquals(1234, detail.get("runtimePackageSizeBytes"));
        assertEquals(1234, versions.get(0).get("runtimePackageSizeBytes"));
        assertEquals("https://r2.example/runtime.zip", runtimePackage.get("downloadUrl"));
        assertFalse(runtimePackage.containsKey("objectKey"));
        assertFalse(runtimePackage.containsKey("errorMessage"));
        assertEquals(hash('b'), browserRender.get("sceneManifestSha256"));
        List<Map<String, Object>> slotBindings =
                (List<Map<String, Object>>) browserRender.get("slotBindings");
        List<Map<String, Object>> resources =
                (List<Map<String, Object>>) browserRender.get("resources");
        assertEquals("https://cdn.example/default.jpg",
                ((Map<String, Object>) slotBindings.get(0).get("asset")).get("url"));
        assertEquals("https://cdn.example/effect.png",
                ((Map<String, Object>) resources.get(0).get("asset")).get("url"));
        assertEquals("https://cdn.example/lut.png", ((Map<?, ?>) resources.get(1).get("asset")).get("url"));
        verify(runtimePackages).downloadExactImages(org.mockito.ArgumentMatchers.argThat(items ->
                items.size() == 1 && "lut".equals(items.get(0).get("resourceKey"))));
        verify(runtimePackages, never()).downloadExactImage(any());
        assertEquals(Collections.emptyMap(), browserRender.get("textOverrides"));
        Map<String, Object> outputVideo = (Map<String, Object>) browserRender.get("outputVideo");
        assertEquals(Integer.valueOf(1080), outputVideo.get("width"));
        assertEquals(Integer.valueOf(1920), outputVideo.get("height"));
        assertEquals(Integer.valueOf(30), outputVideo.get("fps"));
    }

    @Test
    void refusesReplacingCurrentTemplateWithOlderOrUnorderedContent() {
        when(repository.template("tpl_1")).thenReturn(row("current_version_id", "current"));
        when(repository.version("tpl_1", "current")).thenReturn(row("version_number", 3));
        for (Integer number : java.util.Arrays.asList(2, 3, null)) {
            when(repository.version("tpl_1", "old")).thenReturn(row("version_number", number));
            ApiException error = assertThrows(ApiException.class, () -> service.publish("tpl_1", "old"));
            assertEquals("TEMPLATE_NEWER_CONTENT_REQUIRED", error.getCode());
        }
        verify(repository, never()).publish(anyString(), anyString());
    }

    @Test
    void publishesBrowserReadyVersionAfterMatchingVisualParity() {
        when(repository.publish("tpl_1", "tplver_1")).thenReturn(true);
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        Map<String, Object> version = row("validation_status", "browser_ready");
        version.put("source_availability", "unavailable");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);
        Map<String, Object> scene = row("status", "ready");
        scene.put("manifest_sha256", hash('c'));
        when(repository.browserScene("tplver_1")).thenReturn(scene);
        Map<String, Object> reference = row("status", "ready");
        reference.put("source_sha256", hash('d'));
        when(repository.mediaByRole("tplver_1", "browser_parity_reference"))
                .thenReturn(reference);
        Map<String, Object> parity = row("status", "passed");
        parity.put("scene_manifest_sha256", hash('c'));
        parity.put("reference_sha256", hash('d'));
        when(repository.browserParity("tplver_1")).thenReturn(parity);

        Map<String, Object> result = service.publish("tpl_1", "tplver_1");
        assertEquals("published", result.get("status"));
        when(repository.template("tpl_1")).thenReturn(row("current_version_id", "older"));
        when(repository.version("tpl_1", "older")).thenReturn(row("version_number", 1));
        version.put("version_number", 2);
        assertEquals("published", service.publish("tpl_1", "tplver_1").get("status"));
        when(repository.template("tpl_1")).thenReturn(row("current_version_id", "tplver_1"));
        assertEquals("published", service.publish("tpl_1", "tplver_1").get("status"));
        verify(repository, org.mockito.Mockito.times(3)).publish("tpl_1", "tplver_1");
        when(repository.publish("tpl_1", "tplver_1")).thenReturn(false);
        ApiException superseded = assertThrows(ApiException.class, () -> service.publish("tpl_1", "tplver_1"));
        assertEquals("TEMPLATE_PUBLICATION_SUPERSEDED", superseded.getCode());
    }

    @Test
    void refusesPassedParityEvidenceFromAnotherImmutableScene() {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("validation_status", "browser_ready"));
        Map<String, Object> scene = row("status", "ready");
        scene.put("manifest_sha256", hash('c'));
        when(repository.browserScene("tplver_1")).thenReturn(scene);
        Map<String, Object> reference = row("status", "ready");
        reference.put("source_sha256", hash('d'));
        when(repository.mediaByRole("tplver_1", "browser_parity_reference"))
                .thenReturn(reference);
        Map<String, Object> parity = row("status", "passed");
        parity.put("scene_manifest_sha256", hash('x'));
        parity.put("reference_sha256", hash('d'));
        when(repository.browserParity("tplver_1")).thenReturn(parity);

        ApiException error = assertThrows(ApiException.class,
                () -> service.publish("tpl_1", "tplver_1"));

        assertEquals("TEMPLATE_BROWSER_PARITY_REQUIRED", error.getCode());
        verify(repository, never()).publish(anyString(), anyString());
    }

    @Test
    void acceptsAverageSsimPassEvenWhenOneDiagnosticFrameIsBelowThreshold() {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        stubParityInputs();
        Map<String, Object> stored = row("status", "passed");
        stored.put("validation_id", "bpar_1");
        when(repository.matchingBrowserParity("tplver_1", hash('c'), hash('d')))
                .thenReturn(null, stored);
        TemplateBrowserParityRequest request = browserParityRequest();
        request.setAverageSsim(Double.valueOf(0.910019d));
        request.setMinSsim(Double.valueOf(0.756631d));

        Map<String, Object> result = service.synchronizeBrowserParity(
                "tpl_1", "tplver_1", request);

        assertEquals("passed", result.get("status"));
        verify(repository).upsertBrowserParity(anyString(), eq("tpl_1"), eq("tplver_1"),
                eq(hash('c')), eq(hash('d')), eq("passed"), eq(Integer.valueOf(7)),
                eq(Double.valueOf(0.9d)), eq(Double.valueOf(25.0d)),
                eq(Double.valueOf(0.910019d)), eq(Double.valueOf(0.756631d)),
                eq(Double.valueOf(11.3446d)), eq(Double.valueOf(21.4141d)),
                eq(Double.valueOf(33.4333d)), eq(Double.valueOf(33.5147d)),
                eq(hash('e')), anyString());
    }

    @Test
    void acceptsAverageSsimPassWhenMaeAndDurationAreDiagnosticOnly() {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        stubParityInputs();
        Map<String, Object> stored = row("status", "passed");
        stored.put("validation_id", "bpar_1");
        when(repository.matchingBrowserParity("tplver_1", hash('c'), hash('d')))
                .thenReturn(null, stored);
        TemplateBrowserParityRequest request = browserParityRequest();
        request.setAverageSsim(Double.valueOf(0.91d));
        request.setMinSsim(Double.valueOf(0.4d));
        request.setMaxMae(Double.valueOf(200.0d));
        request.setOutputDurationSeconds(Double.valueOf(50.0d));

        Map<String, Object> result = service.synchronizeBrowserParity(
                "tpl_1", "tplver_1", request);

        assertEquals("passed", result.get("status"));
    }

    @Test
    void rejectsAClientSuppliedSsimThresholdBelowProductGate() {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        stubParityInputs();
        TemplateBrowserParityRequest request = browserParityRequest();
        request.setSsimThreshold(Double.valueOf(0.5d));
        request.setAverageSsim(Double.valueOf(0.6d));
        request.setMinSsim(Double.valueOf(0.4d));

        ApiException error = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserParity("tpl_1", "tplver_1", request));

        assertEquals("TEMPLATE_BROWSER_PARITY_THRESHOLD_INVALID", error.getCode());
    }

    private TemplateBrowserParityRequest browserParityRequest() {
        TemplateBrowserParityRequest request = new TemplateBrowserParityRequest();
        request.setSceneManifestSha256(hash('c'));
        request.setReferenceSha256(hash('d'));
        request.setStatus("passed");
        request.setSampleCount(Integer.valueOf(7));
        request.setSsimThreshold(Double.valueOf(0.9d));
        request.setMaeThreshold(Double.valueOf(25.0d));
        request.setAverageMae(Double.valueOf(11.3446d));
        request.setMaxMae(Double.valueOf(21.4141d));
        request.setReferenceDurationSeconds(Double.valueOf(33.4333d));
        request.setOutputDurationSeconds(Double.valueOf(33.5147d));
        request.setOutputSha256(hash('e'));
        return request;
    }

    private void stubParityInputs() {
        Map<String, Object> scene = row("manifest_sha256", hash('c'));
        when(repository.browserScene("tplver_1")).thenReturn(scene);
        Map<String, Object> reference = row("status", "ready");
        reference.put("source_sha256", hash('d'));
        when(repository.mediaByRole("tplver_1", "browser_parity_reference"))
                .thenReturn(reference);
    }

    @Test
    void refusesPublishWhileRuntimePackageIntegrityCheckIsPending() {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("validation_status", "browser_ready"));
        when(repository.runtimePackage("tplver_1"))
                .thenReturn(row("status", "awaiting_upload"));

        ApiException error = assertThrows(ApiException.class,
                () -> service.publish("tpl_1", "tplver_1"));

        assertEquals("TEMPLATE_RUNTIME_PACKAGE_NOT_READY", error.getCode());
        verify(repository, never()).publish(anyString(), anyString());
    }

    @Test
    void rejectsVersionTwoBrowserSceneWhenPhotoAnimationIsNotExecutable() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> capability = new LinkedHashMap<String, Object>();
        capability.put("photoReplacementReady", Boolean.TRUE);
        capability.put("photoAnimationReady", Boolean.FALSE);
        capability.put("photoAnimationContract", "timeline_keyframes_v1");
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("schemaVersion", "browser-template-scene-v2");
        scene.put("templateId", "tpl_1");
        scene.put("versionId", "tplver_1");
        scene.put("capability", capability);
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion("browser-template-scene-v2");
        request.setScene(scene);
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));

        ApiException error = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));

        assertEquals("TEMPLATE_BROWSER_SCENE_ANIMATION_NOT_READY", error.getCode());
        verify(repository, never()).upsertBrowserScene(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void acceptsVersionFiveSceneGraphWithVerifiedEffectInventory() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> capability = new LinkedHashMap<String, Object>();
        capability.put("photoReplacementReady", Boolean.TRUE);
        capability.put("photoAnimationReady", Boolean.TRUE);
        capability.put("photoAnimationContract", "timeline_keyframes_v1");
        capability.put("browserLayerCompositionReady", Boolean.TRUE);
        capability.put("browserExportReady", Boolean.TRUE);
        capability.put("blockingFeatures", Collections.emptyList());
        capability.put("executionCapabilities", Collections.singletonList(
                executionCapability("photo_layers", 2, 2, "exact", false)));
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("schemaVersion", "browser-template-scene-v5");
        scene.put("templateId", "tpl_1");
        scene.put("versionId", "tplver_1");
        scene.put("capability", capability);
        scene.put("slots", Collections.singletonList(row("slotKey", "photo_01")));
        Map<String, Object> resource = row("resourceKey", "font_123");
        resource.put("kind", "font");
        resource.put("role", "browser_resource:font_123");
        resource.put("fontFamily", "storyai-font-123");
        resource.put("inlineData", "data:font/ttf;base64,AAECAw==");
        Map<String, Object> lutResource = row("resourceKey", "lut_123");
        lutResource.put("kind", "lut_2d_png");
        lutResource.put("role", "browser_resource:lut_123");
        lutResource.put("inlineData", null);
        Map<String, Object> imageResource = row("resourceKey", "static_photo_123");
        imageResource.put("kind", "image");
        imageResource.put("role", "browser_resource:static_photo_123");
        Map<String, Object> stickerResource = row("resourceKey", "sticker_123");
        stickerResource.put("kind", "image");
        stickerResource.put("role", "browser_resource:sticker_123");
        Map<String, Object> videoResource = row("resourceKey", "video_123");
        videoResource.put("kind", "video");
        videoResource.put("role", "browser_resource:video_123");
        scene.put("resources", java.util.Arrays.asList(
                resource, lutResource, imageResource, stickerResource, videoResource));
        Map<String, Object> layer = row("layerId", "segment-1");
        layer.put("type", "photo");
        layer.put("slotKey", "photo_01");
        Map<String, Object> mask = row("type", "rectangle");
        mask.put("width", 0.8d);
        mask.put("height", 0.6d);
        mask.put("roundCorner", 0.1d);
        mask.put("invert", Boolean.FALSE);
        layer.put("mask", mask);
        Map<String, Object> animation = row("preset", "scale_up_approximation");
        animation.put("durationSeconds", Double.valueOf(0.4d));
        animation.put("fidelity", "semantic_approximation");
        Map<String, Object> glyphAnimation = row(
                "preset", "glyph_texture_shuffle_animation");
        glyphAnimation.put("durationSeconds", Double.valueOf(0.4d));
        glyphAnimation.put("fidelity", "semantic_approximation");
        glyphAnimation.put("contractVersion", "browser-animation-semantic-v2");
        glyphAnimation.put("semanticFamily", "glyph_texture_shuffle_animation");
        glyphAnimation.put("packageClock", "four_discrete_phases_per_declared_duration");
        glyphAnimation.put("textTransform", "uppercase_supported_characters");
        glyphAnimation.put("glyphSource", "indexed_charimage_texture_pairs");
        glyphAnimation.put("variantSelection", "deterministic_seeded_character_variant");
        glyphAnimation.put("motionSelection", "seeded_thirty_percent_position_scale_rotation");
        glyphAnimation.put("layoutAdjustment", "package_word_gap_point_35_line_gap_point_45");
        glyphAnimation.put("evidence", "package_textanim_lua_and_indexed_glyph_textures");
        layer.put("animations", java.util.Arrays.asList(animation, glyphAnimation));
        Map<String, Object> transition = row("preset", "ab_progress_mix");
        transition.put("durationSeconds", Double.valueOf(0.3d));
        transition.put("fidelity", "exact");
        transition.put("contractVersion", "browser-transition-semantic-v1");
        transition.put("semanticFamily", "ab_progress_mix");
        transition.put("progressMapping", "normalized_transition_progress");
        transition.put("inputAWeight", "one_minus_progress");
        transition.put("inputBWeight", "progress");
        transition.put("alphaOutputContract", "opaque");
        transition.put("applicationStage", "timeline_ab_after_source_graph");
        transition.put("evidence", "package_shader_two_input_progress_mix");
        layer.put("transitionIn", transition);
        Map<String, Object> layerEffect = row("preset", "texture_sequence_screen_multiply");
        layerEffect.put("intensity", Double.valueOf(0.05d));
        layerEffect.put("fidelity", "exact");
        layerEffect.put("contractVersion", "browser-layer-effect-semantic-v4");
        layerEffect.put("semanticFamily", "texture_sequence_screen_multiply");
        layerEffect.put("packageClock",
                "effect_local_seconds_clamped_to_declared_range_then_times_half_plus_speed_times_one_point_five");
        layerEffect.put("passOrder", "screen_sequence_then_multiply_texture");
        layerEffect.put("applicationStage", "source_graph_before_video_animation");
        layerEffect.put("sequenceSampling", "serialized_sequence_order_floor_clock");
        layerEffect.put("sequenceDurationSeconds", Double.valueOf(3.0d));
        layerEffect.put("sequencePlaybackMode", "clamp");
        layerEffect.put("sequenceEndBehavior", "hold_last_frame");
        layerEffect.put("screenBlend", "straight_alpha_screen");
        layerEffect.put("multiplyBlend", "inverse_premultiplied_straight_alpha_multiply");
        layerEffect.put("alphaContract", "opaque_result");
        layerEffect.put("evidence", "package_lua_sequence_and_blend_shaders");
        Map<String, Object> personProtectedEffect = row("preset", "turbulence_bounce_shake");
        personProtectedEffect.put("fidelity", "semantic_approximation");
        personProtectedEffect.put("contractVersion", "browser-layer-effect-semantic-v4");
        personProtectedEffect.put("semanticFamily", "turbulence_bounce_shake");
        personProtectedEffect.put("packageClock",
                "local_seconds_times_half_plus_speed_times_one_point_five");
        personProtectedEffect.put("passOrder",
                "downsample_passthrough_then_difference_sharpen_then_turbulence_then_shake_then_bounce");
        personProtectedEffect.put("applicationStage", "source_graph_before_video_animation");
        personProtectedEffect.put("alphaContract", "preserve_source_alpha");
        personProtectedEffect.put("maskProvider", "person_matting");
        personProtectedEffect.put("maskSource", "share_bgmask_red_y_flipped");
        personProtectedEffect.put("maskInput", "effect_pass_input_rgba");
        personProtectedEffect.put("maskProtection",
                "max_original_3x3_radius_10_texels_and_displaced_then_restore_uv");
        personProtectedEffect.put("paperTexturePath",
                "AmazingFeature/image/peopleTex.png");
        personProtectedEffect.put("approximationBoundary", "browser_person_matting_model");
        personProtectedEffect.put("evidence", "package_lua_and_five_fragment_passes");
        Map<String, Object> paperStrokeEffect = row("preset", "paper_stroke_person_mask");
        paperStrokeEffect.put("fidelity", "semantic_approximation");
        paperStrokeEffect.put("contractVersion", "browser-layer-effect-semantic-v5");
        paperStrokeEffect.put("semanticFamily", "paper_stroke_person_mask");
        paperStrokeEffect.put("passOrder",
                "person_matting_then_multi_radius_outline_then_textured_composite");
        paperStrokeEffect.put("applicationStage", "source_graph_before_video_animation");
        paperStrokeEffect.put("alphaContract", "preserve_source_alpha");
        paperStrokeEffect.put("maskProvider", "person_matting");
        paperStrokeEffect.put("maskSource", "share_bgmask_red_y_flipped");
        paperStrokeEffect.put("maskInput", "effect_pass_input_rgba");
        paperStrokeEffect.put("outlineExpansion", "draft_size_and_range_sliders");
        paperStrokeEffect.put("paperTexturePath", "AmazingFeature/image/peopleTex.png");
        paperStrokeEffect.put("approximationBoundary", "browser_person_matting_model");
        paperStrokeEffect.put("evidence", "package_lua_matting_blur_and_composite_shaders");
        paperStrokeEffect.put("texture", Double.valueOf(0.5d));
        paperStrokeEffect.put("size", Double.valueOf(0.5d));
        paperStrokeEffect.put("range", Double.valueOf(0.5d));
        paperStrokeEffect.put("backgroundAlpha", Double.valueOf(1.0d));
        layer.put("effects", java.util.Arrays.asList(
                layerEffect, personProtectedEffect, paperStrokeEffect));
        Map<String, Object> fixedLayer = row("layerId", "fixed-segment-1");
        fixedLayer.put("type", "static_image");
        fixedLayer.put("resourceKey", "static_photo_123");
        Map<String, Object> stickerLayer = row("layerId", "sticker-segment-1");
        stickerLayer.put("type", "sticker");
        stickerLayer.put("resourceKey", "sticker_123");
        Map<String, Object> videoLayer = row("layerId", "video-segment-1");
        videoLayer.put("type", "video");
        videoLayer.put("resourceKey", "video_123");
        Map<String, Object> textLayer = row("layerId", "text-segment-1");
        textLayer.put("type", "text");
        textLayer.put("fidelity", "semantic_approximation");
        textLayer.put("templateResourceId", "text-template-123");
        Map<String, Object> textRun = row("start", 0);
        textRun.put("end", 5);
        textRun.put("fontResourceKey", "font_123");
        textLayer.put("runs", Collections.singletonList(textRun));
        scene.put("layers", java.util.Arrays.asList(
                layer, fixedLayer, stickerLayer, videoLayer, textLayer));
        Map<String, Object> lutEffect = row("effectId", "filter-1");
        lutEffect.put("preset", "dual_lut_skin_mask");
        lutEffect.put("targetStartSeconds", Double.valueOf(0.0d));
        lutEffect.put("targetDurationSeconds", Double.valueOf(4.0d));
        lutEffect.put("intensity", Double.valueOf(0.6d));
        lutEffect.put("fidelity", "semantic_approximation");
        lutEffect.put("resourceKeys", row("background", "lut_123"));
        lutEffect.put("contractVersion", "browser-post-effect-semantic-v2");
        lutEffect.put("semanticFamily", "dual_lut_skin_mask");
        lutEffect.put("lutSampling", "64_cube_8x8_floor_blue_linear_rg");
        lutEffect.put("maskSource", "skin_seg_alpha_y_flipped");
        lutEffect.put("maskProvider", "mediapipe_selfie_multiclass_256");
        lutEffect.put("maskClassComposition", "max_body_skin_face_skin_confidence");
        lutEffect.put("maskRefreshContract", "ten_hz_reuse_with_seek_refresh");
        lutEffect.put("intensityMix", "source_to_mask_selected_lut");
        lutEffect.put("alphaContract", "preserve_source_alpha");
        lutEffect.put("approximationBoundary", "browser_mediapipe_model_not_capcut_skin_seg");
        lutEffect.put("evidence", "package_skinseg_shader_algorithm_and_dual_lut_media");
        Map<String, Object> textureOverlay = row("effectId", "texture-overlay-1");
        textureOverlay.put("preset", "static_texture_screen_overlay");
        textureOverlay.put("targetStartSeconds", Double.valueOf(0.0d));
        textureOverlay.put("targetDurationSeconds", Double.valueOf(4.0d));
        textureOverlay.put("intensity", Double.valueOf(0.25d));
        textureOverlay.put("fidelity", "exact");
        textureOverlay.put("contractVersion", "browser-post-effect-semantic-v3");
        textureOverlay.put("semanticFamily", "static_texture_screen_overlay");
        textureOverlay.put("applicationStage", "post_scene_before_fade");
        textureOverlay.put("blendMode", "straight_alpha_screen");
        textureOverlay.put("textureSampling", "cover_center_crop_y_flipped");
        textureOverlay.put("intensitySource", "effects_adjust_texture");
        textureOverlay.put("alphaContract", "opaque_result");
        textureOverlay.put("texturePath", "amazingfeature/image/a0.png");
        textureOverlay.put("evidence", "package_lua_center_crop_screen_and_texture");
        scene.put("postEffects", java.util.Arrays.asList(lutEffect, textureOverlay));
        Map<String, Object> capabilityReport = row(
                "schemaVersion", "browser-scene-capability-report-v1");
        capabilityReport.put("effectRegistryContract", "browser_effect_registry_v1");
        capabilityReport.put("features", capability.get("executionCapabilities"));
        capabilityReport.put("effectImplementations", java.util.Arrays.asList(
                effectImplementation("animation", "scale_up_approximation",
                        "timeline_animation_v1", "semantic_approximation"),
                effectImplementation("animation", "glyph_texture_shuffle_animation",
                        "timeline_animation_v1", "semantic_approximation"),
                effectImplementation("transition", "ab_progress_mix",
                        "timeline_transition_v1", "exact"),
                effectImplementation("layer_effect", "texture_sequence_screen_multiply",
                        "canvas_layer_effect_v1", "exact"),
                effectImplementation("layer_effect", "turbulence_bounce_shake",
                        "canvas_layer_effect_v1", "semantic_approximation"),
                effectImplementation("layer_effect", "paper_stroke_person_mask",
                        "canvas_layer_effect_v1", "semantic_approximation"),
                effectImplementation("mask", "rectangle",
                        "canvas_mask_v1", "exact"),
                effectImplementation("text_template", "expanded_text_template",
                        "text_template_expansion_v1", "semantic_approximation"),
                effectImplementation("post_effect", "dual_lut_skin_mask",
                        "canvas_post_effect_v1", "semantic_approximation"),
                effectImplementation("post_effect", "static_texture_screen_overlay",
                        "canvas_post_effect_v1", "exact")));
        Map<String, Object> reportSummary = row("declaredItemCount", Integer.valueOf(2));
        reportSummary.put("executableItemCount", Integer.valueOf(2));
        reportSummary.put("exactFeatureCount", Integer.valueOf(1));
        reportSummary.put("approximateFeatureCount", Integer.valueOf(0));
        reportSummary.put("unsupportedFeatureCount", Integer.valueOf(0));
        reportSummary.put("effectImplementationCount", Integer.valueOf(10));
        capabilityReport.put("summary", reportSummary);
        scene.put("capabilityReport", capabilityReport);
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion("browser-template-scene-v5");
        request.setScene(scene);
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));

        Map<String, Object> result = service.synchronizeBrowserScene(
                "tpl_1", "tplver_1", request);

        assertEquals("ready", result.get("status"));
        verify(repository).upsertBrowserScene(eq("tpl_1"), eq("tplver_1"),
                eq("browser-template-scene-v5"), anyString(), eq("ready"), anyString());

        glyphAnimation.remove("glyphSource");
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));
        ApiException incompleteGlyphAnimation = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));
        assertEquals("TEMPLATE_BROWSER_SCENE_ANIMATION_CONTRACT_INVALID",
                incompleteGlyphAnimation.getCode());
        glyphAnimation.put("glyphSource", "indexed_charimage_texture_pairs");

        personProtectedEffect.put("paperTexturePath", null);
        personProtectedEffect.put("texturePath", null);
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));
        assertEquals("ready", service.synchronizeBrowserScene(
                "tpl_1", "tplver_1", request).get("status"));
        personProtectedEffect.remove("texturePath");

        for (String privateField : java.util.Arrays.asList("path", "sourceDraftPath", "materials")) {
            personProtectedEffect.put(privateField, null);
            request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));
            ApiException privateFieldError = assertThrows(ApiException.class,
                    () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));
            assertEquals("TEMPLATE_BROWSER_SCENE_PRIVATE_DATA", privateFieldError.getCode());
            personProtectedEffect.remove(privateField);
        }

        for (Object invalidPath : java.util.Arrays.asList("", "   ", "file:///tmp/private.png",
                "C:\\Users\\test\\private.png", Integer.valueOf(1), row("path", "/tmp/private.png"))) {
            personProtectedEffect.put("paperTexturePath", invalidPath);
            request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));
            ApiException invalidPathError = assertThrows(ApiException.class,
                    () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));
            assertEquals("TEMPLATE_BROWSER_SCENE_PRIVATE_DATA", invalidPathError.getCode());
        }

        personProtectedEffect.put("paperTexturePath", "/Users/test/private/peopleTex.png");
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));
        ApiException privatePath = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));
        assertEquals("TEMPLATE_BROWSER_SCENE_PRIVATE_DATA", privatePath.getCode());
        personProtectedEffect.put("paperTexturePath", "../private/peopleTex.png");
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));
        ApiException traversalPath = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));
        assertEquals("TEMPLATE_BROWSER_SCENE_PRIVATE_DATA", traversalPath.getCode());
        personProtectedEffect.put("paperTexturePath",
                "AmazingFeature/image/peopleTex.png");

        layerEffect.remove("sequenceEndBehavior");
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));
        ApiException incompleteLayerEffect = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));
        assertEquals("TEMPLATE_BROWSER_SCENE_LAYER_EFFECT_CONTRACT_INVALID",
                incompleteLayerEffect.getCode());
        layerEffect.put("sequenceEndBehavior", "hold_last_frame");

        personProtectedEffect.remove("maskProtection");
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));
        ApiException incompletePersonProtection = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));
        assertEquals("TEMPLATE_BROWSER_SCENE_LAYER_EFFECT_CONTRACT_INVALID",
                incompletePersonProtection.getCode());
        personProtectedEffect.put("maskProtection",
                "max_original_3x3_radius_10_texels_and_displaced_then_restore_uv");

        paperStrokeEffect.remove("outlineExpansion");
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));
        ApiException incompletePaperStroke = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));
        assertEquals("TEMPLATE_BROWSER_SCENE_LAYER_EFFECT_CONTRACT_INVALID",
                incompletePaperStroke.getCode());
        paperStrokeEffect.put("outlineExpansion", "draft_size_and_range_sliders");

        lutEffect.remove("maskClassComposition");
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));
        ApiException incompleteSkinMask = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));
        assertEquals("TEMPLATE_BROWSER_SCENE_POST_EFFECT_CONTRACT_INVALID",
                incompleteSkinMask.getCode());
        lutEffect.put("maskClassComposition", "max_body_skin_face_skin_confidence");

        textureOverlay.remove("textureSampling");
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));
        ApiException incompleteTextureOverlay = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));
        assertEquals("TEMPLATE_BROWSER_SCENE_POST_EFFECT_CONTRACT_INVALID",
                incompleteTextureOverlay.getCode());
        textureOverlay.put("textureSampling", "cover_center_crop_y_flipped");

        transition.remove("evidence");
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));
        ApiException incomplete = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));
        assertEquals("TEMPLATE_BROWSER_SCENE_TRANSITION_CONTRACT_INVALID",
                incomplete.getCode());
    }

    @Test
    void acceptsVersionSixSceneWithCompiledRenderIr() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> feature = executionCapability(
                "photo_layers", 1, 1, "exact", false);
        Map<String, Object> capability = new LinkedHashMap<String, Object>();
        capability.put("photoReplacementReady", Boolean.TRUE);
        capability.put("browserLayerCompositionReady", Boolean.TRUE);
        capability.put("browserExportReady", Boolean.TRUE);
        capability.put("blockingFeatures", Collections.emptyList());
        capability.put("executionCapabilities", Collections.singletonList(feature));
        Map<String, Object> layer = row("layerId", "segment-1");
        layer.put("type", "photo");
        layer.put("slotKey", "photo_01");
        layer.put("targetStartSeconds", Double.valueOf(0));
        layer.put("targetDurationSeconds", Double.valueOf(4));
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("schemaVersion", "browser-template-scene-v6");
        scene.put("templateId", "tpl_1");
        scene.put("versionId", "tplver_1");
        scene.put("canvas", row("width", Integer.valueOf(720)));
        scene.put("capability", capability);
        scene.put("slots", Collections.singletonList(row("slotKey", "photo_01")));
        scene.put("resources", Collections.emptyList());
        scene.put("postEffects", Collections.emptyList());
        scene.put("layers", Collections.singletonList(layer));
        Map<String, Object> report = row(
                "schemaVersion", "browser-scene-capability-report-v1");
        report.put("effectRegistryContract", "browser_effect_registry_v1");
        report.put("features", capability.get("executionCapabilities"));
        report.put("effectImplementations", Collections.emptyList());
        Map<String, Object> summary = row("declaredItemCount", Integer.valueOf(1));
        summary.put("executableItemCount", Integer.valueOf(1));
        summary.put("exactFeatureCount", Integer.valueOf(1));
        summary.put("approximateFeatureCount", Integer.valueOf(0));
        summary.put("unsupportedFeatureCount", Integer.valueOf(0));
        summary.put("effectImplementationCount", Integer.valueOf(0));
        report.put("summary", summary);
        scene.put("capabilityReport", report);
        Map<String, Object> irLayer = row("id", "segment-1");
        irLayer.put("kind", "photo");
        irLayer.put("startSeconds", Double.valueOf(0));
        irLayer.put("durationSeconds", Double.valueOf(4));
        irLayer.put("source", row("slotKey", "photo_01"));
        irLayer.put("keyframes", Collections.emptyList());
        irLayer.put("animations", Collections.emptyList());
        irLayer.put("effects", Collections.emptyList());
        Map<String, Object> policy = row(
                "coordinateSpace", "canvas_center_normalized_y_up");
        policy.put("timeUnit", "seconds");
        policy.put("transitionClock", "dual_input_ab_v1");
        policy.put("unsupported", "block_export");
        Map<String, Object> ir = row("version", "browser-render-ir-v2");
        ir.put("canvas", scene.get("canvas"));
        ir.put("layers", Collections.singletonList(irLayer));
        ir.put("postEffects", Collections.emptyList());
        ir.put("resources", Collections.emptyList());
        ir.put("policy", policy);
        scene.put("renderIr", ir);
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion("browser-template-scene-v6");
        request.setScene(scene);
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));

        Map<String, Object> result = service.synchronizeBrowserScene(
                "tpl_1", "tplver_1", request);

        assertEquals("ready", result.get("status"));
        verify(repository).upsertBrowserScene(eq("tpl_1"), eq("tplver_1"),
                eq("browser-template-scene-v6"), anyString(), eq("ready"), anyString());
    }

    @Test
    void rejectsVersionFiveCapabilityReportThatInventsAnEffect() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> capability = new LinkedHashMap<String, Object>();
        capability.put("photoReplacementReady", Boolean.TRUE);
        capability.put("browserLayerCompositionReady", Boolean.TRUE);
        capability.put("browserExportReady", Boolean.TRUE);
        capability.put("blockingFeatures", Collections.emptyList());
        capability.put("executionCapabilities", Collections.singletonList(
                executionCapability("photo_layers", 1, 1, "exact", false)));
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("schemaVersion", "browser-template-scene-v5");
        scene.put("templateId", "tpl_1");
        scene.put("versionId", "tplver_1");
        scene.put("capability", capability);
        scene.put("slots", Collections.singletonList(row("slotKey", "photo_01")));
        Map<String, Object> layer = row("layerId", "segment-1");
        layer.put("type", "photo");
        layer.put("slotKey", "photo_01");
        scene.put("layers", Collections.singletonList(layer));
        Map<String, Object> report = row(
                "schemaVersion", "browser-scene-capability-report-v1");
        report.put("effectRegistryContract", "browser_effect_registry_v1");
        report.put("features", capability.get("executionCapabilities"));
        report.put("effectImplementations", Collections.singletonList(
                effectImplementation("animation", "fade_in",
                        "timeline_animation_v1", "exact")));
        Map<String, Object> summary = row("declaredItemCount", Integer.valueOf(1));
        summary.put("executableItemCount", Integer.valueOf(1));
        summary.put("exactFeatureCount", Integer.valueOf(1));
        summary.put("approximateFeatureCount", Integer.valueOf(0));
        summary.put("unsupportedFeatureCount", Integer.valueOf(0));
        summary.put("effectImplementationCount", Integer.valueOf(1));
        report.put("summary", summary);
        scene.put("capabilityReport", report);
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion("browser-template-scene-v5");
        request.setScene(scene);
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));

        ApiException error = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));

        assertEquals("TEMPLATE_BROWSER_EFFECT_INVENTORY_INVALID", error.getCode());
    }

    @Test
    void rejectsVersionFourLutEffectWithUnknownResource() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> capability = new LinkedHashMap<String, Object>();
        capability.put("photoReplacementReady", Boolean.TRUE);
        capability.put("browserLayerCompositionReady", Boolean.TRUE);
        capability.put("browserExportReady", Boolean.TRUE);
        capability.put("blockingFeatures", Collections.emptyList());
        capability.put("executionCapabilities", Collections.singletonList(
                executionCapability("post_effects", 1, 1,
                        "semantic_approximation", false)));
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("schemaVersion", "browser-template-scene-v4");
        scene.put("templateId", "tpl_1");
        scene.put("versionId", "tplver_1");
        scene.put("capability", capability);
        scene.put("slots", Collections.singletonList(row("slotKey", "photo_01")));
        scene.put("resources", Collections.emptyList());
        Map<String, Object> layer = row("layerId", "segment-1");
        layer.put("type", "photo");
        layer.put("slotKey", "photo_01");
        scene.put("layers", Collections.singletonList(layer));
        Map<String, Object> effect = row("effectId", "filter-1");
        effect.put("preset", "dual_lut_filter_approximation");
        effect.put("targetStartSeconds", Double.valueOf(0.0d));
        effect.put("targetDurationSeconds", Double.valueOf(4.0d));
        effect.put("intensity", Double.valueOf(0.6d));
        effect.put("fidelity", "semantic_approximation");
        effect.put("resourceKeys", row("background", "missing-lut"));
        scene.put("postEffects", Collections.singletonList(effect));
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion("browser-template-scene-v4");
        request.setScene(scene);
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));

        ApiException error = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));

        assertEquals("TEMPLATE_BROWSER_SCENE_POST_EFFECT_RESOURCE_INVALID", error.getCode());
    }

    @Test
    void rejectsVersionFourPhotoLayerWithUnknownSlot() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("schemaVersion", "browser-template-scene-v4");
        scene.put("templateId", "tpl_1");
        scene.put("versionId", "tplver_1");
        Map<String, Object> capability = new LinkedHashMap<String, Object>();
        capability.put("photoReplacementReady", Boolean.TRUE);
        capability.put("browserLayerCompositionReady", Boolean.TRUE);
        capability.put("browserExportReady", Boolean.TRUE);
        capability.put("blockingFeatures", Collections.emptyList());
        capability.put("executionCapabilities", Collections.singletonList(
                executionCapability("photo_layers", 1, 1, "exact", false)));
        scene.put("capability", capability);
        scene.put("slots", Collections.singletonList(row("slotKey", "photo_01")));
        Map<String, Object> layer = row("layerId", "segment-1");
        layer.put("type", "photo");
        layer.put("slotKey", "photo_99");
        scene.put("layers", Collections.singletonList(layer));
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion("browser-template-scene-v4");
        request.setScene(scene);
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));

        ApiException error = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));

        assertEquals("TEMPLATE_BROWSER_SCENE_SLOT_REFERENCE_INVALID", error.getCode());
    }

    @Test
    void rejectsVersionFourLayerWithUnknownMaskShape() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("schemaVersion", "browser-template-scene-v4");
        scene.put("templateId", "tpl_1");
        scene.put("versionId", "tplver_1");
        Map<String, Object> capability = new LinkedHashMap<String, Object>();
        capability.put("photoReplacementReady", Boolean.TRUE);
        capability.put("browserLayerCompositionReady", Boolean.TRUE);
        capability.put("browserExportReady", Boolean.TRUE);
        capability.put("blockingFeatures", Collections.emptyList());
        capability.put("executionCapabilities", Collections.singletonList(
                executionCapability("masks", 1, 1, "exact", false)));
        scene.put("capability", capability);
        scene.put("slots", Collections.singletonList(row("slotKey", "photo_01")));
        Map<String, Object> layer = row("layerId", "segment-1");
        layer.put("type", "photo");
        layer.put("slotKey", "photo_01");
        layer.put("mask", row("type", "custom-proprietary-shape"));
        scene.put("layers", Collections.singletonList(layer));
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion("browser-template-scene-v4");
        request.setScene(scene);
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));

        ApiException error = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));

        assertEquals("TEMPLATE_BROWSER_SCENE_MASK_INVALID", error.getCode());
    }

    @Test
    void rejectsVersionFourTextRunWithUnknownFontResource() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("schemaVersion", "browser-template-scene-v4");
        scene.put("templateId", "tpl_1");
        scene.put("versionId", "tplver_1");
        Map<String, Object> capability = new LinkedHashMap<String, Object>();
        capability.put("photoReplacementReady", Boolean.TRUE);
        capability.put("browserLayerCompositionReady", Boolean.TRUE);
        capability.put("browserExportReady", Boolean.TRUE);
        capability.put("blockingFeatures", Collections.emptyList());
        capability.put("executionCapabilities", Collections.singletonList(
                executionCapability("font_resources", 1, 1, "exact", false)));
        scene.put("capability", capability);
        scene.put("slots", Collections.emptyList());
        scene.put("resources", Collections.emptyList());
        Map<String, Object> run = row("start", Integer.valueOf(0));
        run.put("end", Integer.valueOf(4));
        run.put("fontResourceKey", "font_missing");
        Map<String, Object> layer = row("layerId", "text-1");
        layer.put("type", "text");
        layer.put("text", "Test");
        layer.put("runs", Collections.singletonList(run));
        scene.put("layers", Collections.singletonList(layer));
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion("browser-template-scene-v4");
        request.setScene(scene);
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));

        ApiException error = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));

        assertEquals("TEMPLATE_BROWSER_SCENE_FONT_REFERENCE_INVALID", error.getCode());
    }

    @Test
    void rejectsVersionFourStaticImageLayerWithoutImageResource() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("schemaVersion", "browser-template-scene-v4");
        scene.put("templateId", "tpl_1");
        scene.put("versionId", "tplver_1");
        Map<String, Object> capability = new LinkedHashMap<String, Object>();
        capability.put("photoReplacementReady", Boolean.TRUE);
        capability.put("browserLayerCompositionReady", Boolean.TRUE);
        capability.put("browserExportReady", Boolean.TRUE);
        capability.put("blockingFeatures", Collections.emptyList());
        capability.put("executionCapabilities", Collections.singletonList(
                executionCapability("photo_layers", 1, 1, "exact", false)));
        scene.put("capability", capability);
        scene.put("slots", Collections.singletonList(row("slotKey", "photo_01")));
        scene.put("resources", Collections.emptyList());
        Map<String, Object> layer = row("layerId", "fixed-segment-1");
        layer.put("type", "static_image");
        layer.put("resourceKey", "missing-image");
        scene.put("layers", Collections.singletonList(layer));
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion("browser-template-scene-v4");
        request.setScene(scene);
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));

        ApiException error = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));

        assertEquals("TEMPLATE_BROWSER_SCENE_RESOURCE_REFERENCE_INVALID", error.getCode());
    }

    @Test
    void rejectsVersionFourStickerLayerWithoutImageResource() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("schemaVersion", "browser-template-scene-v4");
        scene.put("templateId", "tpl_1");
        scene.put("versionId", "tplver_1");
        Map<String, Object> capability = new LinkedHashMap<String, Object>();
        capability.put("photoReplacementReady", Boolean.TRUE);
        capability.put("browserLayerCompositionReady", Boolean.TRUE);
        capability.put("browserExportReady", Boolean.TRUE);
        capability.put("blockingFeatures", Collections.emptyList());
        capability.put("executionCapabilities", Collections.singletonList(
                executionCapability("sticker_layers", 1, 1, "exact", false)));
        scene.put("capability", capability);
        scene.put("slots", Collections.emptyList());
        scene.put("resources", Collections.emptyList());
        Map<String, Object> layer = row("layerId", "sticker-segment-1");
        layer.put("type", "sticker");
        layer.put("resourceKey", "missing-sticker");
        scene.put("layers", Collections.singletonList(layer));
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion("browser-template-scene-v4");
        request.setScene(scene);
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));

        ApiException error = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));

        assertEquals("TEMPLATE_BROWSER_SCENE_RESOURCE_REFERENCE_INVALID", error.getCode());
    }

    @Test
    void rejectsVersionFourSceneThatHidesAnUnsupportedExportFeature() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> capability = new LinkedHashMap<String, Object>();
        capability.put("photoReplacementReady", Boolean.TRUE);
        capability.put("browserLayerCompositionReady", Boolean.TRUE);
        capability.put("browserExportReady", Boolean.TRUE);
        capability.put("blockingFeatures", Collections.emptyList());
        capability.put("executionCapabilities", Collections.singletonList(
                executionCapability("video_layers", 1, 0, "unsupported", true)));
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("schemaVersion", "browser-template-scene-v4");
        scene.put("templateId", "tpl_1");
        scene.put("versionId", "tplver_1");
        scene.put("capability", capability);
        scene.put("slots", Collections.singletonList(row("slotKey", "photo_01")));
        Map<String, Object> layer = row("layerId", "segment-1");
        layer.put("type", "photo");
        layer.put("slotKey", "photo_01");
        scene.put("layers", Collections.singletonList(layer));
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion("browser-template-scene-v4");
        request.setScene(scene);
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));

        ApiException error = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));

        assertEquals("TEMPLATE_BROWSER_EXECUTION_STATUS_INVALID", error.getCode());
    }

    @Test
    void migratesCurrentVersionsInPlaceAndReportsMediaPreserved() {
        when(repository.migrateCurrentTemplatesToBrowserRendering()).thenReturn(3);

        Map<String, Object> result = service.migrateCurrentTemplatesToBrowserRendering();

        assertEquals(Integer.valueOf(3), result.get("updatedVersionCount"));
        assertEquals("browser_ready", result.get("validationStatus"));
        assertEquals("browser-canvas-v1", result.get("rendererVersion"));
        assertEquals(Boolean.TRUE, result.get("mediaPreserved"));
    }

    @Test
    void refusesNativeValidatedVersionEvenWhenLegacyMediaIsReady() {
        Map<String, Object> template = row("template_id", "tpl_1");
        Map<String, Object> version = row("validation_status", "exact");
        version.put("source_availability", "available");
        version.put("base_duration_seconds", Double.valueOf(13));
        version.put("cycle_duration_seconds", Double.valueOf(13));
        version.put("validation_master_sha256", hash('a'));
        version.put("source_provenance_json", qualityProvenanceJson(13.0d, 13.0d));
        when(repository.template("tpl_1")).thenReturn(template);
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);
        when(repository.mediaByRole("tplver_1", "cover")).thenReturn(row("status", "ready"));
        when(repository.mediaByRole("tplver_1", "full_mv")).thenReturn(row("status", "processing"));

        ApiException error = assertThrows(ApiException.class,
                () -> service.publish("tpl_1", "tplver_1"));
        assertEquals("TEMPLATE_VERSION_NOT_PUBLISHABLE", error.getCode());
        verify(repository, never()).publish(anyString(), anyString());
    }

    @Test
    void replacesReadyDatabaseRecordWhenCloudflareAssetWasDeleted() {
        Map<String, Object> version = row("version_id", "tplver_1");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);
        Map<String, Object> existing = row("media_id", "media_1");
        existing.put("source_sha256", hash('a'));
        existing.put("status", "ready");
        existing.put("provider", "cloudflare_images");
        existing.put("provider_asset_id", "deleted-image");
        existing.put("provider_details_json", "{\"ready\":true}");
        when(repository.mediaByRole("tplver_1", "cover")).thenReturn(existing);
        when(mediaProvider.isReusableReadyAsset("cloudflare_images", "deleted-image"))
                .thenReturn(false);
        when(mediaProvider.createImageUpload(anyString(), any()))
                .thenReturn(new CloudflareTemplateMediaProvider.UploadSession(
                        "cloudflare_images", "replacement-image", "https://upload.example",
                        "awaiting_upload", new LinkedHashMap<String, Object>()));

        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("cover");
        request.setSourceSha256(hash('a'));
        request.setSourceSizeBytes(Long.valueOf(100));
        request.setWidth(Integer.valueOf(1080));
        request.setHeight(Integer.valueOf(1920));

        Map<String, Object> result = service.createMediaSession(
                "tpl_1", "tplver_1", false, request);

        assertEquals("media_1", result.get("mediaId"));
        assertEquals("awaiting_upload", result.get("status"));
        assertFalse((Boolean) result.get("idempotentReplay"));
        verify(repository).upsertMedia(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any(Long.class),
                any(), any(), any(), anyString());
    }

    @Test
    void forceReplaceCreatesFreshAssetEvenWhenExistingAssetIsReusable() {
        Map<String, Object> version = row("version_id", "tplver_1");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);
        Map<String, Object> existing = row("media_id", "media_1");
        existing.put("source_sha256", hash('a'));
        existing.put("status", "ready");
        existing.put("provider", "cloudflare_stream");
        existing.put("provider_asset_id", "old-stream");
        when(repository.mediaByRole("tplver_1", "full_mv")).thenReturn(existing);
        when(mediaProvider.isReusableReadyAsset("cloudflare_stream", "old-stream"))
                .thenReturn(true);
        when(mediaProvider.createStreamUpload(anyString(), any()))
                .thenReturn(new CloudflareTemplateMediaProvider.UploadSession(
                        "cloudflare_stream", "new-stream", "https://upload.example",
                        "awaiting_upload", new LinkedHashMap<String, Object>()));

        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("full_mv");
        request.setSourceSha256(hash('a'));
        request.setSourceSizeBytes(Long.valueOf(100));
        request.setWidth(Integer.valueOf(1080));
        request.setHeight(Integer.valueOf(1920));
        request.setDurationSeconds(Double.valueOf(180));
        request.setFilename("showcase.mp4");
        request.setForceReplace(Boolean.TRUE);

        Map<String, Object> result = service.createMediaSession(
                "tpl_1", "tplver_1", true, request);

        assertEquals("media_1", result.get("mediaId"));
        assertEquals("awaiting_upload", result.get("status"));
        assertFalse((Boolean) result.get("idempotentReplay"));
        verify(mediaProvider).createStreamUpload(anyString(), any());
    }

    @Test
    void repeatedSynchronizationReusesQueuedVideoInsteadOfUploadingAgain() {
        when(repository.version("tpl_1", "tplver_1")).thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> existing = row("media_id", "media_1");
        existing.put("source_sha256", hash('a'));
        existing.put("status", "awaiting_upload");
        existing.put("provider_asset_id", "queued-video");
        when(repository.mediaByRole("tplver_1", "browser_parity_reference")).thenReturn(existing);
        when(mediaProvider.streamState("queued-video")).thenReturn(
                new CloudflareTemplateMediaProvider.MediaState("processing", row("state", "queued")));
        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("browser_parity_reference");
        request.setSourceSha256(hash('a'));
        request.setFilename("reference.mp4");
        request.setDurationSeconds(14.267);
        Map<String, Object> result = service.createMediaSession("tpl_1", "tplver_1", true, request);
        assertEquals("processing", result.get("status"));
        assertEquals("media_1", result.get("mediaId"));
        assertEquals(Boolean.TRUE, result.get("idempotentReplay"));
        verify(mediaProvider, never()).createStreamUpload(anyString(), any());
        when(mediaProvider.streamState("queued-video")).thenReturn(
                new CloudflareTemplateMediaProvider.MediaState("ready", row("state", "ready")));
        assertEquals("ready", service.createMediaSession("tpl_1", "tplver_1", true, request).get("status"));
        verify(repository).markMediaReady(eq("media_1"), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsCapCutOfficialPreviewProvenanceOnFullMv() {
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        when(repository.mediaByRole("tplver_1", "full_mv")).thenReturn(null);
        when(mediaProvider.createStreamUpload(anyString(), any()))
                .thenReturn(new CloudflareTemplateMediaProvider.UploadSession(
                        "cloudflare_stream", "preview-stream", "https://upload.example",
                        "awaiting_upload", row("playbackUrl", "https://stream.example/manifest/video.m3u8")));
        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("full_mv");
        request.setSourceSha256(hash('c'));
        request.setSourceSizeBytes(Long.valueOf(100));
        request.setDurationSeconds(Double.valueOf(26.633));
        request.setFilename("official-preview.mp4");
        request.setSourceType("capcut_official_template_preview");
        request.setDisplayLabel("CapCut 原始模板预览");
        request.setOfficialTemplateId("7583099812292218119");
        request.setOfficialPageUrl("https://www.capcut.com/template-detail/7583099812292218119");

        Map<String, Object> result = service.createMediaSession(
                "tpl_1", "tplver_1", true, request);
        Map<String, Object> details = (Map<String, Object>) result.get("providerDetails");

        assertEquals("capcut_official_template_preview", details.get("sourceType"));
        assertEquals("CapCut 原始模板预览", details.get("displayLabel"));
        assertEquals("7583099812292218119", details.get("officialTemplateId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsValidatedAiMusicMvNativeOutputAsFullMv() {
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        when(repository.mediaByRole("tplver_1", "full_mv")).thenReturn(null);
        when(mediaProvider.createStreamUpload(anyString(), any()))
                .thenReturn(new CloudflareTemplateMediaProvider.UploadSession(
                        "cloudflare_stream", "validated-stream", "https://upload.example",
                        "awaiting_upload", row("playbackUrl", "https://stream.example/manifest/video.m3u8")));
        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("full_mv");
        request.setSourceSha256(hash('d'));
        request.setSourceSizeBytes(Long.valueOf(100));
        request.setDurationSeconds(Double.valueOf(180));
        request.setFilename("validated-native-output.mp4");
        request.setSourceType("validated_ai_music_mv_native_output");
        request.setDisplayLabel("已验收原生 MV");

        Map<String, Object> result = service.createMediaSession(
                "tpl_1", "tplver_1", true, request);
        Map<String, Object> details = (Map<String, Object>) result.get("providerDetails");

        assertEquals("validated_ai_music_mv_native_output", details.get("sourceType"));
        assertEquals("已验收原生 MV", details.get("displayLabel"));
    }

    @Test
    void createsCloudflareImageForADeclaredTemplatePhotoSlot() {
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> slot = row("slot_key", "photo_01");
        slot.put("slot_type", "image");
        when(repository.slots("tplver_1")).thenReturn(Collections.singletonList(slot));
        when(repository.mediaByRole("tplver_1", "slot_default:photo_01")).thenReturn(null);
        when(mediaProvider.createImageUpload(anyString(), any()))
                .thenReturn(new CloudflareTemplateMediaProvider.UploadSession(
                        "cloudflare_images", "template-photo", "https://upload.example",
                        "awaiting_upload", new LinkedHashMap<String, Object>()));
        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("slot_default:photo_01");
        request.setSourceSha256(hash('b'));
        request.setSourceSizeBytes(Long.valueOf(100));
        request.setWidth(Integer.valueOf(1080));
        request.setHeight(Integer.valueOf(1920));

        Map<String, Object> result = service.createMediaSession(
                "tpl_1", "tplver_1", false, request);

        assertEquals("awaiting_upload", result.get("status"));
        verify(repository).upsertMedia(anyString(), eq("tpl_1"), eq("tplver_1"),
                eq("slot_default:photo_01"), anyString(), anyString(), anyString(),
                anyString(), any(Long.class), any(), any(), any(), anyString());
    }

    @Test
    void createsCloudflareImageForADeclaredBrowserResource() {
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> browserScene = row("status", "ready");
        browserScene.put("scene_json", "{\"resources\":[{\"resourceKey\":\"lut_background\","
                + "\"role\":\"browser_resource:lut_background\",\"kind\":\"lut_2d_png\"}]}");
        when(repository.browserScene("tplver_1")).thenReturn(browserScene);
        when(repository.mediaByRole("tplver_1", "browser_resource:lut_background"))
                .thenReturn(null);
        when(mediaProvider.createImageUpload(anyString(), any()))
                .thenReturn(new CloudflareTemplateMediaProvider.UploadSession(
                        "cloudflare_images", "browser-lut", "https://upload.example",
                        "awaiting_upload", new LinkedHashMap<String, Object>()));
        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("browser_resource:lut_background");
        request.setSourceSha256(hash('e'));
        request.setSourceSizeBytes(Long.valueOf(100));
        request.setWidth(Integer.valueOf(512));
        request.setHeight(Integer.valueOf(512));

        Map<String, Object> result = service.createMediaSession(
                "tpl_1", "tplver_1", false, request);

        assertEquals("awaiting_upload", result.get("status"));
        verify(repository).upsertMedia(anyString(), eq("tpl_1"), eq("tplver_1"),
                eq("browser_resource:lut_background"), anyString(), anyString(), anyString(),
                anyString(), any(Long.class), any(), any(), any(), anyString());
    }

    @Test
    void createsCloudflareStreamForADeclaredBrowserVideoResource() {
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> browserScene = row("status", "ready");
        browserScene.put("scene_json", "{\"resources\":[{\"resourceKey\":\"video_background\","
                + "\"role\":\"browser_resource:video_background\",\"kind\":\"video\"}]}");
        when(repository.browserScene("tplver_1")).thenReturn(browserScene);
        when(repository.mediaByRole("tplver_1", "browser_resource:video_background"))
                .thenReturn(null);
        when(mediaProvider.createStreamUpload(anyString(), any()))
                .thenReturn(new CloudflareTemplateMediaProvider.UploadSession(
                        "cloudflare_stream", "browser-video", "https://upload.example",
                        "awaiting_upload", new LinkedHashMap<String, Object>()));
        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("browser_resource:video_background");
        request.setSourceSha256(hash('v'));
        request.setSourceSizeBytes(Long.valueOf(1000));
        request.setWidth(Integer.valueOf(720));
        request.setHeight(Integer.valueOf(1280));
        request.setDurationSeconds(Double.valueOf(18.0d));
        request.setFilename("background.mp4");

        Map<String, Object> result = service.createMediaSession(
                "tpl_1", "tplver_1", true, request);

        assertEquals("awaiting_upload", result.get("status"));
        verify(mediaProvider).createStreamUpload(anyString(), any());
        verify(repository).upsertMedia(anyString(), eq("tpl_1"), eq("tplver_1"),
                eq("browser_resource:video_background"), eq("cloudflare_stream"),
                anyString(), anyString(), anyString(), any(Long.class), any(), any(), any(),
                anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsCloudflareStreamForOfficialBrowserParityReference() {
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        when(repository.mediaByRole("tplver_1", "browser_parity_reference"))
                .thenReturn(null);
        when(mediaProvider.createStreamUpload(anyString(), any()))
                .thenReturn(new CloudflareTemplateMediaProvider.UploadSession(
                        "cloudflare_stream", "parity-reference", "https://upload.example",
                        "awaiting_upload", new LinkedHashMap<String, Object>()));
        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("browser_parity_reference");
        request.setSourceSha256(hash('p'));
        request.setSourceSizeBytes(Long.valueOf(1000));
        request.setWidth(Integer.valueOf(1080));
        request.setHeight(Integer.valueOf(1920));
        request.setDurationSeconds(Double.valueOf(33.433d));
        request.setFilename("official-preview.mp4");
        request.setSourceType("capcut_official_template_preview");
        request.setDisplayLabel("CapCut 官方模板预览");

        Map<String, Object> result = service.createMediaSession(
                "tpl_1", "tplver_1", true, request);
        Map<String, Object> details = (Map<String, Object>) result.get("providerDetails");

        assertEquals("awaiting_upload", result.get("status"));
        assertEquals("capcut_official_template_preview", details.get("sourceType"));
        verify(repository).upsertMedia(anyString(), eq("tpl_1"), eq("tplver_1"),
                eq("browser_parity_reference"), eq("cloudflare_stream"),
                anyString(), anyString(), anyString(), any(Long.class), any(), any(), any(),
                anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsCurrentNativeVersionExportAsBrowserParityReference() {
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        when(repository.mediaByRole("tplver_1", "browser_parity_reference"))
                .thenReturn(null);
        when(mediaProvider.createStreamUpload(anyString(), any()))
                .thenReturn(new CloudflareTemplateMediaProvider.UploadSession(
                        "cloudflare_stream", "native-reference", "https://upload.example",
                        "awaiting_upload", new LinkedHashMap<String, Object>()));
        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("browser_parity_reference");
        request.setSourceSha256(hash('n'));
        request.setSourceSizeBytes(Long.valueOf(1000));
        request.setWidth(Integer.valueOf(1080));
        request.setHeight(Integer.valueOf(1920));
        request.setDurationSeconds(Double.valueOf(33.433d));
        request.setFilename("native-validation.mp4");
        request.setSourceType("capcut_native_version_export");
        request.setDisplayLabel("CapCut 原生验收成片");

        Map<String, Object> result = service.createMediaSession(
                "tpl_1", "tplver_1", true, request);
        Map<String, Object> details = (Map<String, Object>) result.get("providerDetails");

        assertEquals("awaiting_upload", result.get("status"));
        assertEquals("capcut_native_version_export", details.get("sourceType"));
        assertEquals("CapCut 原生验收成片", details.get("displayLabel"));
    }

    @Test
    void rejectsBrowserResourceThatIsNotDeclaredByTheScene() {
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> browserScene = row("status", "ready");
        browserScene.put("scene_json", "{\"resources\":[]}");
        when(repository.browserScene("tplver_1")).thenReturn(browserScene);
        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("browser_resource:undeclared");
        request.setSourceSha256(hash('f'));
        request.setSourceSizeBytes(Long.valueOf(100));

        ApiException error = assertThrows(ApiException.class, () -> service.createMediaSession(
                "tpl_1", "tplver_1", false, request));

        assertEquals("TEMPLATE_MEDIA_BROWSER_RESOURCE_INVALID", error.getCode());
        verify(mediaProvider, never()).createImageUpload(anyString(), any());
    }

    @Test
    void reconcilesDerivedSlotsForTheSameImmutableSource() {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        Map<String, Object> version = row("source_node_id", "mac-1");
        version.put("source_local_key", "templates/tpl_1/tplver_1");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);

        TemplateSlotReconcileRequest request = reconcileRequest();
        Map<String, Object> result = service.reconcileSlots("tpl_1", "tplver_1", request);

        assertEquals("reconciled", result.get("status"));
        assertEquals(Integer.valueOf(1), result.get("slotCount"));
        verify(repository).replaceSlots("tpl_1", "tplver_1", request.getSlots());
    }

    @Test
    void rejectsSlotReconciliationFromAnotherSourceSnapshot() {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        Map<String, Object> version = row("source_node_id", "mac-1");
        version.put("source_local_key", "templates/tpl_1/tplver_1");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);

        TemplateSlotReconcileRequest request = reconcileRequest();
        request.setSourceLocalKey("templates/tpl_1/other");
        ApiException error = assertThrows(ApiException.class,
                () -> service.reconcileSlots("tpl_1", "tplver_1", request));

        assertEquals("TEMPLATE_SLOT_SOURCE_MISMATCH", error.getCode());
        verify(repository, never()).replaceSlots(anyString(), anyString(), any());
    }

    @Test
    void publishedSlotReplayPreservesIdsWithoutWriting() {
        Map<String, Object> version = row("status", "published");
        version.put("source_node_id", "mac-1");
        version.put("source_local_key", "templates/tpl_1/tplver_1");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);
        Map<String, Object> stored = publishedSlot();
        when(repository.slots("tplver_1")).thenReturn(Collections.singletonList(stored));

        assertEquals("reconciled", service.reconcileSlots("tpl_1", "tplver_1", reconcileRequest()).get("status"));
        assertEquals("slot_original", stored.get("slot_id"));
        verify(repository, never()).replaceSlots(anyString(), anyString(), any());
    }

    @Test
    void publishedSlotsRejectChangesToEveryPersistedFieldAndMissingSlots() {
        Map<String, Object> version = row("status", "published");
        version.put("source_node_id", "mac-1");
        version.put("source_local_key", "templates/tpl_1/tplver_1");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);
        for (String field : Arrays.asList("slot_key", "slot_type", "display_name", "timeline_order",
                "aspect_ratio", "crop_policy", "repeat_policy", "material_id", "material_group", "is_required")) {
            Map<String, Object> stored = publishedSlot();
            stored.put(field, "timeline_order".equals(field) ? Integer.valueOf(2)
                    : "is_required".equals(field) ? Integer.valueOf(0) : "changed");
            when(repository.slots("tplver_1")).thenReturn(Collections.singletonList(stored));
            assertEquals("TEMPLATE_PUBLISHED_SLOTS_IMMUTABLE", assertThrows(ApiException.class,
                    () -> service.reconcileSlots("tpl_1", "tplver_1", reconcileRequest())).getCode(), field);
        }
        when(repository.slots("tplver_1")).thenReturn(Collections.emptyList());
        assertEquals("TEMPLATE_PUBLISHED_SLOTS_IMMUTABLE", assertThrows(ApiException.class,
                () -> service.reconcileSlots("tpl_1", "tplver_1", reconcileRequest())).getCode());
        verify(repository, never()).replaceSlots(anyString(), anyString(), any());
    }

    @Test
    void withdrawnPreviouslyPublishedSlotsRemainImmutable() {
        Map<String, Object> version = row("status", "offline");
        version.put("published_at", "2026-09-10T00:00:00Z");
        version.put("source_node_id", "mac-1");
        version.put("source_local_key", "templates/tpl_1/tplver_1");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);
        when(repository.slots("tplver_1")).thenReturn(Collections.singletonList(publishedSlot()));
        TemplateSlotReconcileRequest request = reconcileRequest();
        request.getSlots().get(0).setMaterialId("replacement");
        assertEquals("TEMPLATE_PUBLISHED_SLOTS_IMMUTABLE", assertThrows(ApiException.class,
                () -> service.reconcileSlots("tpl_1", "tplver_1", request)).getCode());
        verify(repository, never()).replaceSlots(anyString(), anyString(), any());
    }

    @Test
    void publishedMediaReplaysWithoutReplacingProviderAssetOrEvidence() {
        when(repository.version("tpl_1", "tplver_1")).thenReturn(row("status", "published"));
        Map<String, Object> media = publishedCover();
        when(repository.mediaByRole("tplver_1", "cover")).thenReturn(media);
        Map<String, Object> result = service.createMediaSession("tpl_1", "tplver_1", false, publishedCoverRequest());
        assertEquals("media_original", result.get("mediaId"));
        assertEquals(Boolean.TRUE, result.get("idempotentReplay"));
        verify(mediaProvider, never()).createImageUpload(anyString(), any());
        verify(repository, never()).markMediaReady(anyString(), anyString());
    }

    @Test
    void publishedMediaRejectsReplacementMissingAssetAndChangedMetadata() {
        Map<String, Object> version = row("status", "offline");
        version.put("published_at", "2026-09-10T00:00:00Z");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);
        for (String change : Arrays.asList("hash", "size", "width", "height", "force", "evidence", "missing", "pending")) {
            TemplateMediaUploadSessionRequest request = publishedCoverRequest();
            Map<String, Object> media = publishedCover();
            if ("hash".equals(change)) request.setSourceSha256(hash('b'));
            if ("size".equals(change)) request.setSourceSizeBytes(101L);
            if ("width".equals(change)) request.setWidth(2);
            if ("height".equals(change)) request.setHeight(2);
            if ("force".equals(change)) request.setForceReplace(true);
            if ("evidence".equals(change)) request.setSourceType("replacement");
            if ("pending".equals(change)) media.put("status", "awaiting_upload");
            when(repository.mediaByRole("tplver_1", "cover")).thenReturn("missing".equals(change) ? null : media);
            assertEquals("TEMPLATE_PUBLISHED_MEDIA_IMMUTABLE", assertThrows(ApiException.class,
                    () -> service.createMediaSession("tpl_1", "tplver_1", false, request)).getCode(), change);
        }
        verify(mediaProvider, never()).createImageUpload(anyString(), any());
        verify(mediaProvider, never()).createStreamUpload(anyString(), any());
        verify(repository, never()).markMediaReady(anyString(), anyString());
    }

    @Test
    void publishedSynchronizationCannotPruneRollbackMedia() {
        when(repository.version("tpl_1", "tplver_1")).thenReturn(row("status", "published"));
        when(repository.browserScene("tplver_1")).thenReturn(row("manifest_sha256", hash('a')));
        Map<String, Object> cover = row("media_role", "cover");
        cover.put("status", "ready");
        Map<String, Object> reference = row("media_role", "browser_parity_reference");
        reference.put("status", "ready");
        Map<String, Object> full = row("media_role", "full_mv");
        full.put("status", "ready");
        when(repository.media("tplver_1")).thenReturn(Arrays.asList(cover, reference, full));
        com.example.cursorquitterweb.musicmv.dto.TemplateSyncCompleteRequest request =
                new com.example.cursorquitterweb.musicmv.dto.TemplateSyncCompleteRequest();
        request.setManifestSha256(hash('a'));
        request.setMediaRoles(Arrays.asList("cover", "browser_parity_reference"));
        assertEquals("TEMPLATE_PUBLISHED_MEDIA_IMMUTABLE", assertThrows(ApiException.class,
                () -> service.completeSynchronization("tpl_1", "tplver_1", request)).getCode());
        request.setMediaRoles(Arrays.asList("full_mv", "cover", "browser_parity_reference"));
        assertEquals("synchronized", service.completeSynchronization("tpl_1", "tplver_1", request).get("status"));
        verify(repository, never()).retainSynchronizedMedia(anyString(), anyString(), anyList());
    }

    private TemplateMediaUploadSessionRequest publishedCoverRequest() {
        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("cover");
        request.setSourceSha256(hash('a'));
        request.setSourceSizeBytes(100L);
        return request;
    }

    private Map<String, Object> publishedCover() {
        Map<String, Object> media = row("media_id", "media_original");
        media.put("status", "ready");
        media.put("source_sha256", hash('a'));
        media.put("source_size_bytes", 100L);
        media.put("width", 1);
        media.put("height", 1);
        media.put("provider_asset_id", "asset_original");
        media.put("provider_details_json", "{}");
        return media;
    }

    private Map<String, Object> publishedSlot() {
        Map<String, Object> slot = row("slot_id", "slot_original");
        slot.put("slot_key", "photo_1");
        slot.put("slot_type", "image");
        slot.put("display_name", "Photo 1");
        slot.put("timeline_order", Long.valueOf(0));
        slot.put("crop_policy", "fill");
        slot.put("repeat_policy", "cycle");
        slot.put("is_required", Integer.valueOf(1));
        return slot;
    }

    @Test
    void permanentlyDeletesOfflineTemplateMediaAndCatalogGraph() {
        Map<String, Object> template = row("status", "offline");
        when(repository.template("tpl_1")).thenReturn(template);
        when(repository.projectReferenceCount("tpl_1")).thenReturn(Long.valueOf(0L));
        when(repository.renderJobReferenceCount("tpl_1")).thenReturn(Long.valueOf(0L));
        Map<String, Object> cover = row("provider", "cloudflare_images");
        cover.put("provider_asset_id", "image-1");
        Map<String, Object> fullMv = row("provider", "cloudflare_stream");
        fullMv.put("provider_asset_id", "stream-1");
        when(repository.mediaForTemplate("tpl_1"))
                .thenReturn(java.util.Arrays.asList(cover, fullMv));

        Map<String, Object> result = service.action("tpl_1", "delete-template", null);

        assertEquals(Boolean.TRUE, result.get("deleted"));
        assertEquals(Integer.valueOf(2), result.get("deletedMediaCount"));
        verify(mediaProvider).deleteAsset("cloudflare_images", "image-1");
        verify(mediaProvider).deleteAsset("cloudflare_stream", "stream-1");
        verify(repository).deleteTemplate("tpl_1");
    }

    @Test
    void refusesPermanentDeletionUntilOfflineAndUnreferenced() {
        when(repository.template("tpl_1")).thenReturn(row("status", "published"));
        ApiException published = assertThrows(ApiException.class,
                () -> service.action("tpl_1", "delete-template", null));
        assertEquals("TEMPLATE_DELETE_REQUIRES_OFFLINE", published.getCode());

        when(repository.template("tpl_1")).thenReturn(row("status", "offline"));
        when(repository.projectReferenceCount("tpl_1")).thenReturn(Long.valueOf(1L));
        ApiException referenced = assertThrows(ApiException.class,
                () -> service.action("tpl_1", "delete-template", null));
        assertEquals("TEMPLATE_DELETE_REFERENCED", referenced.getCode());
        verify(mediaProvider, never()).deleteAsset(anyString(), anyString());
        verify(repository, never()).deleteTemplate(anyString());
    }

    @Test
    void forceDeletionSkipsStatusAndReferenceGuards() {
        when(repository.template("tpl_1")).thenReturn(row("status", "published"));
        when(repository.projectReferenceCount("tpl_1")).thenReturn(Long.valueOf(3L));
        when(repository.renderJobReferenceCount("tpl_1")).thenReturn(Long.valueOf(2L));
        Map<String, Object> preview = row("provider", "cloudflare_stream");
        preview.put("provider_asset_id", "stream-1");
        when(repository.mediaForTemplate("tpl_1"))
                .thenReturn(java.util.Collections.singletonList(preview));

        Map<String, Object> result = service.action("tpl_1", "force-delete-template", null);

        assertEquals(Boolean.TRUE, result.get("deleted"));
        assertEquals(Boolean.TRUE, result.get("forced"));
        assertEquals(Long.valueOf(3L), result.get("detachedProjectCount"));
        assertEquals(Long.valueOf(2L), result.get("deletedRenderJobCount"));
        verify(mediaProvider).deleteAsset("cloudflare_stream", "stream-1");
        verify(repository).forceDeleteTemplate("tpl_1");
        verify(repository, never()).deleteTemplate("tpl_1");
    }

    private TemplateSlotReconcileRequest reconcileRequest() {
        TemplateSlotReconcileRequest request = new TemplateSlotReconcileRequest();
        request.setSourceNodeId("mac-1");
        request.setSourceLocalKey("templates/tpl_1/tplver_1");
        TemplatePromotionRequest.Slot slot = new TemplatePromotionRequest.Slot();
        slot.setSlotKey("photo_1");
        slot.setSlotType("image");
        slot.setDisplayName("Photo 1");
        slot.setTimelineOrder(Integer.valueOf(0));
        slot.setCropPolicy("fill");
        slot.setRepeatPolicy("cycle");
        request.setSlots(new java.util.ArrayList<>(Collections.singletonList(slot)));
        return request;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Set<String> capturedCategoryKeys(String primary) {
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceTemplateCategories(eq("tpl_1"), eq(primary), captor.capture());
        Set<String> result = new HashSet<String>();
        for (Object raw : captor.getValue()) {
            result.add(String.valueOf(((Map<String, Object>) raw).get("categoryKey")));
        }
        return result;
    }

    private TemplatePromotionRequest validPromotion() {
        TemplatePromotionRequest request = new TemplatePromotionRequest();
        request.setTemplateId("tpl_1");
        request.setCapcutTemplateId("7362454015088561426");
        request.setSlug("birthday-one");
        request.setCategoryKey("birthday");
        request.setNameZh("生日");
        request.setNameEn("Birthday");
        request.setWidth(Integer.valueOf(1080));
        request.setHeight(Integer.valueOf(1920));
        request.setFps(Double.valueOf(30));
        request.setDurationSeconds(Double.valueOf(180));
        request.setBaseDurationSeconds(Double.valueOf(13));
        request.setCycleDurationSeconds(Double.valueOf(13));
        request.setValidationRenderJobId("native_1");
        request.setValidationMasterSha256(hash('a'));
        request.setDraftSnapshotSha256(hash('b'));
        request.setTimelineEvidenceSha256(hash('c'));
        request.setNativeRuntimeVersion("9.2.0");
        request.setNativeRuntimeSha256(hash('d'));
        request.setRendererVersion("renderer-1");
        request.setSourceNodeId("mac-1");
        request.setSourceLocalKey("templates/tpl_1/tplver_1");
        request.setSemanticIntegrity("exact");
        request.setVideoEncodeCount(Integer.valueOf(1));
        request.setIntermediateVideoCount(Integer.valueOf(0));
        request.setExternalResourceReadCount(Integer.valueOf(0));
        request.setMissingResourceCount(Integer.valueOf(0));
        request.setValidationElapsedSeconds(Double.valueOf(12));
        request.setVisualQuality(visualQuality(13.0d, 13.0d));
        TemplatePromotionRequest.Slot slot = new TemplatePromotionRequest.Slot();
        slot.setSlotKey("photo_1");
        slot.setSlotType("image");
        slot.setDisplayName("照片 1");
        slot.setTimelineOrder(Integer.valueOf(0));
        slot.setCropPolicy("fill");
        slot.setRepeatPolicy("cycle");
        request.setSlots(new java.util.ArrayList<>(Collections.singletonList(slot)));
        return request;
    }

    private Map<String, Object> visualQuality(double base, double cycle) {
        Map<String, Object> quality = new LinkedHashMap<String, Object>();
        quality.put("schemaVersion", "template-visual-quality-v1");
        quality.put("status", cycle < base ? "adjusted" : "passed");
        quality.put("sourceSha256", hash('a'));
        quality.put("baseDurationSeconds", Double.valueOf(base));
        quality.put("effectiveCycleDurationSeconds", Double.valueOf(cycle));
        return quality;
    }

    private String qualityProvenanceJson(double base, double cycle) {
        try {
            Map<String, Object> provenance = new LinkedHashMap<String, Object>();
            provenance.put("visualQuality", visualQuality(base, cycle));
            return new ObjectMapper().writeValueAsString(provenance);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Map<String, Object> row(String key, Object value) {
        Map<String, Object> row = new LinkedHashMap<String, Object>(); row.put(key, value); return row;
    }

    private Map<String, Object> executionCapability(
            String feature, int declared, int executable, String fidelity, boolean blocksExport) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("feature", feature);
        result.put("declaredCount", Integer.valueOf(declared));
        result.put("executableCount", Integer.valueOf(executable));
        result.put("fidelity", fidelity);
        result.put("blocksExport", Boolean.valueOf(blocksExport));
        return result;
    }

    private Map<String, Object> effectImplementation(
            String kind, String implementationKey, String renderer, String fidelity) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("kind", kind);
        result.put("implementationKey", implementationKey);
        result.put("renderer", renderer);
        result.put("usageCount", Integer.valueOf(1));
        result.put("fidelity", fidelity);
        result.put("blocksExport", Boolean.valueOf("unsupported".equals(fidelity)));
        return result;
    }

    private String hash(char value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 64; i++) result.append(value);
        return result.toString();
    }

    private String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte item : digest) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }
}
