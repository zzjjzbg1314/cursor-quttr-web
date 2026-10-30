package com.example.cursorquitterweb.musicmv.aiphoto;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;

class ArkAiPhotoProviderTest {
    final ObjectMapper json=new ObjectMapper();
    final RestTemplate http=new RestTemplate();
    final MockRestServiceServer server=MockRestServiceServer.bindTo(http).build();
    final ArkAiPhotoProvider provider=new ArkAiPhotoProvider(true,"test-secret","cdn.example",http);
    ObjectNode recipe(String prompt) {
        ObjectNode r=json.createObjectNode();r.put("provider","ark");r.put("model",ArkAiPhotoProvider.MODEL);
        r.put("prompt",prompt);r.putObject("parameters").put("size","2K");return r;
    }
    JsonNode response(String tail) throws Exception {return json.readTree("{\"model\":\""+ArkAiPhotoProvider.MODEL+"\",\"data\":"+tail+"}");}
    @Test void postsOfficialContractAndReturnsOneImage() {
        ObjectNode body=ArkAiPhotoProvider.requestBody(recipe("保留人物"),"https://owned/photo");
        assertEquals("doubao-seedream-5-0-pro-260628",body.path("model").asText());
        assertFalse(body.has("sequential_image_generation"));assertFalse(body.has("stream"));
        assertTrue(body.has("layer_decomposition"));assertFalse(body.path("layer_decomposition").asBoolean());
        assertTrue(body.path("watermark").asBoolean());assertFalse(body.has("tools"));
        assertEquals("url",body.path("response_format").asText());assertFalse(body.has("denoise"));assertFalse(body.has("seed"));
        server.expect(requestTo(ArkAiPhotoProvider.ENDPOINT)).andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization","Bearer test-secret")).andExpect(content().json(body.toString()))
            .andRespond(withSuccess("{\"model\":\""+ArkAiPhotoProvider.MODEL+"\",\"data\":[{\"url\":\"https://cdn.example/result.jpg\"}]}",MediaType.APPLICATION_JSON));
        assertEquals("https://cdn.example/result.jpg",provider.generate(recipe("保留人物"),"https://owned/photo"));server.verify();
    }
    @Test void disabledAndMissingCredentialsNeverCallHttp() {
        assertThrows(RuntimeException.class,()->new ArkAiPhotoProvider(false,"test","cdn.example",http).generate(recipe("x"),"https://owned/photo"));
        assertThrows(RuntimeException.class,()->new ArkAiPhotoProvider(true,"","cdn.example",http).generate(recipe("x"),"https://owned/photo"));server.verify();
    }
    @Test void rejectsForeignModelsAndUndocumentedParameters() {
        ObjectNode r=recipe("x");r.put("model","aip_high_aes_general_v43s_jm");
        assertThrows(RuntimeException.class,()->provider.requireConfigured(r));
        for(String key:new String[]{"denoise","seed","stream","image","model","watermark","sequential_image_generation","layer_decomposition","tools"}) {
            ObjectNode bad=recipe("x");((ObjectNode)bad.get("parameters")).put(key,"unexpected");
            assertThrows(RuntimeException.class,()->provider.requireConfigured(bad));
        }
        ObjectNode badSize=recipe("x");((ObjectNode)badSize.get("parameters")).put("size","8K");
        assertThrows(RuntimeException.class,()->provider.requireConfigured(badSize));
        assertThrows(RuntimeException.class,()->ArkAiPhotoProvider.requestBody(recipe("x"),"http://owned/photo"));
    }
    @Test void validatesOutputCountErrorsModelAndHost() throws Exception {
        for(String data:new String[]{"[]","[{}]","[{\"url\":1}]","[{\"url\":\"https://cdn.example/a\"},{\"url\":\"https://cdn.example/b\"}]","[{\"error\":{},\"url\":\"https://cdn.example/a\"}]"}) {
            JsonNode r=response(data);assertThrows(RuntimeException.class,()->provider.output(r));
        }
        for(String url:new String[]{"http://cdn.example/a","https://evil.example/a","https://cdn.example.evil/a","https://u:p@cdn.example/a","https://cdn.example:123/a"}) {
            JsonNode r=response("[{\"url\":\""+url+"\"}]");assertThrows(RuntimeException.class,()->provider.output(r));
        }
        assertThrows(RuntimeException.class,()->provider.output(json.readTree("{\"error\":{\"message\":\"private\"}}")));
        assertThrows(RuntimeException.class,()->provider.output(json.readTree("{\"model\":\"other\",\"data\":[]}")));
    }
    @Test void httpFailureDoesNotRetryGeneration() {
        server.expect(requestTo(ArkAiPhotoProvider.ENDPOINT)).andRespond(withServerError());
        assertThrows(RuntimeException.class,()->provider.generate(recipe("x"),"https://owned/photo"));server.verify();
    }
    @Test void preservesAllPromptLanguages() {
        for(String prompt:new String[]{"keep the face","保留人物","保留人物與背景","人物を保持","인물 유지","conservar persona","preservar pessoa","garder le visage","Person beibehalten","mantieni persona","اختبار"})
            assertEquals(prompt,ArkAiPhotoProvider.requestBody(recipe(prompt),"https://owned/photo").path("prompt").asText());
    }
    @Test void supportsProSizesAndRejectsLegacyRecipe() {
        for (String size:new String[]{"1K","1.5K","2K"}) {
            ObjectNode r=recipe("x");((ObjectNode)r.get("parameters")).put("size",size);
            assertEquals(size,ArkAiPhotoProvider.requestBody(r,"https://owned/photo").path("size").asText());
        }
        ObjectNode old=recipe("x");old.put("model","doubao-seedream-4-5-251128");
        assertThrows(RuntimeException.class,()->provider.requireConfigured(old));
        ObjectNode high=recipe("x");((ObjectNode)high.get("parameters")).put("size","4K");
        assertThrows(RuntimeException.class,()->provider.requireConfigured(high));
        ObjectNode defaultSize=recipe("x");((ObjectNode)defaultSize.get("parameters")).remove("size");
        assertEquals("2K",ArkAiPhotoProvider.requestBody(defaultSize,"https://owned/photo").path("size").asText());
    }
    @Test void validatesInlineReferenceImage() throws Exception {
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20,20,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        String data="data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        assertEquals(data,ArkAiPhotoProvider.requestBody(recipe("x"),data).path("image").asText());
        assertThrows(RuntimeException.class,()->ArkAiPhotoProvider.requestBody(recipe("x"),data.replace("image/png","image/jpeg")));
        assertThrows(RuntimeException.class,()->ArkAiPhotoProvider.requestBody(recipe("x"),"data:image/png;base64,bm90LWltYWdl"));
        assertThrows(RuntimeException.class,()->ArkAiPhotoProvider.requestBody(recipe("x"),"data:image/png;base64,!!!"));
    }
}
