package com.example.cursorquitterweb.musicmv.aiphoto;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
class KieAiPhotoProviderTest {
    final ObjectMapper json=new ObjectMapper();
    JsonNode recipe() throws Exception {return json.readTree("{\"provider\":\"kie\",\"model\":\"bytedance/seedream-v4-edit\",\"prompt\":\"可爱人物\",\"parameters\":{\"image_resolution\":\"1K\"}}");}
    @Test void forcesOneOutputAndOriginalInput() throws Exception {JsonNode body=KieAiPhotoProvider.requestBody(recipe(),"https://owned/photo");assertEquals(1,body.path("input").path("max_images").asInt());assertEquals("https://owned/photo",body.path("input").path("image_urls").get(0).asText());assertEquals("可爱人物",body.path("input").path("prompt").asText());}
    @Test void neverGuessesAndroidModelOrStrength() throws Exception {assertThrows(RuntimeException.class,()->KieAiPhotoProvider.requestBody(json.readTree(recipe().toString().replace("bytedance/seedream-v4-edit","aip_high_aes_general_v43s_jm")),"https://owned/photo"));assertThrows(RuntimeException.class,()->KieAiPhotoProvider.requestBody(json.readTree(recipe().toString().replace("image_resolution","strength")),"https://owned/photo"));}
    @Test void defaultDisabledCannotSubmit() throws Exception {KieAiPhotoProvider provider=new KieAiPhotoProvider(false,"","",json);assertThrows(RuntimeException.class,()->provider.submit(recipe(),"https://owned/photo"));}
    @Test void outputMustBeExactlyOneRealUrl() throws Exception {assertThrows(com.example.cursorquitterweb.musicmv.support.ApiException.class,()->KieAiPhotoProvider.output(json.createObjectNode(),json));assertThrows(RuntimeException.class,()->KieAiPhotoProvider.output(json.readTree("{\"resultJson\":\"{\\\"resultUrls\\\":[]}\"}"),json));assertThrows(RuntimeException.class,()->KieAiPhotoProvider.output(json.readTree("{\"resultJson\":\"{\\\"resultUrls\\\":[1]}\"}"),json));}
    @Test void sniffsActualContentAndRejectsNonImage() throws Exception {ByteArrayOutputStream b=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB),"png",b);assertEquals("image/png",KieAiPhotoProvider.inspect(b.toByteArray()).contentType);assertThrows(RuntimeException.class,()->KieAiPhotoProvider.inspect("not an image".getBytes()));}
    @Test void downloadRejectsUnconfiguredHostsAndPrivateUrls(){KieAiPhotoProvider p=new KieAiPhotoProvider(false,"","cdn.example",json);assertThrows(RuntimeException.class,()->p.download("http://127.0.0.1/file"));assertThrows(RuntimeException.class,()->p.download("https://other.example/file"));}
}
